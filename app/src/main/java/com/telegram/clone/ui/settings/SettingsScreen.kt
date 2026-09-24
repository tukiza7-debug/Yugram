package com.telegram.clone.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.components.TdFileImage
import com.telegram.clone.ui.theme.GlassBorder
import com.telegram.clone.ui.theme.GlassBorderSoft
import com.telegram.clone.ui.theme.GlassFill
import com.telegram.clone.ui.theme.GlassFillStrong
import com.telegram.clone.ui.theme.NovaBlue
import com.telegram.clone.ui.theme.NovaCyan
import com.telegram.clone.ui.theme.NovaGradientEnd
import com.telegram.clone.ui.theme.NovaGradientStart
import com.telegram.clone.ui.theme.NovaGreen
import com.telegram.clone.ui.theme.NovaOrange
import com.telegram.clone.ui.theme.NovaPink
import com.telegram.clone.ui.theme.NovaPurple
import com.telegram.clone.ui.theme.TelegramCloneTheme

/**
 * Settings screen (Nova glass design):
 * - Soft purple-to-cyan gradient header
 * - Large glass profile card with gradient-bordered avatar
 * - Settings items in rounded glass cards with soft colorful icons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onDevicesClick: () -> Unit = {},
    onDataStorageClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onPremiumClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { TelegramRepository.getInstance() }
    val settings = remember { AppSettingsManager.getInstance(context) }
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val premiumBadge by settings.premiumBadge.collectAsState()

    val fullName = buildString {
        append(currentUser?.firstName ?: "")
        currentUser?.lastName?.let { if (it.isNotBlank()) append(" $it") }
    }.ifBlank { "User" }

    val phoneNumber = currentUser?.phoneNumber ?: ""

    TelegramCloneTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Gradient header — no hamburger, just the wordmark
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(NovaGradientStart, NovaGradientEnd)
                        ),
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    )
            ) {
                Text(
                    text = "Yugram",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 16.dp)
                )
            }

            // Profile glass card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-28).dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassFillStrong)
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .clickable(onClick = onProfileClick),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .border(
                                width = 3.dp,
                                brush = Brush.linearGradient(listOf(NovaGradientStart, NovaGradientEnd)),
                                shape = CircleShape
                            )
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        val photo = currentUser?.profilePhoto?.small
                        if (photo != null) {
                            TdFileImage(
                                file = photo,
                                contentDescription = fullName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = fullName.firstOrNull()?.uppercase() ?: "U",
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fullName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (premiumBadge) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (phoneNumber.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+$phoneNumber",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Menu items — glass cards with colorful icons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-16).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassMenuItem(
                    icon = Icons.Default.Star,
                    iconTint = Color(0xFFFFD54F),
                    title = stringResource(R.string.premium_title),
                    subtitle = stringResource(R.string.settings_premium_subtitle),
                    onClick = onPremiumClick
                )
                GlassMenuItem(
                    icon = Icons.Default.Person,
                    iconTint = NovaPurple,
                    title = stringResource(R.string.settings_account),
                    subtitle = stringResource(R.string.settings_account_subtitle),
                    onClick = onProfileClick
                )
                GlassMenuItem(
                    icon = Icons.Default.Notifications,
                    iconTint = NovaOrange,
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_subtitle),
                    onClick = onNotificationsClick
                )
                GlassMenuItem(
                    icon = Icons.Default.Lock,
                    iconTint = NovaCyan,
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_subtitle),
                    onClick = onPrivacyClick
                )
                GlassMenuItem(
                    icon = Icons.Default.Devices,
                    iconTint = NovaGreen,
                    title = stringResource(R.string.settings_devices),
                    subtitle = "Active sessions",
                    onClick = onDevicesClick
                )
                GlassMenuItem(
                    icon = Icons.Default.Storage,
                    iconTint = NovaBlue,
                    title = stringResource(R.string.settings_data),
                    subtitle = stringResource(R.string.settings_data_subtitle),
                    onClick = onDataStorageClick
                )
                GlassMenuItem(
                    icon = Icons.Default.Palette,
                    iconTint = NovaPink,
                    title = stringResource(R.string.settings_appearance),
                    subtitle = stringResource(R.string.settings_appearance_subtitle),
                    onClick = onAppearanceClick
                )
                GlassMenuItem(
                    icon = Icons.Default.Language,
                    iconTint = Color(0xFFFF6B6B),
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_subtitle),
                    onClick = onLanguageClick
                )
                GlassMenuItem(
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFF9B8CFF),
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    onClick = onAboutClick
                )
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun GlassMenuItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorderSoft, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
