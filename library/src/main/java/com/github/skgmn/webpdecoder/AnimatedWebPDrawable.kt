package com.github.skgmn.webpdecoder

import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import coil.bitmap.BitmapPool
import com.github.skgmn.webpdecoder.libwebp.LibWebPAnimatedDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException

internal class AnimatedWebPDrawable(
    private val decoder: LibWebPAnimatedDecoder,
    private val bitmapPool: BitmapPool,
    firstFrame: LibWebPAnimatedDecoder.DecodeFrameResult? = null
) : Drawable(), Animatable {
    private val paint by lazy(LazyThreadSafetyMode.NONE) { Paint(Paint.FILTER_BITMAP_FLAG) }
    private var decodeChannel: Channel<LibWebPAnimatedDecoder.DecodeFrameResult>? = null
    private var decodeJob: Job? = null
    private var frameWaitingJob: Job? = null
    private var pendingDecodeResult: LibWebPAnimatedDecoder.DecodeFrameResult? = null
    private var nextFrame = false

    // currentBitmap should be set right after Canvas.drawBitmap() is called
    // since it returns existing value to BitmapPool.
    private var currentDecodingResult = firstFrame
        set(value) {
            if (field !== value) {
                field?.bitmap?.let {
                    // put the bitmap to the pool after it is detached from RenderNode
                    // simply by using handler
                    // unless this spam log may be appeared:
                    //   Called reconfigure on a bitmap that is in use! This may cause graphical corruption!
                    scheduleSelf({ bitmapPool.put(it) }, 0)
                }
                field = value
            }
        }

    private var queueTime = -1L
    private var queueDelay = INITIAL_QUEUE_DELAY_HEURISTIC
    private var queueDelayWindow = ArrayDeque(listOf(INITIAL_QUEUE_DELAY_HEURISTIC))
    private var queueDelaySum = INITIAL_QUEUE_DELAY_HEURISTIC

    private val nextFrameScheduler = {
        nextFrame = true
        queueTime = SystemClock.uptimeMillis()
        invalidateSelf()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun draw(canvas: Canvas) {
        val time = SystemClock.uptimeMillis()
        if (queueTime >= 0) {
            val currentDelay = time - queueTime
            addQueueDelay(currentDelay)
            queueTime = -1
        }

        val channel = decodeChannel
        if (!isRunning || !nextFrame || channel == null) {
            currentDecodingResult?.bitmap?.let {
                canvas.drawBitmap(it, null, bounds, paint)
            }
            return
        }

        nextFrame = false
        val decodeFrameResult = pendingDecodeResult?.also {
            pendingDecodeResult = null
        } ?: channel.tryReceive().getOrNull()
        if (decodeFrameResult == null) {
            currentDecodingResult?.bitmap?.let {
                canvas.drawBitmap(it, null, bounds, paint)
            }
            if (isRunning && frameWaitingJob?.isActive != true) {
                frameWaitingJob = GlobalScope.launch(Dispatchers.Main.immediate) {
                    pendingDecodeResult = channel.receive()
                    frameWaitingJob = null
                    nextFrame = true
                    queueTime = SystemClock.uptimeMillis()
                    invalidateSelf()
                }
            }
        } else {
            canvas.drawBitmap(decodeFrameResult.bitmap, null, bounds, paint)
            currentDecodingResult = decodeFrameResult
            scheduleSelf(
                nextFrameScheduler,
                time + (decodeFrameResult.frameLengthMs - queueDelay).coerceAtLeast(0)
            )
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
        if (decodeJob?.isActive == true) return
        val channel = Channel<LibWebPAnimatedDecoder.DecodeFrameResult>(
            capacity = 1,
            onUndeliveredElement = { bitmapPool.put(it.bitmap) }
        ).also {
            decodeChannel = it
        }
        nextFrame = true
        invalidateSelf()
        decodeJob = GlobalScope.launch(Dispatchers.Default) {
            val loopCount = decoder.loopCount
            var i = 0
            while (loopCount == 0 || i < loopCount) {
                if (!isActive) {
                    return@launch
                }
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
                    try {
                        channel.send(result ?: continue)
                    } catch (e: ClosedSendChannelException) {
                        break
                    }
                }
                decoder.reset()
                ++i
            }
        }
    }

    override fun stop() {
        frameWaitingJob?.cancel()
        frameWaitingJob = null

        decodeJob?.cancel()
        decodeJob = null

        nextFrame = false
        unscheduleSelf(nextFrameScheduler)

        decodeChannel?.close()
        decodeChannel = null

        decoder.reset()
    }

    override fun isRunning(): Boolean {
        return decodeJob?.isActive == true
    }

    private fun addQueueDelay(delay: Long) {
        val coercedDelay = delay.coerceAtMost(MAX_QUEUE_DELAY_HEURISTIC)
        queueDelayWindow.addLast(coercedDelay)
        queueDelaySum += coercedDelay
        while (queueDelayWindow.size > QUEUE_DELAY_WINDOW_COUNT) {
            queueDelaySum -= queueDelayWindow.removeFirst()
        }
        queueDelay = (queueDelaySum / queueDelayWindow.size).coerceAtMost(MAX_QUEUE_DELAY_HEURISTIC)
    }

    companion object {
        private const val INITIAL_QUEUE_DELAY_HEURISTIC = 11L
        private const val MAX_QUEUE_DELAY_HEURISTIC = 21L
        private const val QUEUE_DELAY_WINDOW_COUNT = 20
    }
}