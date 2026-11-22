package com.kothalipi.mobile

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Creates a standard 44-byte WAV header for 16-bit mono PCM audio data.
 */
fun createWavHeader(pcmDataSize: Int, sampleRate: Int = 16000, channels: Short = 1): ByteArray {
    val totalDataLen = pcmDataSize + 36
    val byteRate = sampleRate * channels * 2
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(totalDataLen)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))

    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1)
    header.putShort(channels)
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort((channels * 2).toShort())
    header.putShort(16)

    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(pcmDataSize)

    return header.array()
}

/**
 * Computes normalized RMS amplitude (0.0..1.0) from 16-bit little-endian PCM bytes.
 */
fun computeRmsAmplitude(buffer: ByteArray, length: Int): Float {
    if (length < 2) return 0f
    var sum = 0.0
    val samples = length / 2
    var i = 0
    while (i + 1 < length) {
        val lo = buffer[i].toInt() and 0xFF
        val hi = buffer[i + 1].toInt()
        val sample = ((hi shl 8) or lo).toShort().toDouble()
        sum += sample * sample
        i += 2
    }
    val rms = sqrt(sum / samples)
    // Boost slightly so normal speaking voice fills the waveform nicely
    return ((rms / 12000.0).toFloat()).coerceIn(0f, 1f)
}

/**
 * Thread-safe, crash-proof 16kHz mono PCM AudioRecorder helper shared by
 * MainActivity, Voice Keyboard, and Floating Bubble.
 */
class AudioRecorderHelper {
    private val sampleRate = 16000
    private val isRecordingFlag = AtomicBoolean(false)
    private val lock = Any()

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var pcmStream = ByteArrayOutputStream()

    val isRecording: Boolean
        get() = isRecordingFlag.get()

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onAmplitude: (Float) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        synchronized(lock) {
            if (isRecordingFlag.get()) return true

            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = if (minBuf > 0) maxOf(minBuf * 2, 4096) else 4096

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                    ?: AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
            } catch (e: SecurityException) {
                onError("মাইক্রোফোন পারমিশন নেই। অনুগ্রহ করে পারমিশন দিন।")
                return false
            } catch (e: Exception) {
                onError("মাইক্রোফোন চালু করা যায়নি।")
                return false
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                onError("মাইক্রোফোন অন্য অ্যাপে ব্যবহৃত হচ্ছে বা পাওয়া যায়নি।")
                return false
            }

            pcmStream = ByteArrayOutputStream()
            audioRecord = recorder
            isRecordingFlag.set(true)

            try {
                recorder.startRecording()
            } catch (e: Exception) {
                isRecordingFlag.set(false)
                recorder.release()
                audioRecord = null
                onError("রেকর্ডিং শুরু করা যায়নি।")
                return false
            }

            recordJob = scope.launch(Dispatchers.IO) {
                val readBuf = ByteArray(1024)
                while (isRecordingFlag.get()) {
                    val read = try {
                        recorder.read(readBuf, 0, readBuf.size)
                    } catch (_: Exception) {
                        -1
                    }
                    if (read > 0) {
                        synchronized(pcmStream) {
                            pcmStream.write(readBuf, 0, read)
                        }
                        onAmplitude(computeRmsAmplitude(readBuf, read))
                    } else if (read < 0) {
                        break
                    }
                }
            }
            return true
        }
    }

    /**
     * Safely stops recording, waits for the reader coroutine to finish, and returns WAV bytes.
     */
    suspend fun stopAndGetWav(): ByteArray? {
        val recorderToStop: AudioRecord?
        val jobToJoin: Job?

        synchronized(lock) {
            if (!isRecordingFlag.getAndSet(false)) return null
            recorderToStop = audioRecord
            audioRecord = null
            jobToJoin = recordJob
            recordJob = null
        }

        try {
            recorderToStop?.stop()
        } catch (_: Exception) {
        }

        // Wait cleanly for IO reader loop to exit before releasing & reading buffer
        try {
            jobToJoin?.join()
        } catch (_: Exception) {
        }

        try {
            recorderToStop?.release()
        } catch (_: Exception) {
        }

        val pcmData = synchronized(pcmStream) { pcmStream.toByteArray() }
        if (pcmData.size < 1600) {
            return null
        }
        return createWavHeader(pcmData.size, sampleRate, 1) + pcmData
    }

    fun release() {
        isRecordingFlag.set(false)
        synchronized(lock) {
            try {
                audioRecord?.stop()
            } catch (_: Exception) {
            }
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
            audioRecord = null
            recordJob?.cancel()
            recordJob = null
        }
    }
}

/**
 * Converts low-level Rust/HTTP exceptions into clean, user-friendly Bengali messages
 * while ensuring the API key is never leaked to the UI.
 */
fun sanitizeErrorMessage(raw: String?, apiKeyToScrub: String = ""): String {
    if (raw.isNullOrBlank()) return "অপ্রত্যাশিত এরর ঘটেছে। আবার চেষ্টা করুন।"
    val scrubbed = if (apiKeyToScrub.isNotBlank()) raw.replace(apiKeyToScrub, "••••") else raw
    val lower = scrubbed.lowercase()
    return when {
        lower.contains("missing") && lower.contains("key") ->
            "Gemini API Key সেট করা নেই! সেটিংস (⚙️) থেকে আপনার ফ্রি API Key দিন।"
        lower.contains("api_key_invalid") || lower.contains("401") || lower.contains("403") || lower.contains("badapikey") ->
            "ভুল বা অকার্যকর API Key! সেটিংস থেকে সঠিক Google AI Studio Key দিন।"
        lower.contains("429") || lower.contains("resource_exhausted") || lower.contains("quota") || lower.contains("ratelimit") ->
            "ফ্রি API কোটা সীমা পূর্ণ হয়েছে। কয়েক সেকেন্ড পর আবার চেষ্টা করুন।"
        lower.contains("503") || lower.contains("unavailable") || lower.contains("high demand") ->
            "Google Gemini সার্ভারে সাময়িক চাপ রয়েছে। একটু পর আবার চেষ্টা করুন।"
        lower.contains("too short") || lower.contains("empty") || lower.contains("no speech") || lower.contains("invalidaudio") ->
            "কোনো কথা শোনা যায়নি। অনুগ্রহ করে মাইক্রোফোনে স্পষ্ট করে বলুন।"
        lower.contains("timeout") || lower.contains("dns") || lower.contains("network") || lower.contains("connection") || lower.contains("failed to lookup") ->
            "ইন্টারনেট সংযোগ পাওয়া যায়নি। নেটওয়ার্ক চেক করে আবার চেষ্টা করুন।"
        else -> "প্রসেসিং ব্যর্থ হয়েছে। আবার চেষ্টা করুন।"
    }
}

data class DictationHistoryItem(
    val id: Long,
    val text: String,
    val mode: String,
    val style: String,
    val timestamp: Long
)
