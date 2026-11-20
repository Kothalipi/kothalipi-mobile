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

@Composable
fun WisprFlowStyleMainScreen(context: Context) {
    val app = KothalipiApp.instance
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("কথা বলতে ট্যাপ করুন") }
    var resultText by remember { mutableStateOf("") }
    var translateToEnglish by remember { mutableStateOf(app.translateToEnglish) }
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

    // Wispr Flow Pulse Glow Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.22f else 1f,
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
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

                    IconButton(onClick = { showSettingsDialog = true }) {
                        Text("⚙️", fontSize = 22.sp)
                    }
                }
            }

            // Wispr Flow Mode Pill Switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141220))
                    .border(1.dp, Color(0x33A855F7), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (!translateToEnglish) Brush.horizontalGradient(
                                listOf(Color(0xFF8A2BE2), Color(0xFF7C3AED))
                            ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable {
                            translateToEnglish = false
                            app.translateToEnglish = false
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("🇧🇩 বাংলা", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (translateToEnglish) Brush.horizontalGradient(
                                listOf(Color(0xFF8A2BE2), Color(0xFFEC4899))
                            ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable {
                            translateToEnglish = true
                            app.translateToEnglish = true
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("বাংলা ➔ 🇬🇧 English", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Center Microphone Hero Element with Glowing Ring
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
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
                            .size(100.dp)
                            .shadow(16.dp, CircleShape, spotColor = Color(0xFFA855F7))
                            .clip(CircleShape)
                            .background(
                                if (isRecording) Brush.linearGradient(
                                    listOf(Color(0xFFEC4899), Color(0xFF8A2BE2))
                                ) else Brush.linearGradient(
                                    listOf(Color(0xFF8A2BE2), Color(0xFF6D28D9))
                                )
                            )
                            .clickable {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@clickable
                                }

                                if (!isRecording) {
                                    audioBuffer.reset()
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
                                    statusText = if (translateToEnglish) "শুনছি… ইংরেজিতে অনুবাদ হবে" else "শুনছি… বাংলায় বলুন"

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
                                            val result = engine.processRecording(
                                                fullWav,
                                                "audio/wav",
                                                translateToEnglish,
                                                app.smartPolish
                                            )
                                            withContext(Dispatchers.Main) {
                                                resultText = result.processedText
                                                statusText = "সম্পন্ন!"
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                statusText = "এরর: ${e.message}"
                                                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRecording) "⏹️" else "🎙️",
                            fontSize = 38.sp
                        )
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
                        repeat(5) { index ->
                            val barHeight by infiniteTransition.animateFloat(
                                initialValue = 8f,
                                targetValue = 28f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(400 + (index * 80), easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "bar_$index"
                            )
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFFEC4899))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusText,
                    color = Color(0xFF9E9EA7),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
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
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Settings Sheet Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("সেটিংস") },
            text = {
                Column {
                    Text("Google Gemini API Key:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "আপনার ফ্রি জেমিনি কি দিন। এটি ডিভাইসেই সুরক্ষিত থাকবে।",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        app.geminiApiKey = apiKey
                        showSettingsDialog = false
                        Toast.makeText(context, "সংরক্ষণ করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
