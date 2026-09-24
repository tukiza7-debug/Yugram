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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.unit.dp
import com.telegram.clone.R
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme

/**
 * Language settings screen with a selectable list of languages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val mgr = remember { AppSettingsManager.getInstance(context) }
    val currentLang by mgr.language.collectAsState()

    val languages = listOf(
        "English" to "🇬🇧",
        "Indonesian" to "🇮🇩",
        "Spanish" to "🇪🇸",
        "French" to "🇫🇷",
        "German" to "🇩🇪",
        "Portuguese" to "🇵🇹",
        "Russian" to "🇷🇺",
        "Arabic" to "🇸🇦",
        "Chinese" to "🇨🇳",
        "Japanese" to "🇯🇵",
        "Korean" to "🇰🇷",
        "Malay" to "🇲🇾"
    )

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_language), color = Color.White, fontWeight = FontWeight.Medium) },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Spacer(modifier = Modifier.height(8.dp))

                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        languages.forEachIndexed { index, (name, flag) ->
                            if (index > 0) SettingsDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { mgr.setLanguage(name) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(flag, style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (name == currentLang) {
                                    Box(
                                        modifier = Modifier.size(24.dp).clip(CircleShape).background(TelegramBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
