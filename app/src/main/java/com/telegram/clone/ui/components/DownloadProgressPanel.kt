package com.telegram.clone.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.core.download.MediaDownloadManager
import com.telegram.clone.core.download.MediaDownloadManager.Status

/**
 * Floating bulk-download progress panel (Nekogram-style download manager UI).
 *
 * Renders nothing when the queue is empty. Shows a compact summary bar with
 * overall progress; tap to expand the per-file list where items can be
 * individually cancelled and finished entries cleared.
 */
@Composable
fun DownloadProgressPanel(
    manager: MediaDownloadManager,
    modifier: Modifier = Modifier
) {
    val items by manager.items.collectAsState()
    val entries = items.values.toList().sortedBy { it.status.ordinal }
    if (entries.isEmpty()) return

    val active = entries.filter {
        it.status == Status.QUEUED || it.status == Status.DOWNLOADING || it.status == Status.SAVING
    }
    val done = entries.count { it.status == Status.DONE }
    val failed = entries.count { it.status == Status.FAILED || it.status == Status.CANCELLED }
    val overall = if (active.isEmpty()) {
        1f
    } else {
        active.map { it.progress }.average().toFloat() / 100f
    }

    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .padding(top = 64.dp)
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(16.dp))
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEE1C1C1E),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7B5FE8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (active.isNotEmpty()) {
                            "Memuat turun ${active.size} fail"
                        } else if (done > 0) {
                            "$done fail disimpan ke galeri"
                        } else {
                            "Muat turun selesai"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (failed > 0) {
                        Text(
                            text = "$failed gagal/dibatalkan",
                            color = Color(0xFFFFB4AB),
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            if (active.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { overall.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF3FB6F5),
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height((entries.size.coerceAtMost(4) * 46).dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries.size) { index ->
                        val item = entries[index]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.status == Status.DOWNLOADING) {
                                        "${manager.statusLabel(item.status)} ${item.progress}%"
                                    } else {
                                        item.resultMessage ?: manager.statusLabel(item.status)
                                    },
                                    color = when (item.status) {
                                        Status.DONE -> Color(0xFF9BE8A8)
                                        Status.FAILED, Status.CANCELLED -> Color(0xFFFFB4AB)
                                        else -> Color.White.copy(alpha = 0.6f)
                                    },
                                    fontSize = 11.sp
                                )
                            }
                            if (item.status in listOf(Status.QUEUED, Status.DOWNLOADING)) {
                                IconButton(
                                    onClick = { manager.cancel(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Batal",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Kosongkan selesai",
                    color = Color(0xFF9BB4FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { manager.clearFinished() }
                        .padding(top = 4.dp, bottom = 2.dp)
                )
            }
        }
    }
}
