package com.github.skgmn.webpdecoder.libwebp

import android.graphics.Bitmap
import android.os.Build
import java.nio.ByteBuffer

class LibWebPAnimatedDecoder private constructor(
    @Suppress("unused") private val byteBuffer: ByteBuffer, // to keep in memory
    private val decoder: Long,
    private val premultipliedAlpha: Boolean
) {
    val width get() = metadata.width
    val height get() = metadata.height
    val loopCount get() = metadata.loopCount
    val backgroundColor get() = metadata.backgroundColor
    val frameCount get() = metadata.frameCount
    val hasAlpha get() = metadata.hasAlpha

    private val metadata by lazy(LazyThreadSafetyMode.NONE) { getMetadata(decoder) }

    fun decodeNextFrame(reuseBitmap: Bitmap?): DecodeFrameResult? {
        val outBitmap = reuseBitmap
            ?.takeIf {
                it.width == width &&
                        it.height == height &&
                        it.config == Bitmap.Config.ARGB_8888
            } ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (outBitmap.isPremultiplied != premultipliedAlpha) {
                outBitmap.isPremultiplied = premultipliedAlpha
            }
        }
        val duration = decodeNextFrame(decoder, outBitmap)
        return if (duration >= 0) {
            DecodeFrameResult(outBitmap, duration)
        } else {
            if (outBitmap !== reuseBitmap) {
                outBitmap.recycle()
            }
            null
        }
    }

    fun hasNextFrame(): Boolean {
        return hasNextFrame(decoder)
    }

    fun reset() {
        reset(decoder)
    }

    protected fun finalize() {
        deleteDecoder(decoder)
    }

    class DecodeFrameResult(
        val bitmap: Bitmap,
        val frameLengthMs: Int
    )

    private class Metadata(
        val width: Int,
        val height: Int,
        val loopCount: Int,
        val backgroundColor: Int,
        val frameCount: Int,
        val hasAlpha: Boolean
    )

    companion object {
        @JvmStatic
        private external fun createDecoder(
            byteBuffer: ByteBuffer,
            premultipliedAlpha: Boolean
        ): Long

        @JvmStatic
        private external fun deleteDecoder(decoder: Long)

        @JvmStatic
        private external fun getMetadata(decoder: Long): Metadata

        @JvmStatic
        private external fun hasNextFrame(decoder: Long): Boolean

        @JvmStatic
        private external fun decodeNextFrame(decoder: Long, outBitmap: Bitmap): Int

        @JvmStatic
        private external fun reset(decoder: Long)

        init {
            System.loadLibrary("libwebp")
        }

        fun create(
            byteBuffer: ByteBuffer,
            premultipliedAlpha: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        ): LibWebPAnimatedDecoder {
            val directBuffer = if (byteBuffer.isDirect) {
                byteBuffer
            } else {
                ByteBuffer.allocateDirect(byteBuffer.limit()).put(byteBuffer)
            }
            return LibWebPAnimatedDecoder(
                directBuffer,
                createDecoder(
                    directBuffer,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && premultipliedAlpha
                ),
                premultipliedAlpha
            )
        }
    }
}