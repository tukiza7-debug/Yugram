package com.telegram.clone.ui.components

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telegram.clone.data.repository.TelegramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fullscreen media viewer state — a lightweight singleton so deeply
 * nested message content (photo / video bubbles) can open the viewer
 * without threading callbacks through the whole message tree.
 *
 * Usage: `MediaViewerState.request = MediaRequest(...)` from a bubble,
 * observed once at the chat room root.
 */
object MediaViewerState {
    var request by mutableStateOf<MediaRequest?>(null)
}

data class MediaRequest(
    /** File rendered fullscreen (photo file, or video thumbnail). */
    val displayFile: TdApi.File?,
    /** File saved to the gallery by the download action (same as display for photos). */
    val saveFile: TdApi.File?,
    val isVideo: Boolean,
    val fileName: String
)

private fun extensionFor(fileName: String, isVideo: Boolean): String {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext.isNotEmpty() && ext.length <= 5) return ext
    return if (isVideo) "mp4" else "jpg"
}

private fun mimeFor(ext: String, isVideo: Boolean): String {
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    return if (isVideo) "video/mp4" else "image/jpeg"
}

private fun uniqueDisplayName(fileName: String, ext: String): String {
    val base = fileName.removeSuffix(".${fileName.substringAfterLast('.', "")}")
        .ifBlank { "Yugram_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}" }
    return "${base}.$ext"
}

/**
 * Copies an already-downloaded TDLib local file into the device gallery:
 *
 * - API 29+: inserted into MediaStore (Pictures/Yugram, Movies/Yugram or
 *   Downloads/Yugram for documents) — no permission required.
 * - API 26–28: written to the public directory after WRITE_EXTERNAL_STORAGE
 *   was granted by the caller, then scanned into the media index.
 *
 * @return user-facing result message (success or failure reason).
 */
private suspend fun saveToGallery(
    context: Context,
    sourcePath: String,
    isVideo: Boolean,
    originalName: String,
    asDocument: Boolean
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

/**
 * Fullscreen media viewer with pinch-zoom, double-tap zoom toggle and a
 * gallery download action. Shows live TDLib download progress when the
 * media is not yet available locally.
 */
@Composable
fun FullscreenMediaViewer(
    request: MediaRequest,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TelegramRepository.getInstance() }
    val saveFileId = request.saveFile?.id ?: 0

    // Resolve the file to save: download on demand and follow file updates.
    var targetFile by remember(saveFileId) {
        mutableStateOf(request.saveFile?.takeIf { !it.local.path.isNullOrEmpty() })
    }
    var downloading by remember(saveFileId) {
        mutableStateOf(
            request.saveFile != null && targetFile == null
        )
    }
    var progressPercent by remember(saveFileId) { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(saveFileId) {
        val file = request.saveFile ?: return@LaunchedEffect
        if (targetFile == null) repository.downloadFile(file.id, 32)
        repository.fileFlow.collect { updated ->
            if (updated.id == saveFileId) {
                if (updated.expectedSize > 0) {
                    progressPercent = ((updated.local.downloadedPrefixSize * 100L) / updated.expectedSize).toInt()
                        .coerceIn(0, 100)
                }
                val path = updated.local.path
                if (!path.isNullOrEmpty() && File(path).exists()) {
                    targetFile = updated
                    downloading = false
                }
            }
        }
    }

    // Pinch-zoom + pan state (resets when a new request opens).
    var scale by remember(request) { mutableStateOf(1f) }
    var offsetX by remember(request) { mutableStateOf(0f) }
    var offsetY by remember(request) { mutableStateOf(0f) }

    // Runtime storage permission for API 26–28. A granted result flips
    // [pendingSave] and the LaunchedEffect below performs the actual save,
    // keeping the flow race-free.
    var pendingSave by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingSave = true
    }

    LaunchedEffect(pendingSave) {
        if (!pendingSave) return@LaunchedEffect
        pendingSave = false
        val f = targetFile ?: return@LaunchedEffect
        saving = true
        val message = saveToGallery(
            context = context,
            sourcePath = f.local.path,
            isVideo = request.isVideo,
            originalName = request.fileName,
            asDocument = false
        )
        saving = false
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun requestSave() {
        if (targetFile == null) return
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            pendingSave = true
        } else {
            permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE0A0A0C))
                .pointerInput(request) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(request) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = {
                            if (scale == 1f) onDismiss()
                        }
                    )
                }
        ) {
            // ---- Media ----
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                TdFileImage(
                    file = request.displayFile,
                    contentDescription = request.fileName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit,
                    priority = 32,
                    placeholder = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (downloading && progressPercent > 0) {
                                Text(
                                    text = "Memuat turun $progressPercent%",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.9f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                )

                // Video affordance over the thumbnail preview.
                if (request.isVideo && targetFile == null) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // ---- Top action bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (request.isVideo) "Video" else "Foto",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = request.fileName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    saving -> CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    downloading -> CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.8f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    else -> IconButton(
                        onClick = { requestSave() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Muat turun",
                            tint = Color.White
                        )
                    }
                }
            }

            // Hint text at the bottom.
            Text(
                text = "Cubit untuk zum · Ketik dua untuk zum · Ketik untuk tutup",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }
    }
}
