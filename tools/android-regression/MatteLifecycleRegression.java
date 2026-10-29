package com.opensource.svgaplayer.drawer;

import android.graphics.*;
import android.widget.ImageView;
import com.opensource.svgaplayer.*;
import org.json.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import com.svga.glide.*;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;

/** Device regression probe using real Android Canvas and Glide LruBitmapPool. */
public class MatteLifecycleRegression {
    static int failures;
    static void check(boolean condition, String label) {
        System.out.println((condition ? "PASS " : "FAIL ") + label);
        if (!condition) failures++;
    }
    static JSONObject frame(double alpha, int tx) throws Exception {
        return new JSONObject().put("alpha", alpha)
            .put("layout", new JSONObject().put("width", 64).put("height", 64))
            .put("transform", new JSONObject().put("a",1).put("d",1).put("tx",tx));
    }
    static JSONObject sprite(String image, String matte, double... alphas) throws Exception {
        JSONArray frames = new JSONArray();
        for (double alpha : alphas) frames.put(frame(alpha, 0));
        return new JSONObject().put("imageKey", image).put("matteKey", matte).put("frames", frames);
    }
    @SuppressWarnings("unchecked")
    static SVGACanvasDrawer scene(String mode) throws Exception {
        JSONArray sprites = new JSONArray();
        sprites.put(sprite("left.matte", "", 1,0,1));
        sprites.put(sprite("right.matte", "", 1,1,1));
        sprites.put(sprite("red", mode.equals("missing") ? "missing.matte" : "left.matte", 1,1,1));
        sprites.put(sprite("blue", mode.equals("missing") ? "" : "right.matte", 1,1,1));
        JSONObject json = new JSONObject().put("movie", new JSONObject()
            .put("viewBox", new JSONObject().put("width",64).put("height",64))
            .put("fps",30).put("frames",3)).put("sprites",sprites).put("images",new JSONObject());
        SVGAVideoEntity video = new SVGAVideoEntity(json, new File("/data/local/tmp"), 64,64,false);
        Field f = SVGAVideoEntity.class.getDeclaredField("imageMap"); f.setAccessible(true);
        Map<String,Bitmap> images = (Map<String,Bitmap>)f.get(video);
        for (String key : new String[]{"left","right","red","blue"}) {
            Bitmap b = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b); Paint p = new Paint();
            p.setColor(key.equals("red") ? Color.RED : key.equals("blue") ? Color.BLUE : Color.WHITE);
            c.drawRect(key.equals("right") ? 32 : 0,0,key.equals("left") ? 32 : 64,64,p);
            images.put(key,b);
        }
        return new SVGACanvasDrawer(video,new SVGADynamicEntity());
    }
    static int[] pixels(Bitmap b) {
        int[] p = new int[b.getWidth()*b.getHeight()]; b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight()); return p;
    }
    static Bitmap software(SVGACanvasDrawer drawer, int frame, int size) {
        Bitmap b = Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        drawer.drawFrame(new Canvas(b),frame,ImageView.ScaleType.FIT_CENTER); return b;
    }
    static void poolOwnership() throws Exception {
        SVGAVideoEntity video=scene("normal").getVideoItem();
        Map<String,Bitmap> images=video.getImageMap$lib_svga_release();
        Bitmap first=images.get("left");
        images.put("alias",first);
        final int[] puts={0};
        LruBitmapPool pool=new LruBitmapPool(1024*1024) {
            @Override public void put(Bitmap b) { puts[0]++; super.put(b); }
        };
        SVGAGlideEx.INSTANCE.setBitmapPool(pool);
        SVGAGlideResourceDelegate resource=new SVGAGlideResourceDelegate(new SVGAResource(video,"matte-audit",64,64));
        resource.recycle();
        check(!first.isRecycled(),"pool owns a live Bitmap after SVGA release");
        check(puts[0]==4,"same Bitmap under two keys returned once: puts="+puts[0]);
        resource.recycle();
        check(puts[0]==4,"repeated release does not put again");
        try {
            Bitmap reused=pool.getDirty(64,64,Bitmap.Config.ARGB_8888);
            check(!reused.isRecycled(),"Glide can retrieve a usable pooled Bitmap");
            new Canvas(reused).drawColor(Color.GREEN);
        } catch(Exception e) {check(false,"Glide reuse throws "+e);}
        SVGAVideoEntity owned=scene("normal").getVideoItem();
        Bitmap local=owned.getImageMap$lib_svga_release().get("left"); owned.clear();
        check(local.isRecycled(),"standalone video still releases owned images");
    }
    static void layerLifecycle() throws Exception {
        Bitmap output=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(output);
        int before=c.getSaveCount();
        SVGACanvasDrawer missing=scene("missing");
        missing.drawFrame(c,0,ImageView.ScaleType.FIT_CENTER);
        check(c.getSaveCount()==before,"missing matte closes layer before returning");
        check(output.getPixel(32,32)==Color.BLUE,"sprite following missing matte is composited");
        while(c.getSaveCount()>before)c.restore();
        SVGACanvasDrawer throwing=scene("normal");
        throwing.getDynamicItem().setDynamicDrawer((canvas,frame)-> {canvas.translate(12,9);throw new IllegalStateException("expected-test-failure");},"red");
        boolean propagated=false;
        try {throwing.drawFrame(c,0,ImageView.ScaleType.FIT_CENTER);}catch(IllegalStateException e){propagated=e.getMessage().equals("expected-test-failure");}
        check(propagated,"drawing exception remains visible to caller");
        check(c.getSaveCount()==before,"drawing exception restores Canvas layers");
        Field poolField=SGVADrawer.class.getDeclaredField("spritePool");poolField.setAccessible(true);
        Object spritePool=poolField.get(throwing);
        Field sizeField=spritePool.getClass().getDeclaredField("mPoolSize");sizeField.setAccessible(true);
        check(sizeField.getInt(spritePool)==4,"drawing exception returns frame sprites to pool");
        output.recycle();
    }
    static void pixelSignatures() throws Exception {
        SVGACanvasDrawer drawer=scene("normal");
        for(int size:new int[]{64,96,48})for(int frame=0;frame<3;frame++){
            Bitmap b=software(drawer,frame,size);
            System.out.println("PIXELS size="+size+" frame="+frame+" hash="+Arrays.hashCode(pixels(b)));
            b.recycle();
        }
    }
    public static void main(String[] args) throws Exception {
        layerLifecycle(); poolOwnership(); pixelSignatures();
        System.out.println("TOTAL_FAILURES="+failures);
        System.exit(failures==0 ? 0 : 1);
    }
}
