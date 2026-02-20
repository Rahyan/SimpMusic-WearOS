package com.maxrave.simpmusic.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            .collectAsState(initial = ACCENT_PRESET_DEFAULT)
    val accentMigrated by
        dataStoreManager
            .getString(KEY_WEAR_ACCENT_MIGRATED)
            .collectAsState(initial = null)
    val supportedPresetValues =
        remember {
            setOf(
                ACCENT_PRESET_OCEAN,
                ACCENT_PRESET_FOREST,
                ACCENT_PRESET_SUNSET,
                ACCENT_PRESET_MONO,
            )
        }
    val normalizedPreset =
        when {
            accentPreset.isNullOrBlank() -> ACCENT_PRESET_DEFAULT
            accentPreset in supportedPresetValues -> accentPreset!!
            else -> ACCENT_PRESET_DEFAULT
        }
    LaunchedEffect(accentPreset, accentMigrated) {
        if (accentPreset != normalizedPreset) {
            dataStoreManager.putString(KEY_WEAR_ACCENT_PRESET, normalizedPreset)
        }
        if (accentMigrated == WEAR_ACCENT_MIGRATION_DONE) return@LaunchedEffect
        dataStoreManager.putString(KEY_WEAR_ACCENT_MIGRATED, WEAR_ACCENT_MIGRATION_DONE)
    }
    val baseScheme = dynamicColorScheme(context) ?: ColorScheme()
    val scheme = remember(baseScheme, normalizedPreset) { baseScheme.withAccentPreset(normalizedPreset) }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}
