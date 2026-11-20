package com.kothalipi.mobile.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
 * Wispr Flow-style Minimal Voice Keyboard for Android
 * Replaces standard keyboard with an ultra-clean voice dictation bar
 */
class KothalipiInputMethodService : InputMethodService() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val audioBuffer = ByteArrayOutputStream()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var micButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var modePill: TextView

    override fun onCreateInputView(): View {
        val app = KothalipiApp.instance

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0C0A14"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 24, 32, 28)
        }

        // Top Control Bar (Mode switcher & quick actions)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 20)
        }

        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(Color.parseColor("#1B172B"))
            setStroke(2, Color.parseColor("#3B2D5A"))
        }

        modePill = TextView(this).apply {
            text = if (app.translateToEnglish) "🇧🇩 বাংলা ➔ 🇬🇧 English" else "🇧🇩 বাংলা"
            textSize = 13f
            setTextColor(Color.parseColor("#A855F7"))
            background = pillBg
            setPadding(32, 14, 32, 14)
            setOnClickListener {
                app.translateToEnglish = !app.translateToEnglish
                text = if (app.translateToEnglish) "🇧🇩 বাংলা ➔ 🇬🇧 English" else "🇧🇩 বাংলা"
            }
        }

        topBar.addView(modePill)

        statusLabel = TextView(this).apply {
            text = "কথা বলতে মাইক চাপুন"
            setTextColor(Color.parseColor("#8E8CA0"))
            textSize = 14f
            setPadding(0, 10, 0, 18)
            gravity = Gravity.CENTER
        }

        // Center Hero Microphone Button
        val micBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.parseColor("#8A2BE2"), Color.parseColor("#6B21A8"))
        }

        micButton = Button(this).apply {
            text = "🎙️"
            textSize = 34f
            background = micBg
            elevation = 12f
            setOnClickListener {
                toggleRecording()
            }
        }

        val micParams = LinearLayout.LayoutParams(180, 180).apply {
            gravity = Gravity.CENTER
        }

        // Bottom Action Row (Delete, Space, Return)
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        val spaceBtn = Button(this).apply {
            text = "Space"
            setTextColor(Color.parseColor("#9E9EA7"))
            setBackgroundColor(Color.parseColor("#141122"))
            setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        }

        val deleteBtn = Button(this).apply {
            text = "⌫"
            setTextColor(Color.parseColor("#9E9EA7"))
            setBackgroundColor(Color.parseColor("#141122"))
            setOnClickListener { currentInputConnection?.deleteSurroundingText(1, 0) }
        }

        bottomRow.addView(spaceBtn)
        bottomRow.addView(deleteBtn)

        root.addView(topBar)
        root.addView(statusLabel)
        root.addView(micButton, micParams)
        root.addView(bottomRow)

        return root
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
                statusLabel.text = if (app.translateToEnglish) "শুনছি… ইংরেজিতে লিখব" else "শুনছি… বলুন"
                statusLabel.setTextColor(Color.parseColor("#EC4899"))

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
                statusLabel.text = "মাইক পারমিশন দিন"
            }
        } else {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            micButton.text = "⏳"
            statusLabel.text = "প্রসেসিং..."
            statusLabel.setTextColor(Color.parseColor("#8E8CA0"))

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
                        micButton.text = "🎙️"
                        mainHandler.postDelayed({
                            statusLabel.text = "কথা বলতে মাইক চাপুন"
                        }, 1200)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusLabel.text = "এরর: ${e.message}"
                        micButton.text = "🎙️"
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
