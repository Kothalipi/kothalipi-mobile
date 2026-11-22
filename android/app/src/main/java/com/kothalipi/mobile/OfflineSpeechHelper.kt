package com.kothalipi.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 100% Free, 0-MB-bloat OS-Native On-Device Offline Speech Recognizer for Android.
 * Uses Android's built-in SpeechRecognizer with EXTRA_PREFER_OFFLINE = true
 * so users can dictate in Bangla (bn-BD) or English (en-US) without internet or API keys!
 */
class OfflineSpeechHelper(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        mode: String, // "bn", "en", "auto"
        onPartial: (String) -> Unit,
        onAmplitude: (Float) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (!isAvailable) {
            onError("এই ডিভাইসে অফলাইন স্পিচ ইঞ্জিন পাওয়া যায়নি। Google App আপডেট করুন।")
            return false
        }

        stop()

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        val locale = when (mode) {
            "en" -> "en-US"
            else -> "bn-BD"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
                onAmplitude(normalized)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "অডিও রেকর্ডিং এরর হয়েছে।"
                    SpeechRecognizer.ERROR_NO_MATCH -> "কোনো কথা শোনা যায়নি। স্পষ্ট করে বলুন।"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কথা শোনা যায়নি। আবার চেষ্টা করুন।"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোন পারমিশন দিন।"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "অফলাইন বাংলা ল্যাঙ্গুয়েজ প্যাক ডাউনলোড করা নেই (Google Settings → Voice → Offline Speech Recognition থেকে Bangla ডাউনলোড করুন)।"
                    else -> "অফলাইন রিকগনিশন ব্যর্থ হয়েছে (Code: $error)।"
                }
                onError(msg)
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    onResult(text)
                } else {
                    onError("কোনো কথা শোনা যায়নি।")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotEmpty()) {
                    onPartial(partial)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        return try {
            isListening = true
            sr.startListening(intent)
            true
        } catch (e: Exception) {
            isListening = false
            onError("অফলাইন মাইক চালু করা যায়নি।")
            false
        }
    }

    fun stop() {
        isListening = false
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    fun release() {
        stop()
    }
}
