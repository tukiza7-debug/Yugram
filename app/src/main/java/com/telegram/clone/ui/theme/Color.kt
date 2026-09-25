package com.telegram.clone.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Yugram design system — light canvas with the purple-to-blue brand
 * gradient from the redesigned interface spec (#7B5FE8 -> #3FB6F5).
 */

// ============================================================
// Brand gradient (interface spec)
// ============================================================
val BrandPurple = Color(0xFF7B5FE8)
val BrandBlue = Color(0xFF3FB6F5)

// Nova aliases kept so existing call sites automatically adopt the new
// gradient identity.
val NovaPurple = BrandPurple
val NovaCyan = BrandBlue
val NovaGradientStart = BrandPurple
val NovaGradientEnd = BrandBlue

// Backwards-compatible aliases (existing screens keep compiling and
// automatically adopt the Nova purple identity).
val TelegramBlue = NovaPurple
val TelegramBlueDark = Color(0xFF6249E8)
val TelegramBlueLight = Color(0xFF9B8CFF)
val TelegramAccent = NovaPurple

// ============================================================
// Nova dark canvas (kept for the optional dark theme + chat room)
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
// Light canvas (interface spec tokens)
// ============================================================
val Ink = Color(0xFF1A1A1E)            // primary text on light canvas
val MutedGray = Color(0xFF8E8E93)      // secondary text (spec --muted)
val Hairline = Color(0xFFEEEEF0)       // card borders (spec --hair)
val CardBg = Color(0xFFFAFAFC)         // card background (spec --card)
val PageWhite = Color(0xFFFFFFFF)      // page background
val NavDark = Color(0xF71C1C1E)        // floating dark bottom nav
val BadgeRed = Color(0xFFFF3B30)       // unread / alert badge
val StatusGreenSpec = Color(0xFF2FB65A) // online status dot

// ============================================================
// Colored icon chips (menu items / quick actions)
// ============================================================
val ChipPurple = BrandPurple
val ChipPurpleBg = Color(0xFFE4DCFB)
val ChipCyan = Color(0xFF2AA9C4)
val ChipCyanBg = Color(0xFFD4F5F9)
val ChipGreen = Color(0xFF2FA35C)
val ChipGreenBg = Color(0xFFD4F5DE)
val ChipAmber = Color(0xFFF5A623)
val ChipAmberBg = Color(0xFFFFF3C4)
val ChipOrange = Color(0xFFE8862B)
val ChipOrangeBg = Color(0xFFFFE4C4)
val ChipBlue = Color(0xFF3B7FE0)
val ChipBlueBg = Color(0xFFD4E4FB)
val ChipPink = Color(0xFFFF6EC7)
val ChipPinkBg = Color(0xFFFFE1F3)
val ChipRed = Color(0xFFFF6B6B)
val ChipRedBg = Color(0xFFFFE1E1)

// ============================================================
// Glass system (now tuned for the light canvas)
// ============================================================
val GlassFill = Color.Black.copy(alpha = 0.035f)
val GlassFillStrong = Color.White.copy(alpha = 0.96f)
val GlassBorder = Hairline
val GlassBorderSoft = Hairline

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
