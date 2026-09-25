package com.telegram.clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.ui.theme.BadgeRed
import com.telegram.clone.ui.theme.GlassBorder
import com.telegram.clone.ui.theme.GlassBorderSoft
import com.telegram.clone.ui.theme.GlassFill
import com.telegram.clone.ui.theme.GlassFillStrong
import com.telegram.clone.ui.theme.Ink
import com.telegram.clone.ui.theme.MutedGray
import com.telegram.clone.ui.theme.NovaCyan
import com.telegram.clone.ui.theme.NovaGradientEnd
import com.telegram.clone.ui.theme.NovaGradientStart
import com.telegram.clone.ui.theme.NavDark
import com.telegram.clone.ui.theme.NovaPurple
import com.telegram.clone.ui.theme.BrandPurple

/**
 * Nova glassmorphism component kit:
 * - [Modifier.glassSurface]  frosted-glass fill + soft border
 * - [NovaGradientHeader]     purple-to-cyan app header with glowing wordmark
 * - [GlassSearchBar]         floating pill search field
 * - [NovaBottomBar]          floating glass bottom navigation
 * - [NovaFab]                gradient glowing FAB
 * - [GlassCard]              rounded glass content card
 */

/** Frosted-glass surface modifier: translucent fill + hairline border. */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = GlassFill,
    border: Color = GlassBorder
): Modifier = this
    .clip(shape)
    .background(fill, shape)
    .border(1.dp, border, shape)

/** Solid brand gradient brush used across headers, FAB and accents. */
@Composable
fun novaGradientBrush(horizontal: Boolean = true): Brush {
    return if (horizontal) {
        Brush.horizontalGradient(listOf(NovaGradientStart, NovaGradientEnd))
    } else {
        Brush.verticalGradient(listOf(NovaGradientStart, NovaGradientEnd))
    }
}

/**
 * Nova top header: purple-to-blue gradient, glowing "Yugram" wordmark
 * on the left, trailing actions on the right. No hamburger menu.
 */
@Composable
fun NovaGradientHeader(
    title: String = "Yugram",
    actions: @Composable () -> Unit = {},
    contentPadding: Dp = 16.dp,
    bottomRounded: Boolean = true
) {
    val shape = if (bottomRounded) {
        RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(brush = novaGradientBrush(), shape = shape)
    ) {
        // Soft glow blob (blurred cyan) behind the wordmark
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(-30.dp, -40.dp)
                .blur(40.dp)
                .background(Color.White.copy(alpha = 0.25f), CircleShape)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = contentPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.White.copy(alpha = 0.65f),
                    blurRadius = 18f
                ))
            )
            Box(modifier = Modifier.weight(1f))
            actions()
        }
    }
}

/**
 * Floating glass search pill with a soft white border (dark variant).
 */
@Composable
fun GlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp), ambientColor = NovaPurple.copy(alpha = 0.15f))
            .glassSurface(shape = RoundedCornerShape(28.dp), fill = GlassFillStrong),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
            Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(22.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Floating WHITE search pill per the interface spec: 48dp tall, fully
 * rounded, soft drop shadow, gray search glyph, dark text. Overlaps the
 * bottom edge of the gradient header.
 */
@Composable
fun LightSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Cari kontak, chat, atau pesan",
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x2E23145A),
                spotColor = Color(0x2E23145A)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF5FFFFFF)),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MutedGray,
                modifier = Modifier.size(18.dp)
            )
            Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MutedGray,
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(22.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MutedGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Nova top-level tabs for the floating bottom bar. */
enum class NovaTab(val label: String) {
    CHATS("Chats"),
    CALLS("Calls"),
    SETTINGS("Settings")
}

/**
 * Floating DARK bottom navigation per the interface spec: near-opaque
 * dark pill (radius 32), soft black shadow, the active tab is a solid
 * brand-purple capsule, unread badge in spec red.
 */
@Composable
fun NovaBottomBar(
    currentTab: NovaTab?,
    unreadCount: Int,
    onTabSelected: (NovaTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 40.dp, vertical = 10.dp)
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(32.dp))
                .background(NavDark),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovaTabItem(
                    tab = NovaTab.CHATS,
                    icon = Icons.Default.Chat,
                    selected = currentTab == NovaTab.CHATS,
                    unreadCount = unreadCount,
                    onClick = { onTabSelected(NovaTab.CHATS) }
                )
                NovaTabItem(
                    tab = NovaTab.CALLS,
                    icon = Icons.Default.Call,
                    selected = currentTab == NovaTab.CALLS,
                    unreadCount = 0,
                    onClick = { onTabSelected(NovaTab.CALLS) }
                )
                NovaTabItem(
                    tab = NovaTab.SETTINGS,
                    icon = Icons.Default.Settings,
                    selected = currentTab == NovaTab.SETTINGS,
                    unreadCount = 0,
                    onClick = { onTabSelected(NovaTab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun NovaTabItem(
    tab: NovaTab,
    icon: ImageVector,
    selected: Boolean,
    unreadCount: Int,
    onClick: () -> Unit
) {
    val tint = if (selected) Color.White else Color(0xFFB8B8BE)
    val bg = if (selected) BrandPurple else Color.Transparent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = BadgeRed,
                        contentColor = Color.White
                    ) {
                        Text(text = if (unreadCount > 99) "99+" else unreadCount.toString())
                    }
                }
            }
        ) {
            IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Large glowing gradient FAB.
 */
@Composable
fun NovaFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(60.dp)
            .shadow(
                elevation = 18.dp,
                shape = CircleShape,
                ambientColor = NovaPurple.copy(alpha = 0.6f),
                spotColor = NovaCyan.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .background(brush = novaGradientBrush())
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Rounded glass content card used by Settings.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = shape, fill = GlassFill, border = GlassBorderSoft),
        color = Color.Transparent
    ) {
        Box { content() }
    }
}
