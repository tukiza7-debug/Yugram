package com.telegram.clone.ui.settings

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.theme.StatusOnline
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Devices screen showing active sessions (connected websites/devices) from TDLib.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(onBackClick: () -> Unit) {
    val repository = remember { TelegramRepository.getInstance() }
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<TdApi.Session>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var terminateSession by remember { mutableStateOf<TdApi.Session?>(null) }
    val currentUser by repository.currentUser.collectAsState(initial = null)

    // Load active sessions (devices) on screen entry — exclude the current device.
    remember {
        repository.getActiveSessions { result ->
            sessions = result.filter { !it.isCurrent }
            isLoading = false
        }
        true
    }

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_devices), color = Color.White, fontWeight = FontWeight.Medium) },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Spacer(modifier = Modifier.height(8.dp))

                // Current device section
                SettingsSectionLabel("Current Device")
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(TelegramBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Devices, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("This device", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(
                                currentUser?.let { "${it.firstName} ${it.lastName}".trim().ifBlank { "Telegram Clone" } } ?: "Telegram Clone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StatusOnline))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Active sessions
                SettingsSectionLabel("Active Sessions")
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TelegramBlue, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    }
                } else if (sessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No other active sessions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column {
                            sessions.forEachIndexed { index, session ->
                                if (index > 0) SettingsDivider()
                                SessionRow(session) { terminateSession = session }
                            }
                        }
                    }
                }
            }
        }

        // Terminate session confirmation dialog
        terminateSession?.let { session ->
            AlertDialog(
                onDismissRequest = { terminateSession = null },
                title = { Text("Terminate Session") },
                text = { Text("Log out from ${session.deviceModel.ifBlank { session.applicationName.ifBlank { "this device" } }}?") },
                confirmButton = {
                    TextButton(onClick = {
                        repository.terminateSession(session.id) { success ->
                            if (success) { sessions = sessions.filter { it.id != session.id } }
                        }
                        terminateSession = null
                    }) { Text("Terminate", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { terminateSession = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: TdApi.Session,
    onTerminate: () -> Unit
) {
    val location = listOf(session.country, session.region).filter { it.isNotBlank() }.joinToString(" ").ifBlank { session.ip }
    val platformLine = listOf(session.platform, session.systemVersion, session.applicationName).filter { it.isNotBlank() }.joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTerminate).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF5856D6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Devices, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(session.deviceModel.ifBlank { session.applicationName.ifBlank { "Unknown device" } }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (platformLine.isNotBlank()) {
                Text(platformLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Active ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(session.lastActiveDate * 1000L))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Tap to terminate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}
