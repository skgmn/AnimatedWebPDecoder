package com.github.skgmn.webpdecoder

import coil.bitmap.BitmapPool
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.Options
import coil.size.Size
import com.github.skgmn.webpdecoder.libwebp.AnimatedWebPDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import java.nio.ByteBuffer

class WebPDecoder(private val animationOnly: Boolean = false) : Decoder {
    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun decode(
        pool: BitmapPool,
        source: BufferedSource,
        size: Size,
        options: Options
    ): DecodeResult {
        // really wanted to avoid whole bytes copying but it's inevitable
        // unless the size of source is provided in advance
        val decoder = withContext(Dispatchers.IO) {
            val bytes = source.readByteArray()
            val byteBuffer = ByteBuffer.allocateDirect(bytes.size).put(bytes)
            AnimatedWebPDecoder.create(byteBuffer)
        }
        val drawable = AnimatedWebPDrawable(decoder, pool)
        return DecodeResult(drawable, false)
    }

    override fun handles(source: BufferedSource, mimeType: String?): Boolean {
        val headerBytes = source.peek().readByteArray(WebPSupportStatus.HEADER_SIZE)
        return (WebPSupportStatus.isWebpHeader(headerBytes, 0, headerBytes.size) &&
                (!animationOnly || WebPSupportStatus.isAnimatedWebpHeader(headerBytes, 0)))
    }
}