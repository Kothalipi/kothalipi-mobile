package com.kothalipi.mobile.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kothalipi.mobile.AudioRecorderHelper
import com.kothalipi.mobile.KothalipiApp
import com.kothalipi.mobile.MainActivity
import com.kothalipi.mobile.sanitizeErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wispr Flow-style Voice Keyboard for Android (InputMethodService).
 * Supports 4 modes (Bangla, English, Bangla->EN, Auto), quick punctuation,
 * Undo last dictation, Keyboard switcher (🌐), Space, Return, and Backspace.
 */
class KothalipiInputMethodService : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recorder = AudioRecorderHelper()

    private var isProcessing = false
    private var lastInsertedLength = 0

    private lateinit var micButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var modePill: TextView
    private lateinit var micBg: GradientDrawable

    private fun modeDisplay(mode: String): String = when (mode) {
        "en" -> "🇬🇧 English"
        "bn_to_en" -> "বাংলা ➔ 🇬🇧 EN"
        "auto" -> "✨ Auto (BN/EN)"
        else -> "🇧🇩 বাংলা"
    }

    private fun idlePrompt(mode: String): String = when (mode) {
        "en" -> "Tap mic to speak English"
        "bn_to_en" -> "বাংলায় বলুন, ইংরেজিতে টাইপ হবে"
        "auto" -> "বাংলা বা ইংরেজিতে বলুন"
        else -> "কথা বলতে মাইক চাপুন"
    }

    private fun pillDrawable(fill: String, stroke: String, radius: Float = 28f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(fill))
            setStroke(2, Color.parseColor(stroke))
        }
    }

    override fun onCreateInputView(): View {
        val app = KothalipiApp.instance

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0A14"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 18, 24, 22)
        }

        // Top Bar: Mode Switcher + Undo + Settings
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 12)
        }

        modePill = TextView(this).apply {
            text = modeDisplay(app.dictationMode)
            textSize = 13f
            setTextColor(Color.parseColor("#DDD6FE"))
            background = pillDrawable("#1B1730", "#4C3575", 36f)
            setPadding(28, 12, 28, 12)
            setOnClickListener {
                if (recorder.isRecording || isProcessing) return@setOnClickListener
                val next = when (app.dictationMode) {
                    "bn" -> "en"
                    "en" -> "bn_to_en"
                    "bn_to_en" -> "auto"
                    else -> "bn"
                }
                app.dictationMode = next
                text = modeDisplay(next)
                statusLabel.text = idlePrompt(next)
            }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        val undoBtn = TextView(this).apply {
            text = "↶ Undo"
            textSize = 12f
            setTextColor(Color.parseColor("#A1A1AA"))
            background = pillDrawable("#171426", "#2A2540", 28f)
            setPadding(22, 10, 22, 10)
            setOnClickListener {
                if (lastInsertedLength > 0) {
                    currentInputConnection?.deleteSurroundingText(lastInsertedLength, 0)
                    lastInsertedLength = 0
                    statusLabel.text = "মুছে ফেলা হয়েছে"
                }
            }
        }

        val settingsBtn = TextView(this).apply {
            text = "⚙️"
            textSize = 17f
            setPadding(18, 8, 8, 8)
            setOnClickListener {
                val intent = Intent(this@KothalipiInputMethodService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }

        topBar.addView(modePill)
        topBar.addView(spacer)
        topBar.addView(undoBtn)
        topBar.addView(settingsBtn)

        // Status label
        statusLabel = TextView(this).apply {
            text = idlePrompt(app.dictationMode)
            setTextColor(Color.parseColor("#9E9EA7"))
            textSize = 13f
            setPadding(8, 4, 8, 14)
            gravity = Gravity.CENTER
            maxLines = 2
        }

        // Hero Mic Button
        micBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.parseColor("#8A2BE2"), Color.parseColor("#6D28D9"))
        }

        micButton = Button(this).apply {
            text = "🎙️"
            textSize = 32f
            background = micBg
            elevation = 14f
            setOnClickListener { toggleRecording() }
        }

        val micParams = LinearLayout.LayoutParams(172, 172).apply {
            gravity = Gravity.CENTER
        }

        // Quick Punctuation Row
        val punctRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 10)
        }
        listOf("।", ",", "?", "!", ".").forEach { sym ->
            val key = TextView(this).apply {
                text = sym
                textSize = 16f
                setTextColor(Color.parseColor("#E4E4E7"))
                gravity = Gravity.CENTER
                background = pillDrawable("#171426", "#2B2542", 20f)
                setOnClickListener {
                    currentInputConnection?.commitText("$sym ", 1)
                }
            }
            val lp = LinearLayout.LayoutParams(86, 68).apply {
                setMargins(6, 0, 6, 0)
            }
            punctRow.addView(key, lp)
        }

        // Bottom Utility Row: 🌐 Switch Keyboard | Space | ↵ Enter | ⌫ Backspace
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 4, 0, 0)
        }

        val globeBtn = Button(this).apply {
            text = "🌐"
            textSize = 18f
            background = pillDrawable("#171426", "#2B2542", 22f)
            setOnClickListener { switchKeyboard() }
        }

        val spaceBtn = Button(this).apply {
            text = "Space"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.parseColor("#D4D4D8"))
            background = pillDrawable("#171426", "#2B2542", 22f)
            setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        }

        val enterBtn = Button(this).apply {
            text = "↵"
            textSize = 18f
            setTextColor(Color.parseColor("#DDD6FE"))
            background = pillDrawable("#231842", "#4C3575", 22f)
            setOnClickListener {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }

        val deleteBtn = Button(this).apply {
            text = "⌫"
            textSize = 18f
            setTextColor(Color.parseColor("#F4F4F5"))
            background = pillDrawable("#171426", "#2B2542", 22f)
            setOnClickListener { currentInputConnection?.deleteSurroundingText(1, 0) }
        }

        bottomRow.addView(globeBtn, LinearLayout.LayoutParams(0, 108, 1f).apply { setMargins(0, 0, 8, 0) })
        bottomRow.addView(spaceBtn, LinearLayout.LayoutParams(0, 108, 2.4f).apply { setMargins(0, 0, 8, 0) })
        bottomRow.addView(enterBtn, LinearLayout.LayoutParams(0, 108, 1f).apply { setMargins(0, 0, 8, 0) })
        bottomRow.addView(deleteBtn, LinearLayout.LayoutParams(0, 108, 1f))

        root.addView(topBar)
        root.addView(statusLabel)
        root.addView(micButton, micParams)
        root.addView(punctRow)
        root.addView(bottomRow)

        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::modePill.isInitialized) {
            val mode = KothalipiApp.instance.dictationMode
            modePill.text = modeDisplay(mode)
            if (!recorder.isRecording && !isProcessing) {
                statusLabel.text = idlePrompt(mode)
            }
        }
    }

    private fun switchKeyboard() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            false
        }
        if (!switched) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    private fun setMicStyle(recording: Boolean) {
        micBg.colors = if (recording) {
            intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#8A2BE2"))
        } else {
            intArrayOf(Color.parseColor("#8A2BE2"), Color.parseColor("#6D28D9"))
        }
        micButton.background = micBg
    }

    private fun toggleRecording() {
        if (isProcessing) return
        val app = KothalipiApp.instance

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            statusLabel.text = "অ্যাপ ওপেন করে মাইক পারমিশন দিন (⚙️)"
            statusLabel.setTextColor(Color.parseColor("#FCA5A5"))
            return
        }

        if (!app.hasAnyApiKey) {
            statusLabel.text = "⚙️ থেকে API Key সেট করুন"
            statusLabel.setTextColor(Color.parseColor("#FCD34D"))
            return
        }

        if (!recorder.isRecording) {
            val started = recorder.start(
                scope = scope,
                autoSilenceStop = app.autoSilenceStop,
                onSilenceDetected = {
                    mainHandler.post {
                        if (recorder.isRecording && !isProcessing) toggleRecording()
                    }
                },
                onError = { msg ->
                    mainHandler.post {
                        statusLabel.text = msg
                        statusLabel.setTextColor(Color.parseColor("#FCA5A5"))
                    }
                }
            )
            if (started) {
                micButton.text = "⏹️"
                setMicStyle(true)
                statusLabel.text = when (app.dictationMode) {
                    "en" -> "Listening… Speak English"
                    "bn_to_en" -> "শুনছি… ইংরেজিতে লিখব"
                    "auto" -> "শুনছি… বলুন"
                    else -> "শুনছি… বাংলায় বলুন"
                }
                statusLabel.setTextColor(Color.parseColor("#F472B6"))
            }
        } else {
            isProcessing = true
            micButton.text = "⏳"
            setMicStyle(false)
            statusLabel.text = "প্রসেসিং..."
            statusLabel.setTextColor(Color.parseColor("#C4B5FD"))

            scope.launch(Dispatchers.IO) {
                val wavBytes = recorder.stopAndGetWav()
                if (wavBytes == null) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        micButton.text = "🎙️"
                        statusLabel.text = "কোনো কথা শোনা যায়নি"
                        statusLabel.setTextColor(Color.parseColor("#FCA5A5"))
                    }
                    return@launch
                }

                try {
                    val result = app.engine.processRecordingFull(
                        wavBytes,
                        "audio/wav",
                        app.dictationMode,
                        app.writingStyle,
                        app.smartPolish
                    )
                    app.addHistoryItem(result.processedText, app.dictationMode, app.writingStyle)

                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        val textToInsert = result.processedText
                        currentInputConnection?.commitText(textToInsert, 1)
                        lastInsertedLength = textToInsert.length
                        statusLabel.text = "টাইপ হয়েছে ✓"
                        statusLabel.setTextColor(Color.parseColor("#6EE7B7"))
                        micButton.text = "🎙️"
                        mainHandler.postDelayed({
                            if (!recorder.isRecording && !isProcessing) {
                                statusLabel.text = idlePrompt(app.dictationMode)
                                statusLabel.setTextColor(Color.parseColor("#9E9EA7"))
                            }
                        }, 1400)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        statusLabel.text = sanitizeErrorMessage(e.message, app.geminiApiKey)
                        statusLabel.setTextColor(Color.parseColor("#FCA5A5"))
                        micButton.text = "🎙️"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        recorder.release()
        scope.cancel()
        super.onDestroy()
    }
}
