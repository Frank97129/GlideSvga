package com.svga.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SVGAFrameRateLimiterTest {

    @Test
    fun zeroKeepsEveryFrame() {
        val limiter = SVGAFrameRateLimiter(0)

        assertTrue(limiter.shouldRender(0L))
        limiter.markRendered(0L)
        assertTrue(limiter.shouldRender(1L))
    }

    @Test
    fun fifteenFpsSkipsIntermediateThirtyFpsFrames() {
        val limiter = SVGAFrameRateLimiter(15)

        assertTrue(limiter.shouldRender(0L))
        limiter.markRendered(0L)
        assertFalse(limiter.shouldRender(33_333_333L))
        assertTrue(limiter.shouldRender(66_666_666L))
    }

    @Test
    fun changingRateAllowsTheNextFrameImmediately() {
        val limiter = SVGAFrameRateLimiter(15)
        limiter.markRendered(1_000_000_000L)
        assertFalse(limiter.shouldRender(1_020_000_000L))

        limiter.frameRate = 30

        assertTrue(limiter.shouldRender(1_020_000_000L))
    }

    @Test
    fun invalidRateIsClamped() {
        assertEquals(0, SVGAFrameRateLimiter(-1).frameRate)
        assertEquals(240, SVGAFrameRateLimiter(1_000).frameRate)
    }
}
