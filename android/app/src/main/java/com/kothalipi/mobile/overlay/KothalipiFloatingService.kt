package com.kothalipi.mobile.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
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
 * Wispr Flow-style Floating Bubble and Accessibility Assistant for Android.
 * Floats unobtrusively on the screen edge over WhatsApp, Gmail, Slack, etc.
 * Tap to record -> speaks in Bangla -> types directly into the active text field!
 */
class KothalipiFloatingService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var bubbleContainer: FrameLayout? = null
    private var pillLayout: LinearLayout? = null
    private var micIcon: TextView? = null
    private var statusText: TextView? = null

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val audioBuffer = ByteArrayOutputStream()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastFocusedNode: AccessibilityNodeInfo? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            val source = event.source
            if (source != null && source.isEditable) {
                lastFocusedNode = source
            }
        }
    }

    override fun onInterrupt() {}

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingBubble()
    }

    private fun setupFloatingBubble() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 180
            y = 400
        }

        // Wispr Flow style glassmorphic pill background
        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 48f
            setColor(Color.parseColor("#E612101F")) // Dark glass
            setStroke(2, Color.parseColor("#40A855F7")) // Subtle violet glow border
        }

        pillLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pillBg
            setPadding(24, 16, 28, 16)
            elevation = 16f
        }

        micIcon = TextView(this).apply {
            text = "🎙️"
            textSize = 20f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "ক্থালিপি"
            textSize = 13f
            setTextColor(Color.parseColor("#F5F5F7"))
            setPadding(16, 0, 0, 0)
            visibility = View.GONE
        }

        pillLayout?.addView(micIcon)
        pillLayout?.addView(statusText)

        bubbleContainer = FrameLayout(this).apply {
            addView(pillLayout)
        }

        // Draggable touch listener with smooth edge snapping
        pillLayout?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager?.updateViewLayout(bubbleContainer, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Clicked! Toggle Wispr Flow dictation
                            toggleRecording(params)
                        } else {
                            // Snap to nearest screen edge
                            val midX = screenWidth / 2
                            params.x = if (params.x < midX) 20 else screenWidth - 180
                            windowManager?.updateViewLayout(bubbleContainer, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(bubbleContainer, params)
    }

    private fun toggleRecording(params: WindowManager.LayoutParams) {
        val app = KothalipiApp.instance

        if (!isRecording) {
            // Start recording
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

                // Wispr Flow expanding pill animation
                statusText?.visibility = View.VISIBLE
                statusText?.text = if (app.translateToEnglish) "শুনছি ➔ EN..." else "শুনছি..."
                micIcon?.text = "⏹️"

                (pillLayout?.background as? GradientDrawable)?.apply {
                    setColor(Color.parseColor("#F2251B38"))
                    setStroke(3, Color.parseColor("#EC4899")) // Glowing pink-violet
                }
                windowManager?.updateViewLayout(bubbleContainer, params)

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
                statusText?.text = "মাইক পারমিশন দিন"
            }
        } else {
            // Stop recording & Process through Rust Engine
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            micIcon?.text = "⏳"
            statusText?.text = "প্রসেসিং..."
            windowManager?.updateViewLayout(bubbleContainer, params)

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
                        insertTextIntoActiveField(result.processedText)
                        statusText?.text = "টাইপ হয়েছে ✓"

                        mainHandler.postDelayed({
                            statusText?.visibility = View.GONE
                            micIcon?.text = "🎙️"
                            (pillLayout?.background as? GradientDrawable)?.apply {
                                setColor(Color.parseColor("#E612101F"))
                                setStroke(2, Color.parseColor("#40A855F7"))
                            }
                            windowManager?.updateViewLayout(bubbleContainer, params)
                        }, 1200)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText?.text = "এরর!"
                        mainHandler.postDelayed({
                            statusText?.visibility = View.GONE
                            micIcon?.text = "🎙️"
                            windowManager?.updateViewLayout(bubbleContainer, params)
                        }, 2000)
                    }
                }
            }
        }
    }

    private fun insertTextIntoActiveField(text: String) {
        if (text.isEmpty()) return

        // 1. Try direct AccessibilityNodeInfo set text
        val node = lastFocusedNode ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node != null && node.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                return
            }
        }

        // 2. Fallback: Copy to Clipboard for instant paste
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Kothalipi", text)
        clipboard.setPrimaryClip(clip)

        // Try pasting via accessibility action
        node?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override fun onDestroy() {
        audioRecord?.release()
        audioRecord = null
        bubbleContainer?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }
}
