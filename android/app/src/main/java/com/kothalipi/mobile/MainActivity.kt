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
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KothalipiMainScreen(this)
        }
    }
}

@Composable
fun KothalipiMainScreen(context: Context) {
    val app = KothalipiApp.instance
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("কথা বলতে চাপুন") }
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

    // Pulse animation for recording button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F14)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "কথালিপি",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF5F5F7)
                )

                IconButton(onClick = { showSettingsDialog = true }) {
                    Text("⚙️", fontSize = 24.sp)
                }
            }

            // Mode Toggle (Bangla vs Bangla-to-English)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A24))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (!translateToEnglish) Color(0xFF8A2BE2) else Color.Transparent)
                        .clickable {
                            translateToEnglish = false
                            app.translateToEnglish = false
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("বাংলা", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (translateToEnglish) Color(0xFF8A2BE2) else Color.Transparent)
                        .clickable {
                            translateToEnglish = true
                            app.translateToEnglish = true
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("বাংলা ➔ English", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            // Record Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFE254FF) else Color(0xFF8A2BE2))
                        .clickable {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@clickable
                            }

                            if (!isRecording) {
                                // Start recording
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
                                statusText = "শুনছি… কথা বলুন"

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
                                // Stop recording and process
                                isRecording = false
                                audioRecord?.stop()
                                audioRecord?.release()
                                audioRecord = null
                                statusText = "প্রসেস হচ্ছে…"

                                scope.launch(Dispatchers.IO) {
                                    val pcmData = audioBuffer.toByteArray()
                                    // Encode PCM to WAV
                                    val wavHeader = createWavHeader(pcmData.size, 16000, 1)
                                    val fullWav = wavHeader + pcmData

                                    // Call Rust Core Engine
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
                        fontSize = 42.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusText,
                    color = Color(0xFF9E9EA7),
                    fontSize = 16.sp
                )
            }

            // Output Card
            if (resultText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = resultText,
                            fontSize = 18.sp,
                            color = Color(0xFFF5F5F7),
                            lineHeight = 26.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Kothalipi", resultText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2))
                            ) {
                                Text("📋 Copy")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, resultText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "শেয়ার করুন"))
                                }
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
            title = { Text("সেটিংস") },
            text = {
                Column {
                    Text("Gemini API Key:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true
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

fun createWavHeader(dataLen: Int, sampleRate: Int, channels: Int): ByteArray {
    val totalLen = 36 + dataLen
    val byteRate = sampleRate * channels * 2
    val header = ByteArray(44)
    header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
    header[4] = (totalLen and 0xff).toByte()
    header[5] = ((totalLen shr 8) and 0xff).toByte()
    header[6] = ((totalLen shr 16) and 0xff).toByte()
    header[7] = ((totalLen shr 24) and 0xff).toByte()
    header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
    header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
    header[20] = 1; header[21] = 0 // PCM
    header[22] = channels.toByte(); header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = ((sampleRate shr 8) and 0xff).toByte()
    header[26] = ((sampleRate shr 16) and 0xff).toByte()
    header[27] = ((sampleRate shr 24) and 0xff).toByte()
    header[28] = (byteRate and 0xff).toByte()
    header[29] = ((byteRate shr 8) and 0xff).toByte()
    header[30] = ((byteRate shr 16) and 0xff).toByte()
    header[31] = ((byteRate shr 24) and 0xff).toByte()
    header[32] = (channels * 2).toByte(); header[33] = 0
    header[34] = 16; header[35] = 0 // bits per sample
    header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
    header[40] = (dataLen and 0xff).toByte()
    header[41] = ((dataLen shr 8) and 0xff).toByte()
    header[42] = ((dataLen shr 16) and 0xff).toByte()
    header[43] = ((dataLen shr 24) and 0xff).toByte()
    return header
}
