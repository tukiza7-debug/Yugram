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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.telegram.clone.core.settings.AppSettingsManager

/**
 * Nova — Material 3 theme for Yugram.
 * Dark-first glassmorphism scheme with the soft purple-to-cyan brand identity.
 */

private val LightColorScheme = lightColorScheme(
    primary = NovaPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF241A5E),
    secondary = NovaCyan,
    onSecondary = Color(0xFF00202A),
    secondaryContainer = Color(0xFFCFF3FF),
    onSecondaryContainer = Color(0xFF00323E),
    tertiary = NovaGreen,
    onTertiary = Color(0xFF00301F),
    tertiaryContainer = NovaGreen.copy(alpha = 0.2f),
    onTertiaryContainer = Color(0xFF00291A),
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusError.copy(alpha = 0.2f),
    onErrorContainer = Color(0xFF410009),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFE8E8F0),
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight,
    outlineVariant = DividerLight.copy(alpha = 0.5f),
    scrim = Color.Black.copy(alpha = 0.3f),
    inverseSurface = Color(0xFF2B2B33),
    inverseOnSurface = Color.White,
    inversePrimary = NovaPurple,
    surfaceTint = NovaPurple
)

private val DarkColorScheme = darkColorScheme(
    primary = NovaPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D2F86),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = NovaCyan,
    onSecondary = Color(0xFF00202A),
    secondaryContainer = Color(0xFF00485A),
    onSecondaryContainer = Color(0xFFCFF3FF),
    tertiary = NovaGreen,
    onTertiary = Color(0xFF00291A),
    tertiaryContainer = NovaGreen.copy(alpha = 0.18f),
    onTertiaryContainer = Color(0xFFA6F5D8),
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusError.copy(alpha = 0.2f),
    onErrorContainer = Color(0xFFFFD9DD),
    background = NovaBackground,
    onBackground = TextPrimaryDark,
    surface = NovaSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF23232E),
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
    outlineVariant = DividerDark.copy(alpha = 0.5f),
    scrim = Color.Black.copy(alpha = 0.6f),
    inverseSurface = Color(0xFFE8E8F0),
    inverseOnSurface = Color.Black,
    inversePrimary = NovaPurple,
    surfaceTint = NovaPurple
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
    val dynamicColorEnabled by settingsManager.dynamicColor.collectAsState()

    val resolvedDarkTheme = when (themeMode) {
        AppSettingsManager.ThemeMode.LIGHT -> false
        AppSettingsManager.ThemeMode.DARK -> true
        AppSettingsManager.ThemeMode.SYSTEM -> darkTheme
    }

    val colorScheme = when {
        (dynamicColor || dynamicColorEnabled) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (resolvedDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        resolvedDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (resolvedDarkTheme) NovaBackground.toArgb() else NovaPurple.toArgb()
            window.navigationBarColor = if (resolvedDarkTheme) NovaBackground.toArgb() else BackgroundLight.toArgb()
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
