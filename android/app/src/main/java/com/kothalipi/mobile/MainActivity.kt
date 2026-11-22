package com.kothalipi.mobile

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kothalipi.mobile.overlay.KothalipiAccessibilityService
import com.kothalipi.mobile.overlay.KothalipiFloatingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BgObsidian = Color(0xFF08070F)
private val CardDark = Color(0xFF131120)
private val CardElevated = Color(0xFF1A172B)
private val BorderSubtle = Color(0x2EA855F7)
private val AccentViolet = Color(0xFF8B5CF6)
private val AccentPurple = Color(0xFF7C3AED)
private val AccentPink = Color(0xFFEC4899)
private val AccentCyan = Color(0xFF06B6D4)
private val AccentGreen = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)
private val TextPrimary = Color(0xFFF8F8FC)
private val TextSecondary = Color(0xFF9E9CB3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KothalipiMainScreen(this)
        }
    }
}

private fun isKothalipiKeyboardEnabled(context: Context): Boolean {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    } catch (_: Exception) {
        false
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kothalipi", text))
}

private fun shareText(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "শেয়ার করুন"))
}

private fun modeLabel(mode: String): String = when (mode) {
    "en" -> "English"
    "bn_to_en" -> "বাংলা ➔ EN"
    "auto" -> "Auto"
    else -> "বাংলা"
}

private fun styleLabel(style: String): String = when (style) {
    "formal" -> "ফর্মাল"
    "casual" -> "চ্যাট"
    "bullets" -> "পয়েন্ট"
    else -> "স্বাভাবিক"
}

@Composable
fun KothalipiMainScreen(context: Context) {
    val app = KothalipiApp.instance
    val scope = rememberCoroutineScope()
    val recorder = remember { AudioRecorderHelper() }
    val offlineSpeech = remember { OfflineSpeechHelper(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Dictate, 1 = Snippets, 2 = History
    var isRecording by remember { mutableStateOf(false) }
    var isVoiceEditRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    var dictationMode by remember { mutableStateOf(app.dictationMode) }
    var writingStyle by remember { mutableStateOf(app.writingStyle) }
    var smartPolish by remember { mutableStateOf(app.smartPolish) }
    var autoSilenceStop by remember { mutableStateOf(app.autoSilenceStop) }
    var offlineMode by remember { mutableStateOf(app.offlineMode) }
    var hasAnyKey by remember { mutableStateOf(app.hasAnyApiKey) }
    var primaryProvider by remember { mutableStateOf(app.primaryProvider) }

    var resultText by remember { mutableStateOf("") }
    var copiedRecently by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var currentAmplitude by remember { mutableFloatStateOf(0f) }
    val waveBars = remember { mutableStateListOf<Float>().apply { repeat(12) { add(0.15f) } } }

    val isFloatingRunning by KothalipiFloatingService.isRunning.collectAsState()
    val isAccessibilityOn by KothalipiAccessibilityService.isConnected.collectAsState()
    val historyItems by app.history.collectAsState()
    val snippetItems by app.snippets.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            offlineSpeech.release()
        }
    }

    LaunchedEffect(isRecording || isVoiceEditRecording) {
        if (isRecording || isVoiceEditRecording) {
            recordingSeconds = 0
            while (isRecording || isVoiceEditRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "মাইক্রোফোন পারমিশন প্রয়োজন", Toast.LENGTH_SHORT).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        KothalipiFloatingService.start(context)
    }

    fun toggleFloatingBubble() {
        if (isFloatingRunning) {
            KothalipiFloatingService.stop(context)
            Toast.makeText(context, "ফ্লোটিং বাবল বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
            Toast.makeText(context, "ফ্লোটিং বাবলের জন্য 'Display over other apps' অনুমতি দিন", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            KothalipiFloatingService.start(context)
            Toast.makeText(context, "ফ্লোটিং বাবল চালু হয়েছে ✓", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndTranscribe(isVoiceEdit: Boolean) {
        if (offlineMode && !isVoiceEdit) {
            isRecording = false
            currentAmplitude = 0f
            offlineSpeech.stop()
            return
        }
        isRecording = false
        isVoiceEditRecording = false
        isProcessing = true
        currentAmplitude = 0f

        scope.launch(Dispatchers.IO) {
            val wavBytes = recorder.stopAndGetWav()
            if (wavBytes == null) {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    errorMessage = "কোনো কথা শোনা যায়নি। অনুগ্রহ করে মাইক্রোফোনে স্পষ্ট করে বলুন।"
                }
                return@launch
            }

            try {
                val result = if (isVoiceEdit && resultText.isNotBlank()) {
                    app.engine.voiceEditText(resultText, wavBytes, "audio/wav")
                } else {
                    app.engine.processRecordingFull(
                        wavBytes,
                        "audio/wav",
                        dictationMode,
                        writingStyle,
                        smartPolish
                    )
                }
                app.addHistoryItem(result.processedText, dictationMode, writingStyle)
                withContext(Dispatchers.Main) {
                    resultText = result.processedText
                    errorMessage = ""
                    isProcessing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = sanitizeErrorMessage(e.message, app.geminiApiKey)
                    isProcessing = false
                }
            }
        }
    }

    fun startRecordingInternal(isVoiceEdit: Boolean) {
        if (isProcessing) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        // 100% Free OS-Native On-Device Offline Speech Mode (works without internet or API key!)
        if (offlineMode && !isVoiceEdit) {
            errorMessage = ""
            copiedRecently = false
            val ok = offlineSpeech.start(
                mode = dictationMode,
                onPartial = { partial ->
                    resultText = partial
                },
                onAmplitude = { amp ->
                    currentAmplitude = amp
                    if (waveBars.isNotEmpty()) {
                        waveBars.removeAt(0)
                        waveBars.add(amp.coerceIn(0.12f, 1f))
                    }
                },
                onResult = { finalText ->
                    isRecording = false
                    currentAmplitude = 0f
                    resultText = finalText
                    app.addHistoryItem(finalText, dictationMode, "offline")
                },
                onError = { err ->
                    isRecording = false
                    currentAmplitude = 0f
                    errorMessage = err
                }
            )
            if (ok) isRecording = true
            return
        }

        if (!app.hasAnyApiKey) {
            errorMessage = "কোনো API Key সেট করা নেই! সেটিংস থেকে ফ্রি Gemini/Groq Key দিন অথবা 'Offline' মোড অন করুন।"
            showSettingsDialog = true
            return
        }

        errorMessage = ""
        copiedRecently = false
        val started = recorder.start(
            scope = scope,
            autoSilenceStop = autoSilenceStop,
            onAmplitude = { amp ->
                currentAmplitude = amp
                scope.launch(Dispatchers.Main) {
                    if (waveBars.isNotEmpty()) {
                        waveBars.removeAt(0)
                        waveBars.add(amp.coerceIn(0.12f, 1f))
                    }
                }
            },
            onSilenceDetected = {
                scope.launch(Dispatchers.Main) {
                    if (recorder.isRecording && !isProcessing) {
                        stopAndTranscribe(isVoiceEdit)
                    }
                }
            },
            onError = { msg ->
                scope.launch(Dispatchers.Main) {
                    errorMessage = msg
                    isRecording = false
                    isVoiceEditRecording = false
                }
            }
        )
        if (started) {
            if (isVoiceEdit) isVoiceEditRecording = true else isRecording = true
        }
    }

    fun handleMicToggle() {
        if (isProcessing) return
        if (isRecording) {
            stopAndTranscribe(isVoiceEdit = false)
        } else if (isVoiceEditRecording) {
            stopAndTranscribe(isVoiceEdit = true)
        } else {
            startRecordingInternal(isVoiceEdit = false)
        }
    }

    fun handleVoiceEditToggle() {
        if (isProcessing || resultText.isBlank()) return
        if (isVoiceEditRecording) {
            stopAndTranscribe(isVoiceEdit = true)
        } else if (!isRecording) {
            startRecordingInternal(isVoiceEdit = true)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgObsidian
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (isRecording || isVoiceEditRecording) Color(0x33EC4899) else Color(0x297C3AED),
                                Color.Transparent
                            ),
                            radius = 750f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(AccentPurple, AccentPink))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "কথালিপি",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showSettingsDialog = true }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (hasAnyKey) AccentGreen else AccentAmber)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val provBadge = when {
                                    offlineMode -> "Offline Mode (Free)"
                                    !hasAnyKey -> "API Key দিন"
                                    primaryProvider == "groq" -> "Groq Whisper Ready"
                                    primaryProvider == "openai" -> "OpenAI Ready"
                                    else -> "AI Ready (Auto)"
                                }
                                Text(
                                    text = provBadge,
                                    fontSize = 11.sp,
                                    color = if (offlineMode) AccentCyan else if (hasAnyKey) AccentGreen else AccentAmber,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isFloatingRunning) Color(0x338B5CF6) else CardDark)
                                .border(
                                    1.dp,
                                    if (isFloatingRunning) AccentViolet else BorderSubtle,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { toggleFloatingBubble() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.BubbleChart,
                                    contentDescription = "Floating Bubble",
                                    tint = if (isFloatingRunning) AccentViolet else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isFloatingRunning) "ON" else "Bubble",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isFloatingRunning) Color.White else TextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDark)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .clickable { showSettingsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Main Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> DictateTabContent(
                            context = context,
                            hasApiKey = hasAnyKey,
                            dictationMode = dictationMode,
                            writingStyle = writingStyle,
                            smartPolish = smartPolish,
                            autoSilenceStop = autoSilenceStop,
                            offlineMode = offlineMode,
                            isRecording = isRecording,
                            isVoiceEditRecording = isVoiceEditRecording,
                            isProcessing = isProcessing,
                            isTranslating = isTranslating,
                            recordingSeconds = recordingSeconds,
                            currentAmplitude = currentAmplitude,
                            waveBars = waveBars,
                            resultText = resultText,
                            copiedRecently = copiedRecently,
                            errorMessage = errorMessage,
                            isFloatingRunning = isFloatingRunning,
                            isAccessibilityOn = isAccessibilityOn,
                            onModeChange = {
                                dictationMode = it
                                app.dictationMode = it
                                errorMessage = ""
                            },
                            onStyleChange = {
                                writingStyle = it
                                app.writingStyle = it
                            },
                            onTogglePolish = {
                                smartPolish = !smartPolish
                                app.smartPolish = smartPolish
                            },
                            onToggleVad = {
                                autoSilenceStop = !autoSilenceStop
                                app.autoSilenceStop = autoSilenceStop
                                Toast.makeText(
                                    context,
                                    if (autoSilenceStop) "Auto-Stop (২ সেকেন্ড চুপ থাকলে বন্ধ) চালু ✓" else "Auto-Stop বন্ধ",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleOffline = {
                                offlineMode = !offlineMode
                                app.offlineMode = offlineMode
                                Toast.makeText(
                                    context,
                                    if (offlineMode) "Offline Mode চালু (ইন্টারনেট/API ছাড়াই ফ্রি!) ✓" else "Cloud AI Mode চালু",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onMicToggle = { handleMicToggle() },
                            onHoldStart = {
                                if (!isRecording && !isVoiceEditRecording && !isProcessing) {
                                    startRecordingInternal(isVoiceEdit = false)
                                }
                            },
                            onHoldRelease = {
                                if (isRecording && !isProcessing) {
                                    stopAndTranscribe(isVoiceEdit = false)
                                }
                            },
                            onVoiceEditToggle = { handleVoiceEditToggle() },
                            onOpenSettings = { showSettingsDialog = true },
                            onCopy = {
                                copyToClipboard(context, resultText)
                                copiedRecently = true
                                Toast.makeText(context, "কপি করা হয়েছে ✓", Toast.LENGTH_SHORT).show()
                            },
                            onShare = { shareText(context, resultText) },
                            onClear = {
                                resultText = ""
                                copiedRecently = false
                            },
                            onTranslateResult = {
                                if (resultText.isBlank() || isTranslating) return@DictateTabContent
                                isTranslating = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val translated = app.engine.translateBanglaToEnglish(resultText)
                                        withContext(Dispatchers.Main) {
                                            resultText = translated
                                            isTranslating = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            errorMessage = sanitizeErrorMessage(e.message, app.geminiApiKey)
                                            isTranslating = false
                                        }
                                    }
                                }
                            },
                            onToggleBubble = { toggleFloatingBubble() }
                        )
                        1 -> SnippetsTabContent(
                            snippets = snippetItems,
                            onAdd = { trig, content -> app.addSnippet(trig, content) },
                            onDelete = { app.deleteSnippet(it) }
                        )
                        else -> HistoryTabContent(
                            context = context,
                            items = historyItems,
                            onDelete = { app.deleteHistoryItem(it) },
                            onClearAll = { app.clearHistory() }
                        )
                    }
                }

                // Bottom Navigation Bar (3 Tabs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BottomNavItem(
                        label = "ডিক্টেশন",
                        icon = Icons.Rounded.Mic,
                        selected = selectedTab == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 0 }
                    )
                    BottomNavItem(
                        label = "স্ন্যাপেট (${snippetItems.size})",
                        icon = Icons.Rounded.Bolt,
                        selected = selectedTab == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 1 }
                    )
                    BottomNavItem(
                        label = "হিস্ট্রি",
                        icon = Icons.Rounded.History,
                        selected = selectedTab == 2,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 2 }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            context = context,
            initialGeminiKey = app.geminiApiKey,
            initialGroqKey = app.groqApiKey,
            initialOpenaiKey = app.openaiApiKey,
            initialProvider = primaryProvider,
            initialSmartPolish = smartPolish,
            initialAutoSilence = autoSilenceStop,
            isAccessibilityOn = isAccessibilityOn,
            onDismiss = { showSettingsDialog = false },
            onSave = { gKey, grKey, oaKey, prov, polish, vad ->
                app.geminiApiKey = gKey.trim()
                app.groqApiKey = grKey.trim()
                app.openaiApiKey = oaKey.trim()
                app.primaryProvider = prov
                app.smartPolish = polish
                app.autoSilenceStop = vad

                hasAnyKey = app.hasAnyApiKey
                primaryProvider = prov
                smartPolish = polish
                autoSilenceStop = vad
                errorMessage = ""
                showSettingsDialog = false
                Toast.makeText(context, "সেটিংস সংরক্ষণ করা হয়েছে ✓", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(AccentPurple, AccentViolet))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = if (selected) Color.White else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DictateTabContent(
    context: Context,
    hasApiKey: Boolean,
    dictationMode: String,
    writingStyle: String,
    smartPolish: Boolean,
    autoSilenceStop: Boolean,
    offlineMode: Boolean,
    isRecording: Boolean,
    isVoiceEditRecording: Boolean,
    isProcessing: Boolean,
    isTranslating: Boolean,
    recordingSeconds: Int,
    currentAmplitude: Float,
    waveBars: List<Float>,
    resultText: String,
    copiedRecently: Boolean,
    errorMessage: String,
    isFloatingRunning: Boolean,
    isAccessibilityOn: Boolean,
    onModeChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onTogglePolish: () -> Unit,
    onToggleVad: () -> Unit,
    onToggleOffline: () -> Unit,
    onMicToggle: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldRelease: () -> Unit,
    onVoiceEditToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onTranslateResult: () -> Unit,
    onToggleBubble: () -> Unit
) {
    val activeRecording = isRecording || isVoiceEditRecording
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (activeRecording) 1.18f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val voiceBoost by animateFloatAsState(
        targetValue = if (activeRecording) currentAmplitude * 0.25f else 0f,
        animationSpec = tween(90),
        label = "voiceBoost"
    )

    val keyboardEnabled = remember { isKothalipiKeyboardEnabled(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasApiKey) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { onOpenSettings() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x26F59E0B)),
                border = BorderStroke(1.dp, Color(0x66F59E0B))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gemini / Groq API Key সেট করুন",
                            color = Color(0xFFFDE68A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "ফ্রি API Key বসাতে এখানে ট্যাপ করুন",
                            color = Color(0xFFD4D4D8),
                            fontSize = 11.sp
                        )
                    }
                    Text("Setup →", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // 4-Mode Pill Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val modes = listOf(
                Triple("bn", "বাংলা", listOf(AccentPurple, AccentViolet)),
                Triple("en", "English", listOf(Color(0xFF2563EB), AccentCyan)),
                Triple("bn_to_en", "বাংলা➔EN", listOf(AccentPurple, AccentPink)),
                Triple("auto", "Auto", listOf(Color(0xFF059669), AccentCyan))
            )
            modes.forEach { (id, title, colors) ->
                val selected = dictationMode == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) Brush.horizontalGradient(colors)
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { onModeChange(id) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Writing Style + Smart Polish + Auto-Silence VAD Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (smartPolish) Color(0x3310B981) else CardDark)
                    .border(1.dp, if (smartPolish) AccentGreen else BorderSubtle, RoundedCornerShape(14.dp))
                    .clickable { onTogglePolish() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (smartPolish) AccentGreen else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Polish",
                        color = if (smartPolish) Color(0xFFA7F3D0) else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Auto-Silence Stop (VAD) toggle chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (autoSilenceStop) Color(0x3306B6D4) else CardDark)
                    .border(1.dp, if (autoSilenceStop) AccentCyan else BorderSubtle, RoundedCornerShape(14.dp))
                    .clickable { onToggleVad() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = if (autoSilenceStop) AccentCyan else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Auto-Stop",
                        color = if (autoSilenceStop) Color(0xFFA5F3FC) else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Free On-Device Offline Mode chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (offlineMode) Color(0x33F59E0B) else CardDark)
                    .border(1.dp, if (offlineMode) AccentAmber else BorderSubtle, RoundedCornerShape(14.dp))
                    .clickable { onToggleOffline() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (offlineMode) "Offline ON" else "Offline",
                    color = if (offlineMode) Color(0xFFFDE68A) else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            listOf("normal", "formal", "casual", "bullets").forEach { style ->
                val active = writingStyle == style
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) Color(0x338B5CF6) else CardDark)
                        .border(1.dp, if (active) AccentViolet else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                        .clickable { onStyleChange(style) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = styleLabel(style),
                        color = if (active) Color.White else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Center Hero Microphone Orb — supports BOTH Tap-to-Toggle AND Hold-to-Talk!
        Box(
            modifier = Modifier.size(172.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .scale(basePulse + voiceBoost)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (activeRecording) listOf(Color(0x66EC4899), Color(0x1A8B5CF6), Color.Transparent)
                            else listOf(Color(0x338B5CF6), Color.Transparent)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(108.dp)
                    .shadow(24.dp, CircleShape, spotColor = if (activeRecording) AccentPink else AccentViolet)
                    .clip(CircleShape)
                    .background(
                        when {
                            isProcessing -> Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF581C87)))
                            isVoiceEditRecording -> Brush.linearGradient(listOf(AccentAmber, AccentPink))
                            isRecording -> Brush.linearGradient(listOf(AccentPink, AccentPurple))
                            dictationMode == "en" -> Brush.linearGradient(listOf(Color(0xFF2563EB), AccentCyan))
                            dictationMode == "bn_to_en" -> Brush.linearGradient(listOf(AccentPurple, AccentPink))
                            dictationMode == "auto" -> Brush.linearGradient(listOf(Color(0xFF059669), AccentCyan))
                            else -> Brush.linearGradient(listOf(AccentViolet, AccentPurple))
                        }
                    )
                    .pointerInput(isProcessing, isRecording, isVoiceEditRecording) {
                        if (isProcessing) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            var startedByHold = false

                            // If not already recording, start hold-to-talk if held > 320ms
                            val wasAlreadyRecording = isRecording || isVoiceEditRecording
                            val holdJob = if (!wasAlreadyRecording) {
                                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                    delay(320)
                                    startedByHold = true
                                    onHoldStart()
                                }
                            } else null

                            waitForUpOrCancellation()
                            holdJob?.cancel()

                            val heldMs = System.currentTimeMillis() - downTime
                            if (startedByHold && heldMs >= 320) {
                                onHoldRelease()
                            } else {
                                onMicToggle()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (activeRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (activeRecording) "Stop" else "Record",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = activeRecording, enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.height(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    waveBars.forEach { level ->
                        val barH by animateDpAsState(
                            targetValue = (8 + (level * 24)).dp,
                            animationSpec = tween(80),
                            label = "barH"
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(barH)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.verticalGradient(listOf(AccentPink, AccentViolet)))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val mm = recordingSeconds / 60
                val ss = recordingSeconds % 60
                Text(
                    text = String.format(Locale.US, "%02d:%02d", mm, ss),
                    color = if (isVoiceEditRecording) AccentAmber else AccentPink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when {
                isProcessing -> "AI প্রসেস করছে…"
                isVoiceEditRecording -> "এডিট কমান্ড বলুন (যেমন: ফর্মাল ইমেইল বানাও)…"
                isRecording -> when (dictationMode) {
                    "en" -> "Listening… Speak in English"
                    "bn_to_en" -> "শুনছি… বাংলায় বলুন, ইংরেজিতে হবে"
                    "auto" -> "শুনছি… বাংলা বা ইংরেজিতে বলুন"
                    else -> "শুনছি… বাংলায় বলুন"
                }
                else -> "ট্যাপ করুন অথবা চেপে ধরে কথা বলুন"
            },
            color = if (isVoiceEditRecording) AccentAmber else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x29EF4444)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x66EF4444))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Result Card with Voice Edit Command button!
        if (resultText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val wordCount = resultText.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                        Text(
                            text = "${modeLabel(dictationMode)} • $wordCount শব্দ",
                            color = AccentViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    SelectionContainer {
                        Text(
                            text = resultText,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            lineHeight = 25.sp,
                            modifier = Modifier.heightIn(max = 200.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onCopy,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copiedRecently) AccentGreen else AccentPurple
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (copiedRecently) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(if (copiedRecently) "Copied" else "Copy", fontSize = 12.sp)
                        }

                        // Voice Edit Command button!
                        Button(
                            onClick = onVoiceEditToggle,
                            enabled = !isProcessing && !isRecording,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVoiceEditRecording) AccentAmber else Color(0xFF261D42)
                            )
                        ) {
                            Icon(
                                imageVector = if (isVoiceEditRecording) Icons.Rounded.Stop else Icons.Rounded.Edit,
                                contentDescription = "Voice Edit",
                                tint = if (isVoiceEditRecording) Color.Black else AccentAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isVoiceEditRecording) "Apply" else "Voice Edit",
                                color = if (isVoiceEditRecording) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = onTranslateResult,
                            enabled = !isTranslating,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentViolet, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Translate, contentDescription = "EN", tint = Color(0xFFC4B5FD), modifier = Modifier.size(15.dp))
                            }
                        }

                        OutlinedButton(
                            onClick = onShare,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = TextPrimary, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick System Integration Cards (Keyboard + Floating Bubble)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, Color(0x22FFFFFF))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ভয়েস কীবোর্ড", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (keyboardEnabled) "চালু আছে ✓" else "সেটিংস থেকে অন করুন",
                        color = if (keyboardEnabled) AccentGreen else TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Enable",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2206B6D4))
                                .clickable { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                        Text(
                            text = "Switch",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardElevated)
                                .clickable {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                    imm.showInputMethodPicker()
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleBubble() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFloatingRunning) Color(0x268B5CF6) else CardDark
                ),
                border = BorderStroke(1.dp, if (isFloatingRunning) AccentViolet else Color(0x22FFFFFF))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BubbleChart, contentDescription = null, tint = AccentPink, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ফ্লোটিং বাবল", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isFloatingRunning) "চলছে • ট্যাপ করে বন্ধ" else "যেকোনো অ্যাপে টাইপ",
                        color = if (isFloatingRunning) Color(0xFFF9A8D4) else TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isAccessibilityOn) "Auto-Type ON ✓" else "Auto-Type Setup →",
                        color = if (isAccessibilityOn) AccentGreen else AccentViolet,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x228B5CF6))
                            .clickable {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                Toast.makeText(
                                    context,
                                    "Downloaded apps থেকে 'কথালিপি অটো-টাইপ' অন করুন",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun SnippetsTabContent(
    snippets: List<SnippetItem>,
    onAdd: (String, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var triggerInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "ভয়েস স্ন্যাপেট / শর্টকাট",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "যেকোনো ট্রিগার কথা বললেই (যেমন: 'আমার ঠিকানা বলো' বা 'বিকাশ নম্বর') সেভ করা টেক্সট অটো টাইপ হয়ে যাবে।",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = triggerInput,
                    onValueChange = { triggerInput = it },
                    placeholder = { Text("ট্রিগার (যেমন: আমার ঠিকানা বলো)", color = TextSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contentInput,
                    onValueChange = { contentInput = it },
                    placeholder = { Text("আউটপুট টেক্সট (যেমন: House 12, Dhanmondi, Dhaka)", color = TextSecondary, fontSize = 13.sp) },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (triggerInput.isNotBlank() && contentInput.isNotBlank()) {
                            onAdd(triggerInput, contentInput)
                            triggerInput = ""
                            contentInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("স্ন্যাপেট যোগ করুন", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (snippets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "উদাহরণ: 'বিকাশ নম্বর' ➔ '017XX-XXXXXX'\nউপর থেকে আপনার প্রথম স্ন্যাপেট যোগ করুন!",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(snippets, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "“${item.trigger}”",
                                    color = AccentViolet,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.content,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                            }
                            IconButton(onClick = { onDelete(item.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFFCA5A5), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTabContent(
    context: Context,
    items: List<DictationHistoryItem>,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.text.contains(query, ignoreCase = true) }
    }
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("হিস্ট্রি খুঁজুন…", color = TextSecondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentViolet,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (items.isEmpty()) "এখনো কোনো ডিক্টেশন হিস্ট্রি নেই" else "কিছু পাওয়া যায়নি",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${modeLabel(item.mode)} • ${dateFmt.format(Date(item.timestamp))}",
                                    color = AccentViolet,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, item.text)
                                            Toast.makeText(context, "কপি হয়েছে ✓", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { shareText(context, item.text) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.text,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    context: Context,
    initialGeminiKey: String,
    initialGroqKey: String,
    initialOpenaiKey: String,
    initialProvider: String,
    initialSmartPolish: Boolean,
    initialAutoSilence: Boolean,
    isAccessibilityOn: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var geminiKey by remember { mutableStateOf(initialGeminiKey) }
    var groqKey by remember { mutableStateOf(initialGroqKey) }
    var openaiKey by remember { mutableStateOf(initialOpenaiKey) }
    var provider by remember { mutableStateOf(initialProvider) }
    var smartPolish by remember { mutableStateOf(initialSmartPolish) }
    var autoSilence by remember { mutableStateOf(initialAutoSilence) }
    var showKeys by remember { mutableStateOf(false) }

    var testingProvider by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun testKey(prov: String, key: String) {
        if (key.trim().isEmpty()) {
            testResult = false to "আগে $prov API Key লিখুন"
            return
        }
        testingProvider = prov
        testResult = null
        scope.launch(Dispatchers.IO) {
            try {
                KothalipiApp.instance.engine.validateProviderKey(prov, key.trim())
                withContext(Dispatchers.Main) {
                    testingProvider = null
                    testResult = true to "$prov সংযোগ সফল ✓"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    testingProvider = null
                    testResult = false to sanitizeErrorMessage(e.message, key.trim())
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151224),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("কথালিপি সেটিংস", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { showKeys = !showKeys }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (showKeys) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Show/Hide",
                        tint = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary AI Provider selector
                Text("Primary AI Provider (অটো-ফলব্যাক সহ)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC4B5FD))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("auto" to "Auto", "gemini" to "Gemini", "groq" to "Groq", "openai" to "OpenAI").forEach { (id, label) ->
                        val active = provider == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) AccentPurple else CardDark)
                                .border(1.dp, if (active) AccentViolet else BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { provider = id }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (active) Color.White else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Gemini Key
                ProviderKeyField(
                    label = "Google Gemini API Key (ফ্রি)",
                    placeholder = "AIzaSy...",
                    value = geminiKey,
                    showKey = showKeys,
                    isTesting = testingProvider == "gemini",
                    linkText = "ফ্রি Key নিন ↗",
                    onLinkClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) },
                    onValueChange = { geminiKey = it; testResult = null },
                    onTest = { testKey("gemini", geminiKey) }
                )

                // Groq Key
                ProviderKeyField(
                    label = "Groq Whisper Key (ফ্রি ব্যাকআপ, সুপার ফাস্ট)",
                    placeholder = "gsk_...",
                    value = groqKey,
                    showKey = showKeys,
                    isTesting = testingProvider == "groq",
                    linkText = "Groq Key ↗",
                    onLinkClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) },
                    onValueChange = { groqKey = it; testResult = null },
                    onTest = { testKey("groq", groqKey) }
                )

                // OpenAI Key
                ProviderKeyField(
                    label = "OpenAI API Key (অপশনাল)",
                    placeholder = "sk-...",
                    value = openaiKey,
                    showKey = showKeys,
                    isTesting = testingProvider == "openai",
                    linkText = "OpenAI ↗",
                    onLinkClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/api-keys"))) },
                    onValueChange = { openaiKey = it; testResult = null },
                    onTest = { testKey("openai", openaiKey) }
                )

                testResult?.let { (ok, msg) ->
                    Text(
                        text = msg,
                        color = if (ok) AccentGreen else Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Smart Polish", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("দাঁড়ি, কমা ও ফিলার শব্দ ঠিক করবে", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = smartPolish,
                        onCheckedChange = { smartPolish = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentPurple)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Silence Stop (VAD)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("কথা শেষে ২ সেকেন্ড চুপ থাকলে অটো স্টপ", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoSilence,
                        onCheckedChange = { autoSilence = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentCyan)
                    )
                }

                Text(
                    text = if (isAccessibilityOn) "✓ Auto-Type Accessibility চালু আছে"
                    else "💡 ফ্লোটিং বাবল থেকে সরাসরি WhatsApp/Messenger-এ টাইপ করতে Auto-Type Accessibility অন করুন।",
                    color = if (isAccessibilityOn) AccentGreen else TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(geminiKey, groqKey, openaiKey, provider, smartPolish, autoSilence) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun ProviderKeyField(
    label: String,
    placeholder: String,
    value: String,
    showKey: Boolean,
    isTesting: Boolean,
    linkText: String,
    onLinkClick: () -> Unit,
    onValueChange: (String) -> Unit,
    onTest: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC4B5FD))
            Text(
                text = linkText,
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onLinkClick() }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentViolet,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onTest,
                enabled = !isTesting,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentViolet, strokeWidth = 2.dp)
                } else {
                    Text("Test", color = TextPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}
