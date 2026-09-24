package com.telegram.clone.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * Appearance settings screen — lets the user switch between Light, Dark, and
 * System theme modes. The choice is persisted in [AppSettingsManager] and
 * immediately applied across the whole app (see [TelegramCloneTheme]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { AppSettingsManager.getInstance(context) }
    val themeMode by settingsManager.themeMode.collectAsState()

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_appearance), color = Color.White, fontWeight = FontWeight.Medium) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Spacer(modifier = Modifier.height(8.dp))

                // Theme section
                SettingsSectionTitle("Theme")
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        ThemeOptionRow(
                            icon = Icons.Default.LightMode,
                            iconBg = 0xFFFF9500.toInt(),
                            title = "Light",
                            subtitle = "Always use light theme",
                            selected = themeMode == AppSettingsManager.ThemeMode.LIGHT,
                            onSelect = { settingsManager.setThemeMode(AppSettingsManager.ThemeMode.LIGHT) }
                        )
                        ThemeDivider()
                        ThemeOptionRow(
                            icon = Icons.Default.DarkMode,
                            iconBg = 0xFF5856D6.toInt(),
                            title = "Dark",
                            subtitle = "Always use dark theme",
                            selected = themeMode == AppSettingsManager.ThemeMode.DARK,
                            onSelect = { settingsManager.setThemeMode(AppSettingsManager.ThemeMode.DARK) }
                        )
                        ThemeDivider()
                        ThemeOptionRow(
                            icon = Icons.Default.Settings,
                            iconBg = 0xFF8E8E93.toInt(),
                            title = "System",
                            subtitle = "Follow system setting",
                            selected = themeMode == AppSettingsManager.ThemeMode.SYSTEM,
                            onSelect = { settingsManager.setThemeMode(AppSettingsManager.ThemeMode.SYSTEM) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Changes apply instantly across the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = TelegramBlue,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemeOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(iconBg)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = TelegramBlue))
    }
}

@Composable
private fun ThemeDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 64.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
