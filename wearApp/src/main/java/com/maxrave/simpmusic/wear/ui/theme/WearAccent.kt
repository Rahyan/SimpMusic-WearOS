package com.maxrave.simpmusic.wear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

const val KEY_WEAR_ACCENT_PRESET = "wear_accent_preset"
const val ACCENT_PRESET_DYNAMIC = "dynamic"
const val ACCENT_PRESET_OCEAN = "ocean"
const val ACCENT_PRESET_FOREST = "forest"
const val ACCENT_PRESET_SUNSET = "sunset"
const val ACCENT_PRESET_ORCHID = "orchid"
const val ACCENT_PRESET_MONO = "mono"
const val ACCENT_PRESET_DEFAULT = ACCENT_PRESET_OCEAN
const val KEY_WEAR_ACCENT_MIGRATED = "wear_accent_migrated"
const val WEAR_ACCENT_MIGRATION_DONE = "done"

private val ACCENT_PRESET_ORDER =
    listOf(
        ACCENT_PRESET_OCEAN,
        ACCENT_PRESET_FOREST,
        ACCENT_PRESET_SUNSET,
        ACCENT_PRESET_MONO,
    )

fun nextAccentPreset(current: String): String {
    val currentIndex = ACCENT_PRESET_ORDER.indexOf(current).takeIf { it >= 0 } ?: 0
    return ACCENT_PRESET_ORDER[(currentIndex + 1) % ACCENT_PRESET_ORDER.size]
}

fun accentPresetLabel(preset: String): String =
    when (preset) {
        ACCENT_PRESET_OCEAN -> "Ocean blue"
        ACCENT_PRESET_FOREST -> "Forest green"
        ACCENT_PRESET_SUNSET -> "Sunset orange"
        ACCENT_PRESET_ORCHID -> "Orchid purple"
        ACCENT_PRESET_MONO -> "Monochrome"
        else -> "Wallpaper dynamic"
    }

fun ColorScheme.withAccentPreset(preset: String): ColorScheme =
    when (preset) {
        ACCENT_PRESET_OCEAN ->
            copy(
                primary = Color(0xFF5EA2FF),
                primaryDim = Color(0xFF9EC8FF),
                primaryContainer = Color(0xFF1A4C88),
                onPrimary = Color(0xFFEAF3FF),
                onPrimaryContainer = Color(0xFFE3EEFF),
                secondary = Color(0xFF6DB5FF),
                secondaryDim = Color(0xFF9ACDFF),
                secondaryContainer = Color(0xFF1F4E76),
                onSecondary = Color(0xFFEAF4FF),
                onSecondaryContainer = Color(0xFFDFEEFF),
                tertiary = Color(0xFF7BC8FF),
                tertiaryDim = Color(0xFFAEE0FF),
                tertiaryContainer = Color(0xFF265178),
                onTertiary = Color(0xFFE8F6FF),
                onTertiaryContainer = Color(0xFFE1F1FF),
                background = Color(0xFF0F1724),
                onBackground = Color(0xFFE1EDFA),
                onSurface = Color(0xFFE1EDFA),
                onSurfaceVariant = Color(0xFFB5C7DE),
                outline = Color(0xFF4A6482),
                surfaceContainerLow = Color(0xFF152336),
                surfaceContainer = Color(0xFF1A2A40),
                surfaceContainerHigh = Color(0xFF20324B),
            )
        ACCENT_PRESET_FOREST ->
            copy(
                primary = Color(0xFF5CBD72),
                primaryDim = Color(0xFF9FE0AB),
                primaryContainer = Color(0xFF235D33),
                secondary = Color(0xFF77C98B),
                secondaryDim = Color(0xFFAEE5BA),
                secondaryContainer = Color(0xFF2E6240),
                tertiary = Color(0xFF8ED39C),
                tertiaryDim = Color(0xFFBFECC7),
                tertiaryContainer = Color(0xFF356847),
            )
        ACCENT_PRESET_SUNSET ->
            copy(
                primary = Color(0xFFFF9C52),
                primaryDim = Color(0xFFFFC396),
                primaryContainer = Color(0xFF7A4318),
                secondary = Color(0xFFFFB06A),
                secondaryDim = Color(0xFFFFD1A9),
                secondaryContainer = Color(0xFF84512A),
                tertiary = Color(0xFFFFBC7D),
                tertiaryDim = Color(0xFFFFD9B6),
                tertiaryContainer = Color(0xFF8A5830),
            )
        ACCENT_PRESET_ORCHID ->
            copy(
                primary = Color(0xFFC68BFF),
                primaryDim = Color(0xFFE0BFFF),
                primaryContainer = Color(0xFF5A2C82),
                secondary = Color(0xFFD09AFF),
                secondaryDim = Color(0xFFE7CBFF),
                secondaryContainer = Color(0xFF64388C),
                tertiary = Color(0xFFDDB2FF),
                tertiaryDim = Color(0xFFECD5FF),
                tertiaryContainer = Color(0xFF6E4693),
            )
        ACCENT_PRESET_MONO ->
            copy(
                primary = Color(0xFFE2E2E2),
                primaryDim = Color(0xFFBFBFBF),
                primaryContainer = Color(0xFF404040),
                secondary = Color(0xFFD0D0D0),
                secondaryDim = Color(0xFFB2B2B2),
                secondaryContainer = Color(0xFF4A4A4A),
                tertiary = Color(0xFFC7C7C7),
                tertiaryDim = Color(0xFFA8A8A8),
                tertiaryContainer = Color(0xFF545454),
            )
        else -> this
    }
