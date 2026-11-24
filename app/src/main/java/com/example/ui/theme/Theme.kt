package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Auto"),
    LIGHT("light", "Warm Paper"),
    DARK("dark", "Obsidian Dark");

    companion object {
        fun fromKey(key: String?): ThemeMode {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
        }
    }
}

@Composable
fun GalleryHubTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentHex: String = AccentPreset.TERRACOTTA.hex,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val preset = AccentPreset.presetFromHex(accentHex)
    val primaryColor = preset.color
    val secondaryColor = preset.secondaryColor

    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color(0xFF12100E),
            primaryContainer = primaryColor.copy(alpha = 0.20f),
            onPrimaryContainer = DarkTextPrimary,
            secondary = secondaryColor,
            onSecondary = Color(0xFF12100E),
            secondaryContainer = DarkSurfaceVariant,
            onSecondaryContainer = DarkTextPrimary,
            tertiary = secondaryColor,
            background = DarkBg,
            onBackground = DarkTextPrimary,
            surface = DarkSurface,
            onSurface = DarkTextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkBorder,
            outlineVariant = DarkBorder.copy(alpha = 0.65f),
            error = QuotaDangerRed,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.14f),
            onPrimaryContainer = LightTextPrimary,
            secondary = secondaryColor,
            onSecondary = Color.White,
            secondaryContainer = LightSurfaceVariant,
            onSecondaryContainer = LightTextPrimary,
            tertiary = secondaryColor,
            background = LightBg,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightTextSecondary,
            outline = LightBorder,
            outlineVariant = LightBorder.copy(alpha = 0.7f),
            error = QuotaDangerRed,
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
