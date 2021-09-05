package com.github.skgmn.webpdecoder

import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.GuardedBy
import coil.bitmap.BitmapPool
import com.github.skgmn.webpdecoder.libwebp.LibWebPAnimatedDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException

@OptIn(
    DelicateCoroutinesApi::class,
    ExperimentalCoroutinesApi::class
)
internal class AnimatedWebPDrawable(
    private val decoder: LibWebPAnimatedDecoder,
    @GuardedBy("bitmapPool")
    private val bitmapPool: BitmapPool,
    firstFrame: LibWebPAnimatedDecoder.DecodeFrameResult? = null
) : Drawable(), Animatable {
    private val paint by lazy(LazyThreadSafetyMode.NONE) { Paint(Paint.FILTER_BITMAP_FLAG) }
    private var decodeChannel: Channel<LibWebPAnimatedDecoder.DecodeFrameResult>? = null
    private var decodeJob: Job? = null
    private var frameWaitingJob: Job? = null
    private var pendingDecodeResult: LibWebPAnimatedDecoder.DecodeFrameResult? = null
    private var nextFrame = false
    private var isRunning = false

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
                    scheduleSelf({
                        synchronized(bitmapPool) {
                            bitmapPool.put(it)
                        }
                    }, 0)
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
            if (decodeJob?.isActive != true && channel.isEmpty) {
                stop()
            } else if (frameWaitingJob?.isActive != true) {
                frameWaitingJob = GlobalScope.launch(Dispatchers.Main.immediate) {
                    try {
                        pendingDecodeResult = channel.receive()
                        nextFrame = true
                        queueTime = SystemClock.uptimeMillis()
                        invalidateSelf()
                    } catch (e: ClosedReceiveChannelException) {
                        // failed to receive next frame
                    } finally {
                        frameWaitingJob = null
                    }
                }
            }
        } else {
            canvas.drawBitmap(decodeFrameResult.bitmap, null, bounds, paint)
            currentDecodingResult = decodeFrameResult
            if (decodeJob?.isActive != true && channel.isEmpty) {
                stop()
            } else {
                scheduleSelf(
                    nextFrameScheduler,
                    time + (decodeFrameResult.frameLengthMs - queueDelay).coerceAtLeast(0)
                )
            }
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
        if (isRunning) return
        isRunning = true

        val channel = Channel<LibWebPAnimatedDecoder.DecodeFrameResult>(
            capacity = 1,
            onUndeliveredElement = {
                synchronized(bitmapPool) { bitmapPool.put(it.bitmap) }
            }
        ).also {
            decodeChannel = it
        }
        nextFrame = true
        invalidateSelf()
        decodeJob = GlobalScope.launch(Dispatchers.Default) {
            val loopCount = decoder.loopCount
            var i = 0
            while (isActive && (loopCount == 0 || i < loopCount)) {
                decoder.reset()
                while (isActive && decoder.hasNextFrame()) {
                    val reuseBitmap = synchronized(bitmapPool) {
                        bitmapPool.getDirtyOrNull(
                            decoder.width,
                            decoder.height,
                            Bitmap.Config.ARGB_8888
                        )
                    }
                    val result = decoder.decodeNextFrame(reuseBitmap)
                    if (result == null || result.bitmap !== reuseBitmap) {
                        reuseBitmap?.let {
                            synchronized(bitmapPool) { bitmapPool.put(it) }
                        }
                    }
                    if (!isActive) {
                        break
                    }
                    if (result == null) {
                        continue
                    }
                    try {
                        channel.send(result)
                    } catch (e: ClosedSendChannelException) {
                        synchronized(bitmapPool) {
                            bitmapPool.put(result.bitmap)
                        }
                        break
                    }
                }
                ++i
            }
        }
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false

        decodeJob?.cancel()
        decodeJob = null

        decodeChannel?.close()
        decodeChannel = null

        frameWaitingJob?.cancel()
        frameWaitingJob = null

        nextFrame = false
        unscheduleSelf(nextFrameScheduler)
    }

    override fun isRunning(): Boolean {
        return isRunning
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