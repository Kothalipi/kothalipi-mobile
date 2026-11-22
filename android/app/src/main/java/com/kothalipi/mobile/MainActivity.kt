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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
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

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Dictate, 1 = History
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    var dictationMode by remember { mutableStateOf(app.dictationMode) }
    var writingStyle by remember { mutableStateOf(app.writingStyle) }
    var smartPolish by remember { mutableStateOf(app.smartPolish) }
    var savedApiKey by remember { mutableStateOf(app.geminiApiKey) }

    var resultText by remember { mutableStateOf("") }
    var copiedRecently by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Live audio level + rolling 12-bar waveform
    var currentAmplitude by remember { mutableFloatStateOf(0f) }
    val waveBars = remember { mutableStateListOf<Float>().apply { repeat(12) { add(0.15f) } } }

    val isFloatingRunning by KothalipiFloatingService.isRunning.collectAsState()
    val isAccessibilityOn by KothalipiAccessibilityService.isConnected.collectAsState()
    val historyItems by app.history.collectAsState()

    DisposableEffect(Unit) {
        onDispose { recorder.release() }
    }

    // Recording duration timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
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

    fun handleMicToggle() {
        if (isProcessing) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (savedApiKey.isBlank()) {
            errorMessage = "Gemini API Key সেট করা নেই! সেটিংস থেকে ফ্রি API Key দিন।"
            showSettingsDialog = true
            return
        }

        if (!isRecording) {
            errorMessage = ""
            copiedRecently = false
            val started = recorder.start(
                scope = scope,
                onAmplitude = { amp ->
                    currentAmplitude = amp
                    // Update rolling waveform on main thread safely
                    scope.launch(Dispatchers.Main) {
                        if (waveBars.isNotEmpty()) {
                            waveBars.removeAt(0)
                            waveBars.add(amp.coerceIn(0.12f, 1f))
                        }
                    }
                },
                onError = { msg ->
                    scope.launch(Dispatchers.Main) {
                        errorMessage = msg
                        isRecording = false
                    }
                }
            )
            if (started) {
                isRecording = true
            }
        } else {
            isRecording = false
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
                    val result = app.engine.processRecordingFull(
                        wavBytes,
                        "audio/wav",
                        dictationMode,
                        writingStyle,
                        smartPolish
                    )
                    app.addHistoryItem(result.processedText, dictationMode, writingStyle)
                    withContext(Dispatchers.Main) {
                        resultText = result.processedText
                        errorMessage = ""
                        isProcessing = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = sanitizeErrorMessage(e.message, savedApiKey)
                        isProcessing = false
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgObsidian
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle top radial glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (isRecording) Color(0x33EC4899) else Color(0x297C3AED),
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
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(listOf(AccentPurple, AccentPink))
                                ),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (savedApiKey.isNotBlank()) AccentGreen else AccentAmber)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (savedApiKey.isNotBlank()) "Gemini Ready" else "API Key দিন",
                                    fontSize = 11.sp,
                                    color = if (savedApiKey.isNotBlank()) AccentGreen else AccentAmber,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { showSettingsDialog = true }
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Floating Bubble quick pill button
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

                        // Settings icon button
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
                    if (selectedTab == 0) {
                        DictateTabContent(
                            context = context,
                            hasApiKey = savedApiKey.isNotBlank(),
                            dictationMode = dictationMode,
                            writingStyle = writingStyle,
                            smartPolish = smartPolish,
                            isRecording = isRecording,
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
                            onMicClick = { handleMicToggle() },
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
                                            errorMessage = sanitizeErrorMessage(e.message, savedApiKey)
                                            isTranslating = false
                                        }
                                    }
                                }
                            },
                            onToggleBubble = { toggleFloatingBubble() }
                        )
                    } else {
                        HistoryTabContent(
                            context = context,
                            items = historyItems,
                            onDelete = { app.deleteHistoryItem(it) },
                            onClearAll = { app.clearHistory() }
                        )
                    }
                }

                // Bottom Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
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
                        label = "হিস্ট্রি (${historyItems.size})",
                        icon = Icons.Rounded.History,
                        selected = selectedTab == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 1 }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            context = context,
            initialApiKey = savedApiKey,
            initialSmartPolish = smartPolish,
            isAccessibilityOn = isAccessibilityOn,
            onDismiss = { showSettingsDialog = false },
            onSave = { newKey, newPolish ->
                app.geminiApiKey = newKey.trim()
                app.smartPolish = newPolish
                savedApiKey = app.geminiApiKey
                smartPolish = newPolish
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
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) Color.White else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
    isRecording: Boolean,
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
    onMicClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onTranslateResult: () -> Unit,
    onToggleBubble: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.18f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val voiceBoost by animateFloatAsState(
        targetValue = if (isRecording) currentAmplitude * 0.25f else 0f,
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
        // API Key Missing Banner
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
                            text = "Gemini API Key সেট করুন",
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

        // Writing Style + Smart Polish Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Smart Polish Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (smartPolish) Color(0x3310B981) else CardDark)
                    .border(
                        1.dp,
                        if (smartPolish) AccentGreen else BorderSubtle,
                        RoundedCornerShape(14.dp)
                    )
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
                        text = "AI Polish",
                        color = if (smartPolish) Color(0xFFA7F3D0) else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            listOf("normal", "formal", "casual", "bullets").forEach { style ->
                val active = writingStyle == style
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) Color(0x338B5CF6) else CardDark)
                        .border(
                            1.dp,
                            if (active) AccentViolet else Color(0x22FFFFFF),
                            RoundedCornerShape(14.dp)
                        )
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

        Spacer(modifier = Modifier.height(22.dp))

        // Center Hero Microphone Orb
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer reactive halo
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(basePulse + voiceBoost)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (isRecording) listOf(Color(0x66EC4899), Color(0x1A8B5CF6), Color.Transparent)
                            else listOf(Color(0x338B5CF6), Color.Transparent)
                        )
                    )
            )

            // Main Mic Button
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .shadow(24.dp, CircleShape, spotColor = if (isRecording) AccentPink else AccentViolet)
                    .clip(CircleShape)
                    .background(
                        when {
                            isProcessing -> Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF581C87)))
                            isRecording -> Brush.linearGradient(listOf(AccentPink, AccentPurple))
                            dictationMode == "en" -> Brush.linearGradient(listOf(Color(0xFF2563EB), AccentCyan))
                            dictationMode == "bn_to_en" -> Brush.linearGradient(listOf(AccentPurple, AccentPink))
                            dictationMode == "auto" -> Brush.linearGradient(listOf(Color(0xFF059669), AccentCyan))
                            else -> Brush.linearGradient(listOf(AccentViolet, AccentPurple))
                        }
                    )
                    .clickable(enabled = !isProcessing) { onMicClick() },
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
                        imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (isRecording) "Stop" else "Record",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }

        // Live Voice-Reactive Waveform + Recording Timer
        AnimatedVisibility(visible = isRecording, enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.height(34.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    waveBars.forEach { level ->
                        val barH by animateDpAsState(
                            targetValue = (8 + (level * 26)).dp,
                            animationSpec = tween(80),
                            label = "barH"
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(barH)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.verticalGradient(listOf(AccentPink, AccentViolet))
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                val mm = recordingSeconds / 60
                val ss = recordingSeconds % 60
                Text(
                    text = String.format(Locale.US, "%02d:%02d", mm, ss),
                    color = AccentPink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                isProcessing -> "Gemini AI প্রসেস করছে…"
                isRecording -> when (dictationMode) {
                    "en" -> "Listening… Speak in English"
                    "bn_to_en" -> "শুনছি… বাংলায় বলুন, ইংরেজিতে হবে"
                    "auto" -> "শুনছি… বাংলা বা ইংরেজিতে বলুন"
                    else -> "শুনছি… বাংলায় বলুন"
                }
                else -> when (dictationMode) {
                    "en" -> "Tap mic to dictate in English"
                    "bn_to_en" -> "মুখে বাংলায় বলুন, ইংরেজিতে টাইপ হবে"
                    "auto" -> "বাংলা বা ইংরেজি — অটো ডিটেক্ট হবে"
                    else -> "কথা বলতে মাইক ট্যাপ করুন"
                }
            },
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        // Error Alert Card
        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
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

        // Result Card
        if (resultText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
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
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    SelectionContainer {
                        Text(
                            text = resultText,
                            fontSize = 17.sp,
                            color = TextPrimary,
                            lineHeight = 26.sp,
                            modifier = Modifier.heightIn(max = 220.dp)
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
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copiedRecently) AccentGreen else AccentPurple
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (copiedRecently) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedRecently) "Copied" else "Copy", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onTranslateResult,
                            enabled = !isTranslating,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    color = AccentViolet,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = "Translate",
                                    tint = Color(0xFFC4B5FD),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EN", color = TextPrimary, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onShare,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share",
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Quick System Integration Cards (Keyboard + Floating Bubble)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Voice Keyboard Setup Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, Color(0x22FFFFFF))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Keyboard,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ভয়েস কীবোর্ড",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                                .clickable {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                }
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

            // Floating Bubble Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleBubble() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFloatingRunning) Color(0x268B5CF6) else CardDark
                ),
                border = BorderStroke(
                    1.dp,
                    if (isFloatingRunning) AccentViolet else Color(0x22FFFFFF)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.BubbleChart,
                            contentDescription = null,
                            tint = AccentPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ফ্লোটিং বাবল",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
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

        Spacer(modifier = Modifier.height(16.dp))
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
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary)
                },
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { shareText(context, item.text) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Share,
                                            contentDescription = "Share",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDelete(item.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFFCA5A5),
                                            modifier = Modifier.size(16.dp)
                                        )
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
    initialApiKey: String,
    initialSmartPolish: Boolean,
    isAccessibilityOn: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var smartPolish by remember { mutableStateOf(initialSmartPolish) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151224),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text("কথালিপি সেটিংস", fontWeight = FontWeight.Bold, fontSize = 19.sp)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Google Gemini API Key",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFC4B5FD)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testResult = null
                    },
                    placeholder = { Text("AIzaSy...", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle Key",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Test Connection Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (apiKey.trim().isEmpty()) {
                                testResult = false to "আগে API Key লিখুন"
                                return@OutlinedButton
                            }
                            testing = true
                            testResult = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val tempEngine = uniffi.kothalipi_core.KothalipiMobileEngine(apiKey.trim())
                                    tempEngine.validateApiKey()
                                    withContext(Dispatchers.Main) {
                                        testing = false
                                        testResult = true to "সংযোগ সফল ✓"
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        testing = false
                                        testResult = false to sanitizeErrorMessage(e.message, apiKey.trim())
                                    }
                                }
                            }
                        },
                        enabled = !testing,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = AccentViolet,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test API Key", color = TextPrimary, fontSize = 12.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                            )
                        }
                    ) {
                        Text(
                            "ফ্রি Key নিন",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                testResult?.let { (ok, msg) ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        color = if (ok) AccentGreen else Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // AI Smart Polish Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Smart Polish", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "দাঁড়ি, কমা ঠিক করবে ও অপ্রয়োজনীয় শব্দ বাদ দেবে",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = smartPolish,
                        onCheckedChange = { smartPolish = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentPurple
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isAccessibilityOn) "✓ Auto-Type Accessibility চালু আছে"
                    else "💡 ফ্লোটিং বাবল থেকে সরাসরি WhatsApp/Messenger-এ টাইপ করতে Auto-Type Accessibility অন করুন।",
                    color = if (isAccessibilityOn) AccentGreen else TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKey, smartPolish) },
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
