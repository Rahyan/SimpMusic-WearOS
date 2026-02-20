package com.maxrave.simpmusic.wear.ui.theme

const val KEY_WEAR_PLAYER_STYLE = "wear_player_style"
const val WEAR_PLAYER_STYLE_IMMERSIVE = "immersive"
const val WEAR_PLAYER_STYLE_LEGACY = "legacy"
const val WEAR_PLAYER_STYLE_DEFAULT = WEAR_PLAYER_STYLE_IMMERSIVE

fun nextWearPlayerStyle(current: String): String =
    when (current) {
        WEAR_PLAYER_STYLE_LEGACY -> WEAR_PLAYER_STYLE_IMMERSIVE
        else -> WEAR_PLAYER_STYLE_LEGACY
    }

fun wearPlayerStyleLabel(style: String): String =
    when (style) {
        WEAR_PLAYER_STYLE_LEGACY -> "Legacy (classic)"
        else -> "Immersive (new)"
    }
