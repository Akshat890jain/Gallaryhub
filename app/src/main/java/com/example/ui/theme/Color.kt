package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Warm Editorial Paper Light Mode Tokens (Crafted Studio Aesthetic — No Cold Blue)
val LightBg = Color(0xFFFAF7F2)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1ECE3)
val LightTextPrimary = Color(0xFF181615)
val LightTextSecondary = Color(0xFF6E685F)
val LightBorder = Color(0xFFE5DFD5)

// Warm Obsidian & Carbon Darkroom Tokens (Replaces Cold Navy #0B0F17 / #131B2E)
val DarkBg = Color(0xFF0E0E10)
val DarkSurface = Color(0xFF17171B)
val DarkSurfaceVariant = Color(0xFF222127)
val DarkTextPrimary = Color(0xFFF5F2EB)
val DarkTextSecondary = Color(0xFFA39E95)
val DarkBorder = Color(0xFF2D2B33)

// 6 Curated Studio Accent Palettes (Defaulting to Warm Terracotta Studio instead of Google Blue)
enum class AccentPreset(
    val label: String,
    val subtitle: String,
    val hex: String,
    val color: Color,
    val secondaryColor: Color
) {
    TERRACOTTA(
        label = "Terracotta Studio",
        subtitle = "Warm Leica coral & carbon darkroom",
        hex = "#E06D53",
        color = Color(0xFFE06D53),
        secondaryColor = Color(0xFFD49B4B)
    ),
    CHAMPAGNE_GOLD(
        label = "Champagne Noir",
        subtitle = "Editorial amber gold & warm espresso",
        hex = "#D49B4B",
        color = Color(0xFFD49B4B),
        secondaryColor = Color(0xFFE06D53)
    ),
    BOTANICAL_SAGE(
        label = "Botanical Sage",
        subtitle = "Organic studio moss & warm stone",
        hex = "#4E9F76",
        color = Color(0xFF4E9F76),
        secondaryColor = Color(0xFF8AB89E)
    ),
    ROSE_ATELIER(
        label = "Rose Atelier",
        subtitle = "Dusty rose clay & gallery charcoal",
        hex = "#D96B8A",
        color = Color(0xFFD96B8A),
        secondaryColor = Color(0xFFE899AD)
    ),
    VIOLET_INK(
        label = "Violet Ink",
        subtitle = "Muted iris & archival slate",
        hex = "#8B6CEF",
        color = Color(0xFF8B6CEF),
        secondaryColor = Color(0xFFB39DDB)
    ),
    CLASSIC_COBALT(
        label = "Studio Cobalt",
        subtitle = "Deep archival cobalt blue",
        hex = "#3B82F6",
        color = Color(0xFF3B82F6),
        secondaryColor = Color(0xFF60A5FA)
    );

    companion object {
        fun fromHex(hex: String?): Color {
            return entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) }?.color
                ?: TERRACOTTA.color
        }

        fun presetFromHex(hex: String?): AccentPreset {
            return entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) }
                ?: TERRACOTTA
        }
    }
}

// Folder Color Palette Options
val FolderColorPresets = listOf(
    "#E06D53" to Color(0xFFE06D53),
    "#D49B4B" to Color(0xFFD49B4B),
    "#4E9F76" to Color(0xFF4E9F76),
    "#D96B8A" to Color(0xFFD96B8A),
    "#8B6CEF" to Color(0xFF8B6CEF),
    "#06B6D4" to Color(0xFF06B6D4),
    "#F97316" to Color(0xFFF97316),
    "#3B82F6" to Color(0xFF3B82F6)
)

fun parseHexColor(hex: String?, fallback: Color = Color(0xFFE06D53)): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val clean = hex.trim().removePrefix("#")
        val value = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or value)
            8 -> Color(value)
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

// Storage Quota Status Colors
val QuotaHealthyGreen = Color(0xFF4E9F76)
val QuotaWarningAmber = Color(0xFFD49B4B)
val QuotaDangerRed = Color(0xFFE05252)
