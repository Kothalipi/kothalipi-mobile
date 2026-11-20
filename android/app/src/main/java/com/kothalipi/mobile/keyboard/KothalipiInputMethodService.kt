package com.kothalipi.mobile.keyboard

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.kothalipi.mobile.KothalipiApp
import com.kothalipi.mobile.createWavHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Kothalipi Voice Keyboard Extension for Android
 * Provides a dedicated voice typing input method in any app (WhatsApp, Messenger, Notes, etc.)
 */
class KothalipiInputMethodService : InputMethodService() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val audioBuffer = ByteArrayOutputStream()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var micButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var modeButton: Button

    override fun onCreateInputView(): View {
        val app = KothalipiApp.instance

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#120F1D"))
            gravity = Gravity.CENTER
            setPadding(24, 32, 24, 32)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        modeButton = Button(this).apply {
            text = if (app.translateToEnglish) "বাংলা ➔ EN" else "বাংলা"
            setBackgroundColor(Color.parseColor("#1A1A24"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                app.translateToEnglish = !app.translateToEnglish
                text = if (app.translateToEnglish) "বাংলা ➔ EN" else "বাংলা"
            }
        }
        topBar.addView(modeButton)

        statusLabel = TextView(this).apply {
            text = "কথা বলতে মাইক চাপুন"
            setTextColor(Color.parseColor("#9E9EA7"))
            textSize = 14f
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER
        }

        micButton = Button(this).apply {
            text = "🎙️"
            textSize = 28f
            setBackgroundColor(Color.parseColor("#8A2BE2"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                toggleRecording()
            }
        }

        rootLayout.addView(topBar)
        rootLayout.addView(statusLabel)
        rootLayout.addView(micButton)

        return rootLayout
    }

    private fun toggleRecording() {
        val app = KothalipiApp.instance

        if (!isRecording) {
            audioBuffer.reset()
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                audioRecord?.startRecording()
                isRecording = true
                micButton.text = "⏹️"
                micButton.setBackgroundColor(Color.parseColor("#E254FF"))
                statusLabel.text = "শুনছি… বলুন"

                CoroutineScope(Dispatchers.IO).launch {
                    val data = ByteArray(1024)
                    while (isRecording && audioRecord != null) {
                        val read = audioRecord?.read(data, 0, data.size) ?: 0
                        if (read > 0) {
                            audioBuffer.write(data, 0, read)
                        }
                    }
                }
            } catch (e: SecurityException) {
                statusLabel.text = "মাইক পারমিশন নেই"
            }
        } else {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            micButton.text = "🎙️"
            micButton.setBackgroundColor(Color.parseColor("#8A2BE2"))
            statusLabel.text = "টেক্সট তৈরি হচ্ছে…"

            scope.launch(Dispatchers.IO) {
                val pcmData = audioBuffer.toByteArray()
                val wavHeader = createWavHeader(pcmData.size, 16000, 1)
                val fullWav = wavHeader + pcmData

                try {
                    val engine = uniffi.kothalipi_core.KothalipiMobileEngine(app.geminiApiKey)
                    val result = engine.processRecording(
                        fullWav,
                        "audio/wav",
                        app.translateToEnglish,
                        app.smartPolish
                    )

                    withContext(Dispatchers.Main) {
                        currentInputConnection?.commitText(result.processedText, 1)
                        statusLabel.text = "টাইপ করা হয়েছে!"
                        mainHandler.postDelayed({
                            statusLabel.text = "কথা বলতে মাইক চাপুন"
                        }, 1500)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusLabel.text = "এরর: ${e.message}"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        audioRecord?.release()
        audioRecord = null
        super.onDestroy()
    }
}
