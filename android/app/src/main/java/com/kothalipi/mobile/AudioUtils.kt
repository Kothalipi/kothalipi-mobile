package com.kothalipi.mobile

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Creates a standard 44-byte WAV header for 16-bit mono PCM audio data.
 */
fun createWavHeader(pcmDataSize: Int, sampleRate: Int = 16000, channels: Short = 1): ByteArray {
    val totalDataLen = pcmDataSize + 36
    val byteRate = sampleRate * channels * 2
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

    // RIFF header
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(totalDataLen)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))

    // "fmt " chunk
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // Sub-chunk 1 size (16 for PCM)
    header.putShort(1) // AudioFormat (1 for PCM)
    header.putShort(channels)
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort((channels * 2).toShort()) // Block align
    header.putShort(16) // Bits per sample

    // "data" chunk
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(pcmDataSize)

    return header.array()
}
