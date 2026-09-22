package com.system.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SetupUsernameActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kalau sudah pernah setup, langsung ke MainActivity
        val prefs = getSharedPreferences("exoid_user_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("username", null)
        if (!existing.isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00FF66),
                    background = Color(0xFF0A0A0A),
                    surface = Color(0xFF1A0000)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0A)
                ) {
                    SetupScreen(
                        onSubmit = { username -> handleSubmit(username) }
                    )
                }
            }
        }
    }

    private fun handleSubmit(username: String) {
        Thread {
            val ok = checkUsernameExists(username)
            runOnUiThread {
                if (ok) {
                    val prefs = getSharedPreferences("exoid_user_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("username", username).apply()
                    Toast.makeText(this, "Username tersimpan!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        "Username '$username' tidak terdaftar di panel!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun checkUsernameExists(username: String): Boolean {
        return try {
            val cfg = App.config
            val base = cfg.panelUrl
                .replace("ws://", "http://")
                .replace("wss://", "https://")
                .replace("/ws", "")

            val url = URL("$base/api/check-user?username=$username")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun SetupScreen(onSubmit: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0A), Color(0xFF1A0000))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "EXOID ENGINE",
                color = Color(0xFFF5D76E),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "SETUP AWAL",
                color = Color(0xFF00FF66),
                fontSize = 12.sp,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(40.dp))

            Text(
                "Masukkan username yang terdaftar di panel.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                label = { Text("Username") },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF66),
                    unfocusedBorderColor = Color(0xFF8A0000),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00FF66)
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (username.isEmpty()) return@Button
                    loading = true
                    onSubmit(username)
                },
                enabled = !loading && username.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB30000)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (loading) "MEMERIKSA..." else "SIMPAN",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
