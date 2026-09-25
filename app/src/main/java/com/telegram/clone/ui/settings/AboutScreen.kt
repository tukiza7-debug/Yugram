package com.telegram.clone.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.BuildConfig
import com.telegram.clone.R
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import androidx.compose.material.icons.filled.Send

/**
 * About screen showing app version, TDLib version, and build info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { TelegramRepository.getInstance() }
    val currentUser by repository.currentUser.collectAsState(initial = null)

    val appVersion = remember {
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        "v${pkgInfo.versionName} (${pkgInfo.longVersionCode})"
    }
    val apiId = BuildConfig.TELEGRAM_API_ID

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_about), color = Color.White, fontWeight = FontWeight.Medium) },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // App icon
                Box(
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(TelegramBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Telegram Clone for Android",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Info section
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        AboutInfoRow("Version", appVersion)
                        AboutDivider()
                        AboutInfoRow("TDLib", "1.8.56")
                        AboutDivider()
                        AboutInfoRow("API ID", apiId.toString())
                        AboutDivider()
                        AboutInfoRow("Android Target", "API 36 (Android 16)")
                        AboutDivider()
                        AboutInfoRow("Kotlin", "1.9.x + Compose")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // License note
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Source", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This is an unofficial Telegram clone built with Jetpack Compose and TDLib. Telegram and the Telegram logo are trademarks of Telegram FZ-LLC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Account", style = MaterialTheme.typography.labelMedium, color = TelegramBlue)
                        Spacer(modifier = Modifier.height(4.dp))
                        currentUser?.let {
                            Text("Logged in as: ${it.firstName} ${it.lastName}".trim(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("User ID: ${it.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
