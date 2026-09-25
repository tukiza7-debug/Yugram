package com.telegram.clone.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import androidx.compose.material.icons.filled.Check

/**
 * Privacy & Security settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val mgr = remember { AppSettingsManager.getInstance(context) }
    val privacyLastSeen by mgr.privacyLastSeen.collectAsState()
    val privacyPhone by mgr.privacyPhone.collectAsState()
    val privacyProfilePhoto by mgr.privacyProfilePhoto.collectAsState()
    val twoStep by mgr.privacyTwoStep.collectAsState()
    val passcode by mgr.privacyPasscode.collectAsState()
    val autoLock by mgr.privacyAutoLock.collectAsState()

    var dialogField by remember { mutableStateOf<String?>(null) }
    val privacyOptions = listOf("Everybody", "My Contacts", "Nobody")

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_privacy), color = Color.White, fontWeight = FontWeight.Medium) },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Spacer(modifier = Modifier.height(8.dp))

                SettingsSectionLabel("Privacy")
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        PrivacyPickerRow(Icons.Default.Schedule, 0xFF2AABEE.toInt(), "Last Seen", privacyLastSeen) { dialogField = "lastSeen" }
                        SettingsDivider()
                        PrivacyPickerRow(Icons.Default.Phone, 0xFF34C759.toInt(), "Phone Number", privacyPhone) { dialogField = "phone" }
                        SettingsDivider()
                        PrivacyPickerRow(Icons.Default.PhotoCamera, 0xFFFF9500.toInt(), "Profile Photo", privacyProfilePhoto) { dialogField = "photo" }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SettingsSectionLabel("Security")
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        ToggleRow(Icons.Default.Fingerprint, 0xFF5856D6.toInt(), "Two-Step Verification", "Extra layer of security", twoStep) { mgr.setPrivacyTwoStep(it) }
                        SettingsDivider()
                        ToggleRow(Icons.Default.Lock, 0xFF8E8E93.toInt(), "Passcode Lock", "Require passcode to open app", passcode) { mgr.setPrivacyPasscode(it) }
                        SettingsDivider()
                        ToggleRow(Icons.Default.LockClock, 0xFFAF52DE.toInt(), "Auto-Lock", "Lock automatically when idle", autoLock) { mgr.setPrivacyAutoLock(it) }
                    }
                }
            }
        }

        // Privacy option picker dialog
        if (dialogField != null) {
            val current = when (dialogField) {
                "lastSeen" -> privacyLastSeen
                "phone" -> privacyPhone
                else -> privacyProfilePhoto
            }
            AlertDialog(
                onDismissRequest = { dialogField = null },
                title = { Text("Who can see this?") },
                text = {
                    Column {
                        privacyOptions.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    when (dialogField) {
                                        "lastSeen" -> mgr.setPrivacyLastSeen(option)
                                        "phone" -> mgr.setPrivacyPhone(option)
                                        else -> mgr.setPrivacyProfilePhoto(option)
                                    }
                                    dialogField = null
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(20.dp).clip(CircleShape)
                                        .background(if (option == current) TelegramBlue else Color.Transparent.copy(alpha = 0f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (option == current) Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(option, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialogField = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun PrivacyPickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Int,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color(iconBg)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = TelegramBlue)
    }
}
