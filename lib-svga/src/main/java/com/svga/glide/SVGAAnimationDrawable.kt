package com.svga.glide

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.opensource.svgaplayer.SVGADrawable
import com.opensource.svgaplayer.SVGADynamicEntity
import com.opensource.svgaplayer.SVGAVideoEntity
import com.svga.glide.SVGAGlideEx.log

/**
 * 当同一个SVGA图片被加载的时候 如果此时svga动画在运行中他们会共享同样的动画效果
 *
 * ***/
class SVGAAnimationDrawable(
    val videoItem: SVGAVideoEntity,
    var repeatCount: Int,
    val repeatMode: Int,
    val dynamicItem: SVGADynamicEntity,
    var showLastFrame: Boolean = false
) : Animatable, Drawable(), ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {

    private val TAG = "SVGAAnimationDrawable"

    // 上层传下来的识别码
    var tag = ""

    var svgaCallback: SVGACallback2? = null

    private var mAnimator: ValueAnimator? = null
    private var currentFrame = -1
    private var animatorFrame = -1
    private val frameRateLimiter = SVGAFrameRateLimiter()

    /**
     * Maximum redraw rate for this drawable. Zero keeps the SVGA asset's original frame rate.
     * The animation duration is unchanged; intermediate frames are skipped when limited.
     */
    var frameRate: Int
        get() = frameRateLimiter.frameRate
        set(value) {
            val normalized = SVGAFrameRateLimiter.normalize(value)
            if (frameRateLimiter.frameRate == normalized) return
            frameRateLimiter.frameRate = normalized
            renderFrame(animatorFrame)
        }

    //一共有多少帧
    private var totalFrame = 0

    private var isInitiativePause = false

    private var drawer = SVGADrawable(videoItem, dynamicItem)

    var scaleType = ImageView.ScaleType.MATRIX
        set(value) {
            field = value
            drawer.scaleType = value
        }

    /** True if the drawable should animate while visible. */
    private var isStarted = false
    private var isVisible = false

    fun start(now: Boolean) {
        isStarted = true
        if (now) {
            start()
        } else {
            isInitiativePause = true
        }
    }

    fun setToFrame(frame: Int, andPlay: Boolean) {
        val realFrame = frame.coerceIn(0, totalFrame - 1)
        if (!andPlay) {
            stop()
            if (currentFrame != realFrame) {
                animatorFrame = realFrame
                currentFrame = realFrame
                updateDrawableFrame()
                invalidateSelf()
                val percentage = (currentFrame + 1).toDouble() / videoItem.frames.toDouble()
                svgaCallback?.onStep(currentFrame, percentage)
            }
            return
        }
        mAnimator?.let {
            it.currentPlayTime = (0.0f.coerceAtLeast(
                1.0f.coerceAtMost((realFrame.toFloat() / videoItem.frames.toFloat()))
            ) * it.duration).toLong()
        }
    }

    override fun start() {
        isStarted = true
        if (!isVisible) {
            log.d(TAG, "try to start anim, but isVisible=false")
            return
        }
        if (mAnimator == null || mAnimator?.isRunning == false) {
            val startFrame = 0
            val endFrame = videoItem.frames - 1
            totalFrame = (endFrame - startFrame + 1)
            setUpDrawableClear()
            mAnimator?.cancel()
            mAnimator = null
            mAnimator = ValueAnimator.ofInt(startFrame, endFrame)
            mAnimator?.interpolator = LinearInterpolator()
            mAnimator?.duration = (totalFrame * (1000 / videoItem.FPS) / generateScale()).toLong()
            mAnimator?.repeatCount = repeatCount
            mAnimator?.repeatMode = repeatMode
            mAnimator?.addUpdateListener(this)
            mAnimator?.addListener(this)
            mAnimator?.start()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mAnimator?.resume()
            } else {
                mAnimator?.start()
            }
        }
    }

    private fun setUpDrawableClear() {
        drawer.cleared = false
    }

    private fun generateScale(): Double {
        var scale = 1.0
        try {
            val animatorClass = Class.forName("android.animation.ValueAnimator") ?: return scale
            val getMethod = animatorClass.getDeclaredMethod("getDurationScale") ?: return scale
            scale = (getMethod.invoke(animatorClass) as Float).toDouble()
            if (scale == 0.0) {
                val setMethod =
                    animatorClass.getDeclaredMethod("setDurationScale", Float::class.java)
                        ?: return scale
                setMethod.isAccessible = true
                setMethod.invoke(animatorClass, 1.0f)
                scale = 1.0
            }
        } catch (ignore: Exception) {
            ignore.printStackTrace()
        }
        return scale
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        isVisible = visible
        if (isStarted) {
            if (!visible) {
                pause()
            } else {
                resume()
            }
        }
        return super.setVisible(visible, restart)
    }

    override fun stop() {
        log.d(TAG, "stop")
        isStarted = false
        if (mAnimator != null) {
            mAnimator?.cancel()
            mAnimator?.removeAllListeners()
            mAnimator?.removeAllUpdateListeners()
            mAnimator = null
            drawer.stop()
        }
        animatorFrame = -1
        frameRateLimiter.reset()
    }

    override fun isRunning(): Boolean {
        return mAnimator?.isRunning == true
    }

    override fun draw(canvas: Canvas) {
        if (currentFrame > -1) {
            drawer.draw(canvas)
        }
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        val frame = animation.animatedValue as Int
        if (animatorFrame == frame) return
        animatorFrame = frame
        val nowNanos = System.nanoTime()
        if (shouldRenderFrame(nowNanos)) {
            renderFrame(frame, nowNanos)
        }
        val percentage = (frame + 1).toDouble() / videoItem.frames.toDouble()
        svgaCallback?.onStep(frame, percentage)
    }

    private fun shouldRenderFrame(nowNanos: Long): Boolean {
        return frameRateLimiter.shouldRender(nowNanos)
    }

    private fun renderFrame(frame: Int, nowNanos: Long = System.nanoTime()) {
        if (frame < 0 || currentFrame == frame) return
        currentFrame = frame
        frameRateLimiter.markRendered(nowNanos)
        updateDrawableFrame()
        invalidateSelf()
    }

    private fun updateDrawableFrame() {
        drawer.currentFrame = currentFrame
    }

    fun pause(isInitiative: Boolean = false) {
        log.d(TAG, "pause")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (isInitiative) {
                isInitiativePause = true
            }
            if (mAnimator?.isStarted == true && mAnimator?.isPaused == false) {
                mAnimator?.pause()
                svgaCallback?.onSvgaPause()
                drawer.pause()
            }
        } else {
            if (isInitiative) {
                isInitiativePause = true
            }
            stop()
        }
    }

    fun resume(isInitiative: Boolean = false) {
        log.d(TAG, "resume")
        if (this.isInitiativePause && !isInitiative) {
            return
        }
        isInitiativePause = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!isStarted) {
                return
            }
            if (mAnimator?.isStarted == true) {
                if (mAnimator?.isPaused == true) {
                    mAnimator?.resume()
                    svgaCallback?.onSvgaResume()
                    drawer.resume()
                }
            } else {
                if (repeatCount == -1) {
                    start()
                }
            }
        } else {
            if (repeatCount == -1) {
                start()
            }
        }
    }

    override fun onAnimationStart(animation: Animator) {
        svgaCallback?.onSvgaStart()
    }

    override fun onAnimationEnd(animation: Animator) {
        svgaCallback?.onFinished()
        if (showLastFrame && isStarted) {
            renderFrame(videoItem.frames - 1)
        } else {
            currentFrame = -1
            invalidateSelf()
        }
    }

    override fun onAnimationCancel(animation: Animator) {
        currentFrame = -1
        animatorFrame = -1
        frameRateLimiter.reset()
        invalidateSelf()
    }

    override fun onAnimationRepeat(animation: Animator) {
        svgaCallback?.onRepeat()
    }

}
