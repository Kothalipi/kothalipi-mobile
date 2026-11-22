package com.kothalipi.mobile.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kothalipi.mobile.AudioRecorderHelper
import com.kothalipi.mobile.KothalipiApp
import com.kothalipi.mobile.MainActivity
import com.kothalipi.mobile.R
import com.kothalipi.mobile.sanitizeErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wispr Flow-style Floating Voice Bubble Foreground Service for Android 8-14+.
 * Properly uses FOREGROUND_SERVICE_TYPE_MICROPHONE so background mic capture works on Android 14+.
 * Tap mic to record/stop, tap mode pill to switch (BN / EN / BN->EN / Auto), tap ✕ to close.
 */
class KothalipiFloatingService : Service() {

    companion object {
        const val ACTION_STOP = "com.kothalipi.mobile.ACTION_STOP_FLOATING"
        private const val CHANNEL_ID = "kothalipi_floating_channel"
        private const val NOTIFICATION_ID = 4201

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, KothalipiFloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KothalipiFloatingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recorder = AudioRecorderHelper()

    private var windowManager: WindowManager? = null
    private var bubbleContainer: FrameLayout? = null
    private var pillLayout: LinearLayout? = null
    private var micIcon: TextView? = null
    private var statusText: TextView? = null
    private var modeBadge: TextView? = null
    private var closeBtn: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isProcessing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "ফ্লোটিং বাবল চালুর জন্য Overlay পারমিশন দিন", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "মাইক্রোফোন পারমিশন দিন", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        if (bubbleContainer == null) {
            setupFloatingBubble()
        }
        _isRunning.value = true
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "কথালিপি ফ্লোটিং মাইক",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "যেকোনো অ্যাপে ভয়েস টাইপিং চালু রাখে"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, KothalipiFloatingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("কথালিপি ফ্লোটিং মাইক চালু আছে")
            .setContentText("যেকোনো অ্যাপে ভয়েস টাইপ করতে বাবলে ট্যাপ করুন")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "বন্ধ করুন", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun modeShortLabel(mode: String): String = when (mode) {
        "en" -> "EN"
        "bn_to_en" -> "BN➔EN"
        "auto" -> "AUTO"
        else -> "বাংলা"
    }

    private fun cycleMode() {
        val app = KothalipiApp.instance
        val next = when (app.dictationMode) {
            "bn" -> "en"
            "en" -> "bn_to_en"
            "bn_to_en" -> "auto"
            else -> "bn"
        }
        app.dictationMode = next
        modeBadge?.text = modeShortLabel(next)
    }

    private fun setupFloatingBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 220
            y = 420
        }
        layoutParams = params

        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(Color.parseColor("#EB131122"))
            setStroke(3, Color.parseColor("#66A855F7"))
        }

        pillLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pillBg
            setPadding(24, 16, 22, 16)
            elevation = 18f
        }

        micIcon = TextView(this).apply {
            text = "🎙️"
            textSize = 20f
            gravity = Gravity.CENTER
        }

        modeBadge = TextView(this).apply {
            text = modeShortLabel(KothalipiApp.instance.dictationMode)
            textSize = 11f
            setTextColor(Color.parseColor("#C4B5FD"))
            setPadding(14, 4, 10, 4)
            setOnClickListener {
                if (!recorder.isRecording && !isProcessing) {
                    cycleMode()
                }
            }
        }

        statusText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#F8F8FC"))
            setPadding(10, 0, 8, 0)
            visibility = View.GONE
        }

        closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.parseColor("#9E9EA7"))
            setPadding(10, 4, 4, 4)
            setOnClickListener { stopSelf() }
        }

        pillLayout?.addView(micIcon)
        pillLayout?.addView(modeBadge)
        pillLayout?.addView(statusText)
        pillLayout?.addView(closeBtn)

        bubbleContainer = FrameLayout(this).apply {
            addView(pillLayout)
        }

        // Draggable mic icon & pill with click-to-record + edge snapping
        micIcon?.setOnTouchListener(object : View.OnTouchListener {
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
                        if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            safeUpdateLayout()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            toggleRecording()
                        } else {
                            val midX = screenWidth / 2
                            params.x = if (params.x < midX) 16 else screenWidth - 260
                            safeUpdateLayout()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(bubbleContainer, params)
        } catch (e: Exception) {
            Toast.makeText(this, "ফ্লোটিং বাবল দেখানো যায়নি", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun safeUpdateLayout() {
        val container = bubbleContainer ?: return
        val params = layoutParams ?: return
        try {
            windowManager?.updateViewLayout(container, params)
        } catch (_: Exception) {
        }
    }

    private fun toggleRecording() {
        if (isProcessing) return
        val app = KothalipiApp.instance

        if (!app.hasAnyApiKey) {
            showTransientStatus("API Key দিন!", 2200)
            return
        }

        if (!recorder.isRecording) {
            val started = recorder.start(
                scope = serviceScope,
                autoSilenceStop = app.autoSilenceStop,
                onSilenceDetected = {
                    mainHandler.post {
                        if (recorder.isRecording && !isProcessing) toggleRecording()
                    }
                },
                onError = { msg ->
                    mainHandler.post { showTransientStatus(msg, 2200) }
                }
            )
            if (started) {
                modeBadge?.visibility = View.GONE
                statusText?.visibility = View.VISIBLE
                statusText?.text = when (app.dictationMode) {
                    "en" -> "Listening…"
                    "bn_to_en" -> "শুনছি ➔ EN…"
                    "auto" -> "শুনছি (Auto)…"
                    else -> "শুনছি…"
                }
                micIcon?.text = "⏹️"
                (pillLayout?.background as? GradientDrawable)?.apply {
                    setColor(Color.parseColor("#F52A153D"))
                    setStroke(4, Color.parseColor("#EC4899"))
                }
                safeUpdateLayout()
            }
        } else {
            isProcessing = true
            micIcon?.text = "⏳"
            statusText?.text = "প্রসেসিং..."
            safeUpdateLayout()

            serviceScope.launch(Dispatchers.IO) {
                val wavBytes = recorder.stopAndGetWav()
                if (wavBytes == null) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        showTransientStatus("কোনো কথা শোনা যায়নি", 1800)
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
                        val inserted = deliverText(result.processedText)
                        showTransientStatus(
                            if (inserted) "টাইপ হয়েছে ✓" else "কপি হয়েছে ✓ (Paste)",
                            1800
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        val msg = sanitizeErrorMessage(e.message, app.geminiApiKey)
                        showTransientStatus(msg.take(36), 2500)
                    }
                }
            }
        }
    }

    private fun showTransientStatus(message: String, durationMs: Long) {
        modeBadge?.visibility = View.GONE
        statusText?.visibility = View.VISIBLE
        statusText?.text = message
        micIcon?.text = "🎙️"
        (pillLayout?.background as? GradientDrawable)?.apply {
            setColor(Color.parseColor("#EB131122"))
            setStroke(3, Color.parseColor("#66A855F7"))
        }
        safeUpdateLayout()

        mainHandler.postDelayed({
            if (!recorder.isRecording && !isProcessing) {
                statusText?.visibility = View.GONE
                modeBadge?.visibility = View.VISIBLE
                modeBadge?.text = modeShortLabel(KothalipiApp.instance.dictationMode)
                safeUpdateLayout()
            }
        }, durationMs)
    }

    private fun deliverText(text: String): Boolean {
        if (text.isBlank()) return false
        // Always put in clipboard as reliable fallback
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Kothalipi", text))
        // Try direct Accessibility insertion if user enabled Kothalipi Auto-Type
        return KothalipiAccessibilityService.tryInsertText(text)
    }

    override fun onDestroy() {
        _isRunning.value = false
        recorder.release()
        serviceScope.cancel()
        bubbleContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubbleContainer = null
        super.onDestroy()
    }
}
