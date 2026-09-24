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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.telegram.clone.core.settings.AppSettingsManager

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
    val context = LocalContext.current
    val settingsManager = remember { AppSettingsManager.getInstance(context) }
    val themeMode by settingsManager.themeMode.collectAsState()

    val resolvedDarkTheme = when (themeMode) {
        AppSettingsManager.ThemeMode.LIGHT -> false
        AppSettingsManager.ThemeMode.DARK -> true
        AppSettingsManager.ThemeMode.SYSTEM -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (resolvedDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        resolvedDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (resolvedDarkTheme) BackgroundDark.toArgb() else TelegramBlue.toArgb()
            window.navigationBarColor = if (resolvedDarkTheme) BackgroundDark.toArgb() else BackgroundLight.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !resolvedDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !resolvedDarkTheme
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
 * Reads the user's explicit theme preference (Light/Dark/System) from
 * [AppSettingsManager] so the appearance setting actually takes effect.
 */
object ChatBubbleColors {
    @Composable
    private fun isDark(): Boolean {
        val context = androidx.compose.ui.platform.LocalContext.current
        val settings = remember { AppSettingsManager.getInstance(context) }
        val mode by settings.themeMode.collectAsState()
        return when (mode) {
            AppSettingsManager.ThemeMode.LIGHT -> false
            AppSettingsManager.ThemeMode.DARK -> true
            AppSettingsManager.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    }

    @Composable
    fun outgoing(): androidx.compose.ui.graphics.Color {
        return if (isDark()) ChatBubbleOutgoingDark else ChatBubbleOutgoingLight
    }

    @Composable
    fun incoming(): androidx.compose.ui.graphics.Color {
        return if (isDark()) ChatBubbleIncomingDark else ChatBubbleIncomingLight
    }

    @Composable
    fun outgoingText(): androidx.compose.ui.graphics.Color {
        return if (isDark()) TextPrimaryDark else TextPrimaryLight
    }

    @Composable
    fun incomingText(): androidx.compose.ui.graphics.Color {
        return if (isDark()) TextPrimaryDark else TextPrimaryLight
    }

    @Composable
    fun timestampText(): androidx.compose.ui.graphics.Color {
        return if (isDark()) TextSecondaryDark else TextSecondaryLight
    }

    @Composable
    fun chatBackground(): androidx.compose.ui.graphics.Color {
        return if (isDark()) ChatBackgroundDark else ChatBackgroundLight
    }

    @Composable
    fun inputFieldBackground(): androidx.compose.ui.graphics.Color {
        return if (isDark()) InputFieldBackgroundDark else InputFieldBackgroundLight
    }
}
