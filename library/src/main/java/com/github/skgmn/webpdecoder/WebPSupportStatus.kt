package com.github.skgmn.webpdecoder

import java.io.UnsupportedEncodingException
import java.lang.RuntimeException

// original source from https://github.com/facebook/fresco/blob/master/fbcore/src/main/java/com/facebook/common/webp/WebpSupportStatus.java
internal object WebPSupportStatus {
    const val HEADER_SIZE = 21L

    private const val SIMPLE_WEBP_HEADER_LENGTH = 20

    private val WEBP_RIFF_BYTES = asciiBytes("RIFF")
    private val WEBP_NAME_BYTES = asciiBytes("WEBP")
    private val WEBP_VP8X_BYTES: ByteArray = asciiBytes("VP8X")

    fun isWebpHeader(
        imageHeaderBytes: ByteArray, offset: Int, headerSize: Int
    ): Boolean {
        return headerSize >= SIMPLE_WEBP_HEADER_LENGTH &&
                matchBytePattern(imageHeaderBytes, offset, WEBP_RIFF_BYTES) &&
                matchBytePattern(imageHeaderBytes, offset + 8, WEBP_NAME_BYTES)
    }

    fun isAnimatedWebpHeader(imageHeaderBytes: ByteArray, offset: Int): Boolean {
        val isVp8x: Boolean = matchBytePattern(imageHeaderBytes, offset + 12, WEBP_VP8X_BYTES)
        // ANIM is 2nd bit (00000010 == 2) on 21st byte (imageHeaderBytes[20])
        val hasAnimationBit = (imageHeaderBytes[offset + 20].toInt() and 2) == 2
        return isVp8x && hasAnimationBit
    }

    private fun matchBytePattern(
        byteArray: ByteArray, offset: Int, pattern: ByteArray
    ): Boolean {
        if (pattern.size + offset > byteArray.size) {
            return false
        }
        for (i in pattern.indices) {
            if (byteArray[i + offset] != pattern[i]) {
                return false
            }
        }
        return true
    }

    private fun asciiBytes(value: String): ByteArray {
        return try {
            value.toByteArray(charset("ASCII"))
        } catch (uee: UnsupportedEncodingException) {
            // won't happen
            throw RuntimeException("ASCII not found!", uee)
        }
    }
}