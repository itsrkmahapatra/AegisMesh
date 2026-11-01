package com.itsrkmahapatra.aegismesh.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Ultra-fast GZIP byte compression utility for P2P mesh payload transfers
 * and offline bundle decompression.
 */
object CompressionUtils {

    /**
     * Compresses UTF-8 string into GZIP byte array.
     */
    fun compressStringToGzip(input: String): ByteArray {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val byteOut = ByteArrayOutputStream(bytes.size)
        GZIPOutputStream(byteOut).use { gzipOut ->
            gzipOut.write(bytes)
            gzipOut.finish()
        }
        return byteOut.toByteArray()
    }

    /**
     * Decompresses GZIP byte array back into UTF-8 string.
     */
    fun decompressGzipToString(gzipBytes: ByteArray): String {
        ByteArrayInputStream(gzipBytes).use { byteIn ->
            GZIPInputStream(byteIn).use { gzipIn ->
                return gzipIn.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
        }
    }
}

