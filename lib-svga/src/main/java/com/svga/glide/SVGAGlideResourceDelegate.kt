package com.svga.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.util.Util
import com.svga.glide.SVGAGlideEx.bitmapPool
import com.svga.glide.SVGAGlideEx.log
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Time:2022/11/26 22:38
 * Author: zhouzechao
 * Description:
 */
class SVGAGlideResourceDelegate(private val resource: SVGAResource) : Resource<SVGAResource> {

    private val TAG = "SVGAGlideResourceDelegate"

    override fun getResourceClass(): Class<SVGAResource> {
        return SVGAResource::class.java
    }

    override fun get(): SVGAResource {
        return resource
    }

    private fun mapSize(): Int {
        var cnt = 0
        try {
            val map = resource.imageMap
            map?.forEach { cnt += Util.getBitmapByteSize(it.value) }
        } catch (_: Throwable) {
        }
        return cnt
    }

    private fun recycleImage() {
        try {
            val map = resource.imageMap
            log.d(TAG, "recycle ${resource.model} size:${map?.size}")
            if (map.isNullOrEmpty()) return
            val bitmaps = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())
            bitmaps.addAll(map.values)
            // Ownership passes to Glide. videoItem.clear() must not recycle pooled images.
            map.clear()
            bitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmapPool.put(bitmap)
            }
        } catch (_: Throwable) {
        }
    }

    override fun getSize(): Int {
        return mapSize()
    }

    override fun recycle() {
        recycleImage()
        resource.videoItem?.movieItem = null
        resource.videoItem?.clear()
    }
}