package com.telegram.clone.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.telegram.clone.R
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme

/**
 * Notifications settings screen with toggles for message, group, and call
 * notifications, plus preview/sound/vibration options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val mgr = remember { AppSettingsManager.getInstance(context) }
    val notifMessages by mgr.notifMessages.collectAsState()
    val notifGroups by mgr.notifGroups.collectAsState()
    val notifCalls by mgr.notifCalls.collectAsState()
    val notifPreview by mgr.notifPreview.collectAsState()
    val notifSound by mgr.notifSound.collectAsState()
    val notifVibrate by mgr.notifVibrate.collectAsState()

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_notifications), color = Color.White, fontWeight = FontWeight.Medium) },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Spacer(modifier = Modifier.height(8.dp))

                SettingsSectionLabel("Notify for")
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        ToggleRow(Icons.Default.Notifications, 0xFFFF9500.toInt(), "Messages", "Notify on new messages", notifMessages) { mgr.setNotifMessages(it) }
                        SettingsDivider()
                        ToggleRow(Icons.Default.Group, 0xFF34C759.toInt(), "Groups", "Notify on group messages", notifGroups) { mgr.setNotifGroups(it) }
                        SettingsDivider()
                        ToggleRow(Icons.Default.Phone, 0xFF2AABEE.toInt(), "Calls", "Incoming call notifications", notifCalls) { mgr.setNotifCalls(it) }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SettingsSectionLabel("In-app")
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        ToggleRow(Icons.Default.Preview, 0xFF5856D6.toInt(), "Show Preview", "Display message text in notifications", notifPreview) { mgr.setNotifPreview(it) }
                        SettingsDivider()
                        ToggleRow(Icons.Default.GraphicEq, 0xFFAF52DE.toInt(), "Sound", "Play sound for notifications", notifSound) { mgr.setNotifSound(it) }
                        SettingsDivider()
                        ToggleRow(Icons.Default.Vibration, 0xFFFF3B30.toInt(), "Vibrate", "Vibrate on notification", notifVibrate) { mgr.setNotifVibrate(it) }
                    }
                }
            }
        }
    }
}
