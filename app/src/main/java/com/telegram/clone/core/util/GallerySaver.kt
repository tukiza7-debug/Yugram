package com.telegram.clone.core.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared utility that copies an already-downloaded TDLib local file into the
 * device gallery / downloads:
 *
 * - API 29+: inserted into MediaStore (Pictures/Yugram, Movies/Yugram or
 *   Download/Yugram for documents) — no permission required.
 * - API 26-28: written to the public directory after WRITE_EXTERNAL_STORAGE
 *   was granted by the caller, then scanned into the media index.
 *
 * Used by the fullscreen media viewer AND the bulk media download manager
 * so every save path behaves identically across the app.
 */
object GallerySaver {

    fun extensionFor(fileName: String, isVideo: Boolean): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotEmpty() && ext.length <= 5) return ext
        return if (isVideo) "mp4" else "jpg"
    }

    fun mimeFor(ext: String, isVideo: Boolean): String {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return if (isVideo) "video/mp4" else "image/jpeg"
    }

    private fun uniqueDisplayName(fileName: String, ext: String): String {
        val base = fileName.removeSuffix(".${fileName.substringAfterLast('.', "")}")
            .ifBlank { "Yugram_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}" }
        return "${base}.$ext"
    }

    /**
     * Copies [sourcePath] into the public gallery.
     *
     * @return user-facing result message (success or failure reason).
     */
    suspend fun saveToGallery(
        context: Context,
        sourcePath: String,
        isVideo: Boolean,
        originalName: String,
        asDocument: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        if (!source.exists()) return@withContext "Fail tidak dijumpai"

        val ext = extensionFor(originalName, isVideo)
        val mime = mimeFor(ext, isVideo)
        val displayName = uniqueDisplayName(originalName.ifBlank { "Yugram" }, ext)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection: Uri
                val relativePath: String
                when {
                    asDocument -> {
                        collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        relativePath = "Download/Yugram"
                    }
                    isVideo -> {
                        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        relativePath = "Movies/Yugram"
                    }
                    else -> {
                        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        relativePath = "Pictures/Yugram"
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(collection, values)
                    ?: return@withContext "Gagal menyimpan (MediaStore)"
                val ok = resolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(source).use { it.copyTo(out) }
                } != null
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                if (ok) "Disimpan ke galeri" else "Gagal menyimpan"
            } else {
                val dirType = when {
                    asDocument -> Environment.DIRECTORY_DOWNLOADS
                    isVideo -> Environment.DIRECTORY_MOVIES
                    else -> Environment.DIRECTORY_PICTURES
                }
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(dirType),
                    "Yugram"
                )
                if (!dir.exists() && !dir.mkdirs()) return@withContext "Gagal mencipta folder"
                var dest = File(dir, displayName)
                var counter = 1
                while (dest.exists()) {
                    dest = File(dir, displayName.replaceAfterLast('.', "($counter).$ext"))
                    counter++
                }
                FileInputStream(source).use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
                "Disimpan ke galeri"
            }
        } catch (e: SecurityException) {
            "Tiada kebenaran storan"
        } catch (e: Exception) {
            "Gagal menyimpan: ${e.message ?: "ralat tidak diketahui"}"
        }
    }
}
