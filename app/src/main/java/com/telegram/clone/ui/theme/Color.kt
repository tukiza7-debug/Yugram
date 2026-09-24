package com.telegram.clone.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Nova — premium dark glassmorphism palette for Yugram.
 * Soft purple-to-cyan gradient identity on an elegant near-black canvas.
 */

// ============================================================
// Nova brand gradient
// ============================================================
val NovaPurple = Color(0xFF7B61FF)
val NovaCyan = Color(0xFF00D4FF)
val NovaGradientStart = NovaPurple
val NovaGradientEnd = NovaCyan

// Backwards-compatible aliases (existing screens keep compiling and
// automatically adopt the Nova purple identity).
val TelegramBlue = NovaPurple
val TelegramBlueDark = Color(0xFF6249E8)
val TelegramBlueLight = Color(0xFF9B8CFF)
val TelegramAccent = NovaPurple

// ============================================================
// Nova dark canvas
// ============================================================
val NovaBackground = Color(0xFF0F0F13)
val NovaSurface = Color(0xFF17171F)
val NovaSurfaceHigh = Color(0xFF1F1F29)

// Backwards-compatible background aliases
val BackgroundLight = Color(0xFFF7F7FA)
val BackgroundDark = NovaBackground
val SurfaceLight = Color(0xFFF2F2F6)
val SurfaceDark = NovaSurface

// ============================================================
// Glass system
// ============================================================
val GlassFill = Color.White.copy(alpha = 0.06f)
val GlassFillStrong = Color.White.copy(alpha = 0.10f)
val GlassBorder = Color.White.copy(alpha = 0.14f)
val GlassBorderSoft = Color.White.copy(alpha = 0.08f)

// ============================================================
// Text
// ============================================================
val TextPrimaryDark = Color(0xFFF5F5FA)
val TextSecondaryDark = Color(0xFF9A9AAE)
val TextHintDark = Color(0xFF6D6D80)
val TextPrimaryLight = Color(0xFF16161D)
val TextSecondaryLight = Color(0xFF6E6E80)
val TextHintLight = Color(0xFF9A9AA6)

// ============================================================
// Accents
// ============================================================
val NovaPinkRed = Color(0xFFFF5E7A)      // unread badge glow
val NovaGreen = Color(0xFF34E0A1)        // online / success
val NovaOrange = Color(0xFFFFB454)
val NovaPink = Color(0xFFFF6EC7)
val NovaBlue = Color(0xFF4DA3FF)

val StatusOnline = NovaGreen
val StatusOffline = Color(0xFF7A7A8C)
val StatusError = Color(0xFFFF4D67)
val StatusWarning = NovaOrange

// Backwards-compatible unread badge aliases
val UnreadBadge = NovaPinkRed
val UnreadBadgeMuted = Color(0xFF6E6E80)
val UnreadBadgeText = Color.White

// ============================================================
// Chat bubbles (Nova dark)
// ============================================================
val ChatBubbleOutgoingDark = Color(0xFF3D2F86)   // deep violet outgoing
val ChatBubbleOutgoingLight = Color(0xFFE4DFFF)
val ChatBubbleIncomingDark = Color(0xFF1F1F29)   // glass charcoal incoming
val ChatBubbleIncomingLight = Color(0xFFFFFFFF)

// Backwards-compatible chat background / input aliases
val ChatBackgroundLight = Color(0xFFE9E9F0)
val ChatBackgroundDark = Color(0xFF0B0B0F)
val InputFieldBackgroundLight = Color(0xFFF0F0F5)
val InputFieldBackgroundDark = Color(0xFF23232E)

// ============================================================
// Delivery status
// ============================================================
val DeliveryStatusRead = NovaCyan
val DeliveryStatusSent = Color(0xFF8E8EA3)

// ============================================================
// Divider / misc
// ============================================================
val DividerLight = Color(0xFFE4E4EC)
val DividerDark = Color(0xFF2A2A36)
val SelectionLight = Color(0xFFE7E1FF)
val SelectionDark = Color(0xFF3D2F86)

// Avatar placeholder palette — tuned to sit well on the dark canvas
val AvatarColors = listOf(
    NovaPurple,
    NovaCyan,
    NovaPink,
    NovaGreen,
    NovaOrange,
    NovaBlue,
    Color(0xFFB085FF),
    Color(0xFF5EEAD4)
)
