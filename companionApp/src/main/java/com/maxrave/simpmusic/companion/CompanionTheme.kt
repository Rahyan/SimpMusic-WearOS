package com.maxrave.simpmusic.companion

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF0B1015)
internal val Panel = Color(0xFF151D25)
internal val Muted = Color(0xFF91A0AF)
internal val Electric = Color(0xFFCCF36B)
internal val Blue = Color(0xFF8DBBFF)

@Composable
internal fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Electric, onPrimary = Ink,
            background = Ink, onBackground = Color(0xFFF4F6F8),
            surface = Panel, onSurface = Color(0xFFF4F6F8),
            onSurfaceVariant = Muted, outline = Color(0xFF2B3742),
            secondary = Blue, error = Color(0xFFFFB4A9),
        ),
        content = content,
    )
}
