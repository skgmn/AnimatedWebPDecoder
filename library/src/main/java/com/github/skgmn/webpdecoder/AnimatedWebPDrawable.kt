package com.github.skgmn.webpdecoder

import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import coil.bitmap.BitmapPool
import com.github.skgmn.webpdecoder.libwebp.LibWebPAnimatedDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

internal class AnimatedWebPDrawable(
    private val decoder: LibWebPAnimatedDecoder,
    private val bitmapPool: BitmapPool,
    firstFrame: LibWebPAnimatedDecoder.DecodeFrameResult? = null
) : Drawable(), Animatable {
    private val paint by lazy(LazyThreadSafetyMode.NONE) { Paint(Paint.FILTER_BITMAP_FLAG) }
    private val decodeChannel by lazy {
        Channel<LibWebPAnimatedDecoder.DecodeFrameResult>(1)
    }
    private var decodeJob: Job? = null
    private var frameWaitingJob: Job? = null
    private var pendingDecodeResult = firstFrame

    // currentBitmap should be set right after Canvas.drawBitmap() is called
    // since it returns existing value to BitmapPool.
    private var currentBitmap: Bitmap? = firstFrame?.bitmap
        set(value) {
            if (field !== value) {
                field?.let {
                    // put the bitmap to the pool after it is detached from RenderNode
                    // simply by using handler
                    // unless this spam log may be appeared:
                    //   Called reconfigure on a bitmap that is in use! This may cause graphical corruption!
                    scheduleSelf({ bitmapPool.put(it) }, 0)
                }
                field = value
            }
        }

    @OptIn(DelicateCoroutinesApi::class)
    override fun draw(canvas: Canvas) {
        val time = SystemClock.uptimeMillis()
        val decodeFrameResult = pendingDecodeResult?.also {
            pendingDecodeResult = null
        } ?: decodeChannel.tryReceive().getOrNull()
        if (decodeFrameResult == null) {
            currentBitmap?.let {
                canvas.drawBitmap(it, null, bounds, paint)
            }
            if (isRunning && frameWaitingJob == null) {
                frameWaitingJob = GlobalScope.launch(Dispatchers.Main.immediate) {
                    pendingDecodeResult = decodeChannel.receive()
                    invalidateSelf()
                    frameWaitingJob = null
                }
            }
        } else {
            canvas.drawBitmap(decodeFrameResult.bitmap, null, bounds, paint)
            currentBitmap = decodeFrameResult.bitmap
            scheduleSelf({
                invalidateSelf()
            }, time + decodeFrameResult.frameLengthMs)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return decoder.width
    }

    override fun getIntrinsicHeight(): Int {
        return decoder.height
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun start() {
        if (decodeJob != null) return
        decodeJob = GlobalScope.launch(Dispatchers.Default) {
            val loopCount = decoder.loopCount
            var i = 0
            while (loopCount == 0 || i < loopCount) {
                if (!isActive) {
                    return@launch
                }
                decoder.reset()
                while (isActive && decoder.hasNextFrame()) {
                    val reuseBitmap = bitmapPool.getDirtyOrNull(
                        decoder.width,
                        decoder.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val result = decoder.decodeNextFrame(reuseBitmap)
                    if (result == null || result.bitmap !== reuseBitmap) {
                        reuseBitmap?.let { bitmapPool.put(it) }
                    }
                    decodeChannel.send(result ?: break)
                }
                ++i
            }
        }
    }

    override fun stop() {
        frameWaitingJob?.cancel()
        frameWaitingJob = null

        decodeJob?.cancel()
        decodeJob = null
    }

    override fun isRunning(): Boolean {
        return decodeJob?.isActive == true
    }
}