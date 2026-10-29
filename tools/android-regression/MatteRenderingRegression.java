package com.opensource.svgaplayer.drawer;

import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Looper;
import android.widget.ImageView;
import com.opensource.svgaplayer.*;
import org.json.*;
import java.io.File;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

/** Actual software and HWUI render tests, run in a dedicated app_process. */
public final class MatteRenderingRegression {
    static int failures,checks;
    static int gpuHalfAlpha;
    static void check(boolean ok,String label){
        checks++;if(!ok)failures++;
        System.out.println((ok?"PASS ":"FAIL ")+label);
    }
    static JSONObject frame(double alpha,int tx)throws Exception{
        return new JSONObject().put("alpha",alpha)
            .put("layout",new JSONObject().put("width",64).put("height",64))
            .put("transform",new JSONObject().put("a",1).put("d",1).put("tx",tx));
    }
    static JSONObject sprite(String image,String matte,boolean shifted,double...alphas)throws Exception{
        JSONArray frames=new JSONArray();
        for(double a:alphas)frames.put(frame(a,shifted?16:0));
        return new JSONObject().put("imageKey",image).put("matteKey",matte).put("frames",frames);
    }
    static SVGACanvasDrawer scene(String mode)throws Exception{
        JSONArray sprites=new JSONArray();
        boolean shift=mode.equals("shift");
        sprites.put(sprite("left.matte","",shift,1,0,1));
        sprites.put(sprite("right.matte","",false,1,1,1));
        if(mode.equals("ordinary"))sprites=new JSONArray();
        sprites.put(sprite("red",mode.equals("ordinary")?"":"left.matte",false,1,1,1));
        if(!mode.equals("single"))sprites.put(sprite("blue",mode.equals("ordinary")?"":"right.matte",false,1,1,1));
        if(mode.equals("repeated"))sprites.put(sprite("green","left.matte",false,1,1,1));
        if(mode.equals("alpha")){
            sprites.getJSONObject(0).put("frames",new JSONArray().put(frame(.5,0)).put(frame(0,0)).put(frame(.5,0)));
            sprites.getJSONObject(2).put("frames",new JSONArray().put(frame(.5,0)).put(frame(.5,0)).put(frame(.5,0)));
        }
        JSONObject json=new JSONObject().put("movie",new JSONObject().put("viewBox",new JSONObject().put("width",64).put("height",64)).put("fps",30).put("frames",3)).put("sprites",sprites).put("images",new JSONObject());
        SVGAVideoEntity video=new SVGAVideoEntity(json,new File("/data/local/tmp"),64,64,false);
        Map<String,Bitmap> images=video.getImageMap$lib_svga_release();
        for(String name:new String[]{"left","right","red","blue","green"}){
            Bitmap image=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
            Paint p=new Paint();p.setColor(name.equals("red")?Color.RED:name.equals("blue")?Color.BLUE:name.equals("green")?Color.GREEN:Color.WHITE);
            new Canvas(image).drawRect(name.equals("right")?32:0,0,name.equals("left")?32:64,64,p);
            images.put(name,image);
        }
        return new SVGACanvasDrawer(video,new SVGADynamicEntity());
    }
    static void draw(SVGACanvasDrawer drawer,Canvas canvas,int frame,boolean transformed){
        int save=canvas.save();
        try{
            canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            if(transformed){canvas.translate(4,6);canvas.scale(.75f,.75f);canvas.clipRect(0,0,64,64);}
            drawer.drawFrame(canvas,frame,ImageView.ScaleType.FIT_CENTER);
        }finally{canvas.restoreToCount(save);}
    }
    static int[] software(SVGACanvasDrawer drawer,int frame,int size,boolean transform){
        Bitmap out=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        draw(drawer,new Canvas(out),frame,transform);
        int[] pixels=new int[size*size];out.getPixels(pixels,0,size,0,0,size,size);out.recycle();return pixels;
    }
    static final class Gpu implements AutoCloseable{
        final int size;final ImageReader reader;final HardwareRenderer renderer;
        Gpu(int size){this.size=size;reader=ImageReader.newInstance(size,size,PixelFormat.RGBA_8888,2,HardwareBuffer.USAGE_CPU_READ_OFTEN|HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);renderer=new HardwareRenderer();renderer.setOpaque(false);renderer.setSurface(reader.getSurface());}
        RenderNode record(SVGACanvasDrawer drawer,int frame,boolean transform){
            RenderNode node=new RenderNode("svga-matte-regression");node.setPosition(0,0,size,size);
            RecordingCanvas canvas=node.beginRecording(size,size);
            try{draw(drawer,canvas,frame,transform);}finally{node.endRecording();}
            return node;
        }
        int[] render(RenderNode node)throws Exception{return render(node,true);}
        int[] render(RenderNode node,boolean readPixels)throws Exception{
            renderer.setContentRoot(node);
            int result=renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            if(result!=HardwareRenderer.SYNC_OK)throw new IllegalStateException("HWUI result="+result);
            Image image=null;
            for(int i=0;i<50&&image==null;i++){image=reader.acquireNextImage();if(image==null)Thread.sleep(2);}
            if(image==null)throw new IllegalStateException("No HWUI output");
            try{
                if(!readPixels)return null;
                Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();
                int[] pixels=new int[size*size];
                for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                    int p=y*plane.getRowStride()+x*plane.getPixelStride();
                    int r=buffer.get(p)&255,g=buffer.get(p+1)&255,b=buffer.get(p+2)&255,a=buffer.get(p+3)&255;
                    if(a>0&&a<255){r=Math.min(255,(r*255+a/2)/a);g=Math.min(255,(g*255+a/2)/a);b=Math.min(255,(b*255+a/2)/a);}
                    pixels[y*size+x]=(a<<24)|(r<<16)|(g<<8)|b;
                }
                return pixels;
            }finally{image.close();}
        }
        public void close(){renderer.destroy();reader.close();}
    }
    static String hash(int[] pixels)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(int p:pixels){digest.update((byte)(p>>>24));digest.update((byte)(p>>>16));digest.update((byte)(p>>>8));digest.update((byte)p);}
        StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format("%02x",b&255));return s.toString();
    }
    static void validate(String label,String mode,int frame,int size,int[] pixels){
        int left=pixels[(size/2)*size+size/4],right=pixels[(size/2)*size+size*3/4];
        if(mode.equals("ordinary")){check(left==Color.BLUE&&right==Color.BLUE,label+" ordinary rendering");return;}
        int expectedLeft=frame==1?Color.TRANSPARENT:mode.equals("repeated")?Color.GREEN:Color.RED;
        if(mode.equals("alpha")&&frame!=1){
            int expectedAlpha=label.startsWith("GPU")?Math.round(gpuHalfAlpha*127f/255f):63;
            check(Math.abs((left>>>24)-expectedAlpha)<=1&&(left&0xffffff)==0xff0000,label+" alpha multiplication actual="+Integer.toHexString(left));
        }else check(left==expectedLeft,label+" left actual="+Integer.toHexString(left));
        check(right==(mode.equals("single")?Color.TRANSPARENT:Color.BLUE),label+" right actual="+Integer.toHexString(right));
    }
    static void benchmark()throws Exception{
        SVGACanvasDrawer drawer=scene("pair");long[] recordTimes=new long[60],frameTimes=new long[60];
        try(Gpu gpu=new Gpu(720)){
            for(int i=-15;i<60;i++){
                long start=System.nanoTime();RenderNode node=gpu.record(drawer,Math.floorMod(i,3),false);long recorded=System.nanoTime();
                gpu.render(node,false);long finished=System.nanoTime();node.discardDisplayList();
                if(i>=0){recordTimes[i]=recorded-start;frameTimes[i]=finished-start;}
            }
        }
        Arrays.sort(recordTimes);Arrays.sort(frameTimes);
        System.out.println("BENCHMARK frames=60 size=720x720 masks=2 record_p50_ms="+recordTimes[30]/1e6+" record_p95_ms="+recordTimes[57]/1e6+" sync_p50_ms="+frameTimes[30]/1e6+" sync_p95_ms="+frameTimes[57]/1e6);
    }
    public static void main(String[] args)throws Exception{
        if(Looper.myLooper()==null)Looper.prepare();
        System.out.println("RENDER_PROBE pid="+android.os.Process.myPid());
        if(Arrays.asList(args).contains("--benchmark")){benchmark();System.exit(0);}
        // Calibrate the platform's bitmap-alpha path separately from mask composition.
        // Raw ImageReader pixels are premultiplied; Gpu.render converts them for comparisons.
        try(Gpu gpu=new Gpu(64)) {
            RenderNode node=new RenderNode("bitmap-alpha-reference");node.setPosition(0,0,64,64);
            RecordingCanvas canvas=node.beginRecording(64,64);
            canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            int save=canvas.saveLayer(0,0,64,64,null);
            Bitmap source=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);
            Paint paint=new Paint();paint.setAlpha(127);paint.setAntiAlias(true);paint.setFilterBitmap(true);
            canvas.drawBitmap(source,new Matrix(),paint);canvas.restoreToCount(save);node.endRecording();
            gpuHalfAlpha=gpu.render(node)[32*64+16]>>>24;
            node.discardDisplayList();
        }
        DataOutputStream capture=args.length>0?new DataOutputStream(new FileOutputStream(args[0])):null;
        for(String mode:new String[]{"single","pair","repeated","alpha","ordinary"}){
            SVGACanvasDrawer drawer=scene(mode);
            for(int size:new int[]{64,96,48})try(Gpu gpu=new Gpu(size)){
                for(int frame=0;frame<3;frame++){
                    String label=mode+"/"+size+"/"+frame;
                    int[] sw=software(drawer,frame,size,false);validate("SW/"+label,mode,frame,size,sw);
                    RenderNode node=gpu.record(drawer,frame,false);
                    int[] hw=gpu.render(node);validate("GPU/"+label,mode,frame,size,hw);
                    if(capture!=null){capture.writeUTF(label);capture.writeInt(size);for(int p:hw)capture.writeInt(p);}
                    System.out.println("SIGNATURE "+label+" SW="+hash(sw)+" GPU="+hash(hw));node.discardDisplayList();
                }
            }
        }
        if(capture!=null)capture.close();
        // Record later frames before submitting earlier ones to detect mutable-buffer aliasing.
        SVGACanvasDrawer delayed=scene("pair");
        try(Gpu gpu=new Gpu(64)){
            RenderNode first=gpu.record(delayed,0,false),transparent=gpu.record(delayed,1,false),last=gpu.record(delayed,2,false);
            validate("GPU/deferred-first","pair",0,64,gpu.render(first));
            validate("GPU/deferred-transparent","pair",1,64,gpu.render(transparent));
            validate("GPU/deferred-last","pair",2,64,gpu.render(last));
            first.discardDisplayList();transparent.discardDisplayList();last.discardDisplayList();
        }
        SVGACanvasDrawer transformed=scene("pair");
        try(Gpu gpu=new Gpu(64)){
            int[] sw=software(transformed,0,64,true);RenderNode node=gpu.record(transformed,0,true);int[] hw=gpu.render(node);
            check(sw[30*64+16]==Color.RED&&sw[30*64+40]==Color.BLUE,"SW parent matrix and clipping");
            check(hw[30*64+16]==Color.RED&&hw[30*64+40]==Color.BLUE,"GPU parent matrix and clipping");node.discardDisplayList();
        }
        SVGACanvasDrawer custom=scene("pair");final int[] calls={0};
        custom.getDynamicItem().setDynamicDrawer((canvas,frame)->{
            check(!canvas.isHardwareAccelerated(),"custom matte callback preserves software Canvas");calls[0]++;
            canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);Paint p=new Paint();p.setColor(Color.WHITE);canvas.drawRect(0,0,32,64,p);return true;
        },"left.matte");
        try(Gpu gpu=new Gpu(64)){RenderNode node=gpu.record(custom,0,false);validate("GPU/custom","pair",0,64,gpu.render(node));node.discardDisplayList();}
        check(calls[0]==1,"custom matte callback invoked once per group");
        SVGACanvasDrawer clipping=scene("pair");
        clipping.getVideoItem().getSpriteList$lib_svga_release().get(0).getFrames().get(0).setMaskPath(new com.opensource.svgaplayer.entities.SVGAPathEntity("M0 0 L16 0 L16 64 L0 64 Z"));
        int[] clipped=software(clipping,0,64,false);check(clipped[32*64+8]==Color.RED&&clipped[32*64+24]==Color.TRANSPARENT,"SW matte clip path");
        try(Gpu gpu=new Gpu(64)){RenderNode node=gpu.record(clipping,0,false);int[] hw=gpu.render(node);check(hw[32*64+8]==Color.RED&&hw[32*64+24]==Color.TRANSPARENT,"GPU matte clip path");node.discardDisplayList();}
        MatteLifecycleRegression.layerLifecycle();MatteLifecycleRegression.poolOwnership();
        failures+=MatteLifecycleRegression.failures;
        System.out.println("TOTAL_CHECKS="+checks+" PLUS_LIFECYCLE=10 TOTAL_FAILURES="+failures);
        System.exit(failures==0?0:1);
    }
}
