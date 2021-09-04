package com.github.skgmn.webpdecoder

import android.graphics.Bitmap
import coil.bitmap.BitmapPool
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.Options
import coil.size.Size
import com.github.skgmn.webpdecoder.libwebp.LibWebPAnimatedDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import java.nio.ByteBuffer

class AnimatedWebPDecoder : Decoder {
    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun decode(
        pool: BitmapPool,
        source: BufferedSource,
        size: Size,
        options: Options
    ): DecodeResult {
        // really wanted to avoid whole bytes copying but it's inevitable
        // unless the size of source is provided in advance
        val drawable = withContext(Dispatchers.IO) {
            val bytes = source.readByteArray()
            val byteBuffer = ByteBuffer.allocateDirect(bytes.size).put(bytes)
            val decoder = LibWebPAnimatedDecoder.create(byteBuffer, options.premultipliedAlpha)
            val firstFrame = if (decoder.hasNextFrame()) {
                val reuseBitmap = pool.getDirtyOrNull(
                    decoder.width,
                    decoder.height,
                    Bitmap.Config.ARGB_8888
                )
                decoder.decodeNextFrame(reuseBitmap)
            } else {
                null
            }
            AnimatedWebPDrawable(decoder, pool, firstFrame)
        }
        return DecodeResult(drawable, false)
    }

    override fun handles(source: BufferedSource, mimeType: String?): Boolean {
        val headerBytes = source.peek().readByteArray(WebPSupportStatus.HEADER_SIZE)
        return (WebPSupportStatus.isWebpHeader(headerBytes, 0, headerBytes.size) &&
                WebPSupportStatus.isAnimatedWebpHeader(headerBytes, 0))
    }
}