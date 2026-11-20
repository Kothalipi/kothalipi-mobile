package com.kothalipi.mobile

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kothalipi.mobile.overlay.KothalipiFloatingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WisprFlowStyleMainScreen(this)
        }
    }
}

fun sanitizeErrorMessage(raw: String?): String {
    if (raw == null) return "অপ্রত্যাশিত এরর ঘটেছে। আবার চেষ্টা করুন।"
    val lower = raw.lowercase()
    return when {
        lower.contains("503") || lower.contains("unavailable") || lower.contains("high demand") ->
            "Google Gemini সার্ভারে সাময়িক চাপ রয়েছে। কয়েক সেকেন্ড পর আবার চেষ্টা করুন।"
        lower.contains("429") || lower.contains("resource_exhausted") || lower.contains("quota") ->
            "দৈনিক ফ্রি কোটা শেষ হয়েছে। অনুগ্রহ করে কিছুক্ষণ পর আবার চেষ্টা করুন।"
        lower.contains("400") || lower.contains("403") || lower.contains("badapikey") || lower.contains("invalid") ->
            "ভুল বা অকার্যকর API Key! দয়া করে সেটিংস থেকে সঠিক Gemini Key দিন।"
        lower.contains("empty") || lower.contains("invalidaudio") ->
            "কোনো কথা শোনা যায়নি। অনুগ্রহ করে মাইক্রোফোনে স্পষ্ট করে বলুন।"
        lower.contains("timeout") || lower.contains("network") || lower.contains("connection") ->
            "ইন্টারনেট সংযোগ চেক করুন এবং পুনরায় চেষ্টা করুন।"
        else -> "প্রসেসিং ব্যর্থ হয়েছে। দয়া করে আবার চেষ্টা করুন।"
    }
}

@Composable
fun WisprFlowStyleMainScreen(context: Context) {
    val app = KothalipiApp.instance
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var dictationMode by remember { mutableStateOf(app.dictationMode) } // "bn", "en", "bn_to_en"
    var statusText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(app.geminiApiKey) }

    var audioRecord: AudioRecord? by remember { mutableStateOf(null) }
    val audioBuffer = remember { ByteArrayOutputStream() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "মাইক্রোফোন পারমিশন প্রয়োজন", Toast.LENGTH_SHORT).show()
        }
    }

    // Default status text based on mode
    val defaultStatus = when (dictationMode) {
        "en" -> "Tap mic to speak English"
        "bn_to_en" -> "মুখে বাংলায় বলুন, ইংরেজিতে টাইপ হবে"
        else -> "কথা বলতে মাইক ট্যাপ করুন"
    }

    val activeStatus = when (dictationMode) {
        "en" -> "Listening… Speak in English"
        "bn_to_en" -> "শুনছি… ইংরেজিতে অনুবাদ হবে"
        else -> "শুনছি… বাংলায় বলুন"
    }

    // Wispr Flow Pulse Glow Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF09080E) // Ultra-deep Obsidian
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header with Flow Brand & Floating Mode Trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "কথালিপি",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF8F8FC)
                    )
                    Text(
                        text = "AI Voice Dictation & Flow",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8CA0)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Floating Bubble Toggle Button
                    IconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                                Toast.makeText(context, "ফ্লোটিং বাবল অন করার অনুমতি দিন", Toast.LENGTH_LONG).show()
                            } else {
                                val intent = Intent(context, KothalipiFloatingService::class.java)
                                context.startService(intent)
                                Toast.makeText(context, "ফ্লোটিং বাবল চালু হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("🫧", fontSize = 22.sp)
                    }

                    IconButton(onClick = {
                        apiKey = app.geminiApiKey
                        showSettingsDialog = true
                    }) {
                        Text("⚙️", fontSize = 22.sp)
                    }
                }
            }

            // 3-Mode Wispr Flow Pill Switcher (Bangla, English, Bangla -> English)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141220))
                    .border(1.dp, Color(0x33A855F7), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode 1: Bangla
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (dictationMode == "bn") Brush.horizontalGradient(
                                listOf(Color(0xFF8A2BE2), Color(0xFF7C3AED))
                            ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable {
                            dictationMode = "bn"
                            app.dictationMode = "bn"
                            errorMessage = ""
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🇧🇩 বাংলা",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (dictationMode == "bn") FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Mode 2: English
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (dictationMode == "en") Brush.horizontalGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF06B6D4))
                            ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable {
                            dictationMode = "en"
                            app.dictationMode = "en"
                            errorMessage = ""
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🇬🇧 English",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (dictationMode == "en") FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Mode 3: Bangla -> English
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (dictationMode == "bn_to_en") Brush.horizontalGradient(
                                listOf(Color(0xFF8A2BE2), Color(0xFFEC4899))
                            ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable {
                            dictationMode = "bn_to_en"
                            app.dictationMode = "bn_to_en"
                            errorMessage = ""
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "বাংলা ➔ 🇬🇧 EN",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (dictationMode == "bn_to_en") FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Center Microphone Hero Element with Dynamic Glowing Ring
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Brush.radialGradient(
                                listOf(Color(0x66EC4899), Color(0x118A2BE2), Color.Transparent)
                            ) else Brush.radialGradient(
                                listOf(Color(0x338A2BE2), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .shadow(20.dp, CircleShape, spotColor = Color(0xFFA855F7))
                            .clip(CircleShape)
                            .background(
                                when {
                                    isProcessing -> Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF581C87)))
                                    isRecording -> Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8A2BE2)))
                                    dictationMode == "en" -> Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)))
                                    dictationMode == "bn_to_en" -> Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFFEC4899)))
                                    else -> Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFF6D28D9)))
                                }
                            )
                            .clickable(enabled = !isProcessing) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@clickable
                                }

                                if (!isRecording) {
                                    audioBuffer.reset()
                                    errorMessage = ""
                                    val sampleRate = 16000
                                    val bufferSize = AudioRecord.getMinBufferSize(
                                        sampleRate,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT
                                    )
                                    audioRecord = AudioRecord(
                                        MediaRecorder.AudioSource.MIC,
                                        sampleRate,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        bufferSize
                                    )
                                    audioRecord?.startRecording()
                                    isRecording = true
                                    statusText = activeStatus

                                    scope.launch(Dispatchers.IO) {
                                        val data = ByteArray(1024)
                                        while (isRecording && audioRecord != null) {
                                            val read = audioRecord?.read(data, 0, data.size) ?: 0
                                            if (read > 0) {
                                                audioBuffer.write(data, 0, read)
                                            }
                                        }
                                    }
                                } else {
                                    isRecording = false
                                    isProcessing = true
                                    audioRecord?.stop()
                                    audioRecord?.release()
                                    audioRecord = null
                                    statusText = "প্রসেসিং হচ্ছে…"

                                    scope.launch(Dispatchers.IO) {
                                        val pcmData = audioBuffer.toByteArray()
                                        val wavHeader = createWavHeader(pcmData.size, 16000, 1)
                                        val fullWav = wavHeader + pcmData

                                        try {
                                            val engine = uniffi.kothalipi_core.KothalipiMobileEngine(app.geminiApiKey)
                                            val result = engine.processRecordingWithMode(
                                                fullWav,
                                                "audio/wav",
                                                dictationMode,
                                                app.smartPolish
                                            )
                                            withContext(Dispatchers.Main) {
                                                resultText = result.processedText
                                                statusText = "সম্পন্ন!"
                                                isProcessing = false
                                                errorMessage = ""
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                val friendly = sanitizeErrorMessage(e.message)
                                                errorMessage = friendly
                                                statusText = "ব্যর্থ হয়েছে"
                                                isProcessing = false
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text(
                                text = if (isRecording) "⏹️" else "🎙️",
                                fontSize = 38.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Audio Waveform Animation Bars
                if (isRecording) {
                    Row(
                        modifier = Modifier.height(28.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(6) { index ->
                            val barHeight by infiniteTransition.animateFloat(
                                initialValue = 8f,
                                targetValue = 28f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(350 + (index * 70), easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "bar_$index"
                            )
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (dictationMode == "en") Color(0xFF06B6D4) else Color(0xFFEC4899)
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isRecording || isProcessing) statusText else defaultStatus,
                    color = Color(0xFF9E9EA7),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                // Humanized Error Alert
                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66EF4444))
                    ) {
                        Text(
                            text = "⚠️ $errorMessage",
                            color = Color(0xFFFCA5A5),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Wispr Flow Clean Result Box
            if (resultText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x33A855F7), RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141220))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = resultText,
                            fontSize = 18.sp,
                            color = Color(0xFFF8F8FC),
                            lineHeight = 28.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Kothalipi", resultText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "কপি করা হয়েছে ✓", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2))
                            ) {
                                Text("📋 Copy")
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, resultText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "শেয়ার করুন"))
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("↗️ Share", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = Color(0xFF161426),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E2E8),
            title = {
                Text("কথালিপি সেটিংস", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            },
            text = {
                Column {
                    Text(
                        "Google Gemini API Key:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC4B5FD)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text("AIzaSy... / AQ...", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8A2BE2),
                            unfocusedBorderColor = Color(0x44A855F7),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "💡 আপনার কি-টি সম্পূর্ণ সুরক্ষিত ও ডিভাইসেই থাকবে। কোনো কি না থাকলে Google AI Studio থেকে ফ্রি কি নিন:",
                        fontSize = 12.sp,
                        color = Color(0xFF9E9EA7),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "aistudio.google.com/apikey",
                        fontSize = 13.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                            context.startActivity(browserIntent)
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        app.geminiApiKey = apiKey.trim()
                        showSettingsDialog = false
                        Toast.makeText(context, "API Key সংরক্ষণ করা হয়েছে ✓", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel", color = Color(0xFF9E9EA7))
                }
            }
        )
    }
}
