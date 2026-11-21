package com.nira.companion

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : FragmentActivity() {

    private val coreBaseUrl = "http://127.0.0.1:8765"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceModel = Build.MODEL ?: "Unknown"
        val isMotorola = deviceModel.contains("motorola", ignoreCase = true) ||
            deviceModel.contains("moto", ignoreCase = true)

        setContent {
            val scope = rememberCoroutineScope()
            var activeMode by remember { mutableStateOf("normal") }
            var statusText by remember { mutableStateOf("NiRa ready • Moto check: $deviceModel") }
            var inputText by remember { mutableStateOf("") }
            var chatLog by remember {
                mutableStateOf(
                    listOf(
                        "NiRa: வாடா, நான் இங்க தான் இருக்கேன். உன் ஃபைல்ஸ் மற்றும் mode வச்சு பதில் பேசுவேன். `/work`, `/task`, `/emotional`, `/normal` அல்லது `/intimacy mode` சொல்லு."
                    )
                )
            }

            LaunchedEffect(Unit) {
                scope.launch {
                    val health = checkCoreHealth()
                    if (health != null) {
                        activeMode = health.optString("mode", "normal")
                        statusText = "Core Online ($activeMode) • $deviceModel"
                    } else {
                        statusText = "Termux Core offline (127.0.0.1:8765) • $deviceModel"
                    }
                }
            }

            fun activateIntimacyAfterAuth() {
                if (!isMotorola) {
                    chatLog = chatLog + "NiRa: Intimacy mode Motorola-ல மட்டும் தான்."
                    return
                }
                val executor = ContextCompat.getMainExecutor(this)
                val prompt = BiometricPrompt(
                    this,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            scope.launch {
                                val res = postJson(
                                    "$coreBaseUrl/mode/switch",
                                    JSONObject().apply {
                                        put("command", "/intimacy mode")
                                        put("device_name", deviceModel)
                                    }
                                )
                                if (res != null && res.optString("mode") == "intimacy") {
                                    activeMode = "intimacy"
                                    statusText = "INTIMACY MODE • ISOLATED VAULT"
                                    chatLog = chatLog + "NiRa: ${res.optString("message", "Aadhini intimacy mode activated.")}"
                                } else {
                                    chatLog = chatLog + "NiRa: Intimacy mode switch பண்ண முடியல."
                                }
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            chatLog = chatLog + "NiRa: Auth cancel/fail ($errString). Intimacy mode on ஆகல."
                        }
                    }
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("NiRa Intimacy Mode")
                    .setSubtitle("Owner verification required")
                    .setNegativeButtonText("Cancel")
                    .build()
                prompt.authenticate(info)
            }

            val bgColor = when (activeMode) {
                "intimacy" -> Color(0xFF1A0B24)
                "work" -> Color(0xFF091624)
                "emotional" -> Color(0xFF171121)
                else -> Color(0xFF0A0E17)
            }
            val accentColor = when (activeMode) {
                "intimacy" -> Color(0xFFC084FC)
                "work" -> Color(0xFF38BDF8)
                "emotional" -> Color(0xFFF472B6)
                else -> Color(0xFF22D3EE)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "NiRa • ${activeMode.uppercase()}",
                    color = accentColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatLog) { line ->
                        Surface(
                            color = Color(0xFF141C2B),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = line,
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask NiRa or /work /intimacy mode...", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                    Button(
                        onClick = {
                            val raw = inputText.trim()
                            if (raw.isEmpty()) return@Button
                            inputText = ""
                            chatLog = chatLog + "You: $raw"

                            if (raw.equals("/intimacy mode", ignoreCase = true)) {
                                activateIntimacyAfterAuth()
                            } else if (raw.startsWith("/")) {
                                scope.launch {
                                    val res = postJson(
                                        "$coreBaseUrl/mode/switch",
                                        JSONObject().apply {
                                            put("command", raw.lowercase())
                                            put("device_name", deviceModel)
                                        }
                                    )
                                    val nextMode = res?.optString("mode", "normal") ?: "normal"
                                    activeMode = nextMode
                                    statusText = "Core Online ($activeMode) • $deviceModel"
                                    chatLog = chatLog + "NiRa: ${res?.optString("message", "Mode updated.")}"
                                }
                            } else {
                                scope.launch {
                                    val chatRes = postJson(
                                        "$coreBaseUrl/chat",
                                        JSONObject().apply {
                                            put("message", raw)
                                        }
                                    )
                                    if (chatRes != null) {
                                        activeMode = chatRes.optString("mode", activeMode)
                                        val reply = chatRes.optString("reply", "பதில் கிடைக்கல.")
                                        chatLog = chatLog + "NiRa: $reply"
                                    } else {
                                        chatLog = chatLog + "NiRa: Termux-ல nira_engine.py ஓடுதான்னு பாருடா (127.0.0.1:8765)."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Send", color = Color(0xFF090D16), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            Thread {
                try {
                    postJsonSync(
                        "$coreBaseUrl/mode/switch",
                        JSONObject().apply {
                            put("command", "/normal")
                            put("device_name", Build.MODEL ?: "Motorola Edge 60 Pro")
                        }
                    )
                } catch (_: Exception) {
                }
            }.start()
        }
    }

    private suspend fun checkCoreHealth(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$coreBaseUrl/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            conn.disconnect()
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun postJson(urlStr: String, body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        postJsonSync(urlStr, body)
    }

    private fun postJsonSync(urlStr: String, body: JSONObject): JSONObject? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(body.toString())
                it.flush()
            }
            if (conn.responseCode !in 200..299) return null
            val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            conn.disconnect()
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }
}
