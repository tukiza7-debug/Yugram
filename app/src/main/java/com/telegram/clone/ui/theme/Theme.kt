package com.telegram.clone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Telegram-inspired Material 3 theme configuration.
 * Provides both light and dark color schemes matching Telegram's iconic palette.
 */

private val LightColorScheme = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = TelegramBlueLight,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.Black,
    secondary = TelegramBlueDark,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = TelegramBlueLight,
    onSecondaryContainer = androidx.compose.ui.graphics.Color.Black,
    tertiary = StatusOnline,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = StatusOnline.copy(alpha = 0.2f),
    onTertiaryContainer = androidx.compose.ui.graphics.Color.Black,
    error = StatusError,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = StatusError.copy(alpha = 0.2f),
    onErrorContainer = StatusError,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8EAED),
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight,
    outlineVariant = DividerLight.copy(alpha = 0.5f),
    scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFF2B2B2B),
    inverseOnSurface = androidx.compose.ui.graphics.Color.White,
    inversePrimary = TelegramBlueLight,
    surfaceTint = TelegramBlue
)

private val DarkColorScheme = darkColorScheme(
    primary = TelegramBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = TelegramBlueDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = TelegramBlueLight,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = TelegramBlueDark,
    onSecondaryContainer = androidx.compose.ui.graphics.Color.White,
    tertiary = StatusOnline,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = StatusOnline.copy(alpha = 0.2f),
    onTertiaryContainer = androidx.compose.ui.graphics.Color.White,
    error = StatusError,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = StatusError.copy(alpha = 0.2f),
    onErrorContainer = StatusError,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2B3A4A),
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
    outlineVariant = DividerDark.copy(alpha = 0.5f),
    scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFFE8EAED),
    inverseOnSurface = androidx.compose.ui.graphics.Color.Black,
    inversePrimary = TelegramBlueDark,
    surfaceTint = TelegramBlue
)

@Composable
fun TelegramCloneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) BackgroundDark.toArgb() else TelegramBlue.toArgb()
            window.navigationBarColor = if (darkTheme) BackgroundDark.toArgb() else BackgroundLight.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Helper composable to get chat bubble colors based on theme and direction.
 */
object ChatBubbleColors {
    @Composable
    fun outgoing(darkTheme: Boolean = isSystemInDarkTheme()): androidx.compose.ui.graphics.Color {
        return if (darkTheme) ChatBubbleOutgoingDark else ChatBubbleOutgoingLight
    }

    @Composable
    fun incoming(darkTheme: Boolean = isSystemInDarkTheme()): androidx.compose.ui.graphics.Color {
        return if (darkTheme) ChatBubbleIncomingDark else ChatBubbleIncomingLight
    }

    @Composable
    fun outgoingText(darkTheme: Boolean = isSystemInDarkTheme()): androidx.compose.ui.graphics.Color {
        return if (darkTheme) TextPrimaryDark else TextPrimaryLight
    }

    @Composable
    fun incomingText(darkTheme: Boolean = isSystemInDarkTheme()): androidx.compose.ui.graphics.Color {
        return if (darkTheme) TextPrimaryDark else TextPrimaryLight
    }

    @Composable
    fun timestampText(darkTheme: Boolean = isSystemInDarkTheme()): androidx.compose.ui.graphics.Color {
        return if (darkTheme) TextSecondaryDark else TextSecondaryLight
    }

    @Composable
    fun chatBackground(darkTheme: Boolean = isSystemInDarkTheme()): androidx.compose.ui.graphics.Color {
        return if (darkTheme) ChatBackgroundDark else ChatBackgroundLight
    }

    @Composable
    fun inputFieldBackground(darkTheme: Boolean = isSystemInDarkTheme()): androidx.compose.ui.graphics.Color {
        return if (darkTheme) InputFieldBackgroundDark else InputFieldBackgroundLight
    }
}
