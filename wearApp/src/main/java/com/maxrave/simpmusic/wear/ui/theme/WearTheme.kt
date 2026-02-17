package com.maxrave.simpmusic.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Shapes
import androidx.wear.compose.material3.Typography
import androidx.wear.compose.material3.dynamicColorScheme
import com.maxrave.domain.manager.DataStoreManager
import org.koin.core.context.GlobalContext

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dataStoreManager: DataStoreManager = remember { GlobalContext.get().get() }
    val accentPreset by
        dataStoreManager
            .getString(KEY_WEAR_ACCENT_PRESET)
            .collectAsState(initial = ACCENT_PRESET_DYNAMIC)
    val baseScheme = dynamicColorScheme(context) ?: ColorScheme()
    val scheme = remember(baseScheme, accentPreset) { baseScheme.withAccentPreset(accentPreset ?: ACCENT_PRESET_DYNAMIC) }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}
