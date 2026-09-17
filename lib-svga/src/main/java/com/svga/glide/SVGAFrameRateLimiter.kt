package com.svga.glide

internal class SVGAFrameRateLimiter(frameRate: Int = 0) {

    var frameRate: Int = normalize(frameRate)
        set(value) {
            val normalized = normalize(value)
            if (field == normalized) return
            field = normalized
            reset()
        }

    private var lastRenderTimeNanos = NO_RENDER_TIME

    fun shouldRender(nowNanos: Long): Boolean {
        if (frameRate == 0 || lastRenderTimeNanos == NO_RENDER_TIME) return true
        return nowNanos - lastRenderTimeNanos >= NANOS_PER_SECOND / frameRate
    }

    fun markRendered(nowNanos: Long) {
        lastRenderTimeNanos = nowNanos
    }

    fun reset() {
        lastRenderTimeNanos = NO_RENDER_TIME
    }

    companion object {
        const val MAX_FRAME_RATE = 240
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val NO_RENDER_TIME = Long.MIN_VALUE

        fun normalize(value: Int): Int = value.coerceIn(0, MAX_FRAME_RATE)
    }
}
