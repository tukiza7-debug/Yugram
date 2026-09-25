package com.telegram.clone.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.components.novaGradientBrush
import com.telegram.clone.ui.theme.GlassBorder
import com.telegram.clone.ui.theme.GlassBorderSoft
import com.telegram.clone.ui.theme.GlassFill
import com.telegram.clone.ui.theme.NovaGradientEnd
import com.telegram.clone.ui.theme.NovaGradientStart
import com.telegram.clone.ui.theme.NovaGreen
import com.telegram.clone.ui.theme.TelegramCloneTheme
import org.drinkless.tdlib.TdApi

/**
 * Profile screen (Nova glass design) showing the current user's avatar,
 * name, phone, username and bio. The pencil icon opens EditProfileScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onEditProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { TelegramRepository.getInstance() }
    val settings = remember { AppSettingsManager.getInstance(context) }
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val premiumBadge by settings.premiumBadge.collectAsState()

    var bio by remember { mutableStateOf("") }
    LaunchedEffect(currentUser?.id) {
        currentUser?.let { bio = repository.getUserBio(it.id) }
    }

    val fullName = buildString {
        append(currentUser?.firstName ?: "")
        currentUser?.lastName?.let { if (it.isNotBlank()) append(" $it") }
    }.ifBlank { "User" }

    val phoneNumber = currentUser?.phoneNumber ?: ""
    val username = currentUser?.usernames?.activeUsernames?.firstOrNull() ?: ""
    val isOnline = repository.connectionState.value?.constructor == TdApi.ConnectionStateReady.CONSTRUCTOR

    TelegramCloneTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Gradient header with back + edit actions
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(brush = novaGradientBrush())
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_title),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    // The pencil — now fully functional.
                    IconButton(
                        onClick = onEditProfileClick,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.profile_edit),
                            tint = Color.White
                        )
                    }
                }

                // Avatar with gradient border (overlapping the header)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-40).dp)
                            .size(112.dp)
                            .border(
                                width = 3.dp,
                                brush = Brush.linearGradient(listOf(NovaGradientStart, NovaGradientEnd)),
                                shape = CircleShape
                            )
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        ProfilePhoto(currentUser = currentUser, fallback = fullName)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Name + premium star
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = fullName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    if (premiumBadge) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Online status
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isOnline) "online" else "last seen recently",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOnline) NovaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Info glass card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlassFill)
                        .border(1.dp, GlassBorderSoft, RoundedCornerShape(20.dp)),
                    color = Color.Transparent
                ) {
                    Column {
                        InfoRow(
                            icon = Icons.Default.Phone,
                            label = stringResource(R.string.profile_phone),
                            value = if (phoneNumber.isNotBlank()) "+$phoneNumber"
                            else stringResource(R.string.profile_no_username),
                            onClick = onEditProfileClick
                        )
                        HorizontalDivider()
                        InfoRow(
                            icon = Icons.Default.AlternateEmail,
                            label = stringResource(R.string.profile_username),
                            value = if (username.isNotBlank()) "@$username"
                            else stringResource(R.string.profile_no_username),
                            onClick = onEditProfileClick
                        )
                        HorizontalDivider()
                        InfoRow(
                            icon = Icons.Default.Info,
                            label = stringResource(R.string.profile_bio),
                            value = bio.ifBlank { stringResource(R.string.profile_no_bio) },
                            onClick = onEditProfileClick
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Edit profile CTA (gradient)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(brush = novaGradientBrush())
                        .clickable(onClick = onEditProfileClick),
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.profile_edit),
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProfilePhoto(currentUser: TdApi.User?, fallback: String) {
    val photo = currentUser?.profilePhoto?.small
    if (photo != null) {
        com.telegram.clone.ui.components.TdFileImage(
            file = photo,
            contentDescription = "Profile photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Text(
            text = fallback.firstOrNull()?.uppercase() ?: "U",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 64.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
