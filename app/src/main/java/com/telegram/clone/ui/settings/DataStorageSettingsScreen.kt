package com.telegram.clone.ui.settings

import android.os.StatFs
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.telegram.clone.R
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import java.io.File

/**
 * Data & Storage settings screen showing device storage usage and auto-download toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataStorageSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val mgr = remember { AppSettingsManager.getInstance(context) }
    val autoPhotos by mgr.dataAutoDownloadPhotos.collectAsState()
    val autoVoice by mgr.dataAutoDownloadVoice.collectAsState()
    val saveGallery by mgr.dataSaveToGallery.collectAsState()

    // Calculate device storage
    val storageInfo = remember {
        val stat = StatFs(context.filesDir.absolutePath)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val usedBytes = totalBytes - freeBytes
        Triple(totalBytes, usedBytes, freeBytes)
    }
    val (total, used, free) = storageInfo
    val progress = if (total > 0) (used.toFloat() / total.toFloat()) else 0f

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_data), color = Color.White, fontWeight = FontWeight.Medium) },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Spacer(modifier = Modifier.height(8.dp))

                // Storage usage
                SettingsSectionLabel("Storage Usage")
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Used: ${formatBytes(used)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Free: ${formatBytes(free)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = TelegramBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total: ${formatBytes(total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cache size for app
                SettingsSectionLabel("App Cache")
                var cacheSize by remember { mutableStateOf(formatBytes(dirSize(context.cacheDir))) }
                var clearing by remember { mutableStateOf(false) }
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cache size: $cacheSize", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                clearing = true
                                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                cacheSize = formatBytes(dirSize(context.cacheDir))
                                clearing = false
                            },
                            enabled = !clearing
                        ) { Text(if (clearing) "Clearing…" else "Clear Cache") }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-download
                SettingsSectionLabel("Auto-Download Media")
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        ToggleRow(Icons.Default.Image, 0xFF34C759.toInt(), "Photos", "Auto-download photos on mobile data", autoPhotos) { mgr.setDataAutoDownloadPhotos(it) }
                        SettingsDivider()
                        ToggleRow(Icons.Default.Mic, 0xFFFF9500.toInt(), "Voice Messages", "Auto-download voice messages", autoVoice) { mgr.setDataAutoDownloadVoice(it) }
                        SettingsDivider()
                        ToggleRow(Icons.Default.Save, 0xFF5856D6.toInt(), "Save to Gallery", "Save received media to gallery", saveGallery) { mgr.setDataSaveToGallery(it) }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun dirSize(dir: File): Long {
    var size = 0L
    if (dir.isDirectory) {
        dir.listFiles()?.forEach { size += if (it.isDirectory) dirSize(it) else it.length() }
    } else {
        size = dir.length()
    }
    return size
}
