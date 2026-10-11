package com.mari.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mari.assistant.ui.screens.MainScreen
import com.mari.assistant.ui.theme.MariAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MariAssistantTheme {
                MainScreen()
            }
        }
    }
}
package com.mari.assistant.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.mari.assistant.data.GeminiApiClient
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    var statusText by remember { mutableStateOf("Listo para escuchar...") }
    var userInput by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("Hola Keilev, dime qué necesitas.") }
    var isLoading by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "jarvisPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scalePulse"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(if (isLoading) scale else 1f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = aiResponse,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                label = { Text("Escribe o simula comando a Mari") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (userInput.isNotBlank()) {
                        isLoading = true
                        statusText = "Pensando..."
                        coroutineScope.launch {
                            aiResponse = GeminiApiClient.askGemini(userInput)
                            isLoading = false
                            statusText = "Listo"
                            userInput = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar a Mari")
            }
        }
    }
}
package com.mari.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GeminiApiClient {
    private val client = OkHttpClient()
    private const val API_KEY = "TU_GEMINI_API_KEY_AQUI" 

    suspend fun askGemini(prompt: String): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$API_KEY"
        
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", "Eres Mari, una asistente virtual cercana, directa y con estilo Jarvis. Responde de forma breve y concisa: $prompt")
                ))
            ))
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Error de conexión con Gemini."
                val responseData = response.body?.string() ?: return@withContext "Respuesta vacía."
                
                val jsonResponse = JSONObject(responseData)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No entendí bien.")
                    }
                }
                return@withContext "No supe qué responder a eso."
            }
        } catch (e: Exception) {
            return@withContext "Fallo técnico: ${e.localizedMessage}"
        }
    }
}
package com.mari.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VoiceAssistantService : Service() {

    companion object {
        const val CHANNEL_ID = "MariServiceChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mari Activa")
            .setContentText("Escuchando comandos en segundo plano...")
            .setSmallIcon(android.R.drawable.ic_menu_mic)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Canal del Asistente Mari",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }
}
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Mari Assistant"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="Mari Assistant"
            android:theme="@style/Theme.Material3.DayNight.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.VoiceAssistantService"
            android:exported="false"
            android:foregroundServiceType="microphone" />

    </application>

</manifest>
