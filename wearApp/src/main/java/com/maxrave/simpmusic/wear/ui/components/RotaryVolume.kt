package com.maxrave.simpmusic.wear.ui.components

import android.media.AudioManager
import android.view.ViewConfiguration
import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext

/** Player/volume pages use the bezel for volume; lists retain their ordinary rotary scrolling. */
@Composable fun Modifier.rotaryVolume(enabled: Boolean = true, onVolumeChanged: (Int, Int) -> Unit = { _, _ -> }): Modifier {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val focus = remember { FocusRequester() }
    val factor = remember(context) { ViewConfiguration.get(context).scaledVerticalScrollFactor }
    val ticks = remember(factor, enabled) { RotaryVolumeSteps(factor) }
    val callback by rememberUpdatedState(onVolumeChanged)
    LaunchedEffect(enabled) { if (enabled) focus.requestFocus() }
    if (!enabled) return this
    return onRotaryScrollEvent {
        val steps = ticks.consume(it.verticalScrollPixels, it.uptimeMillis)
        if (steps != 0) {
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                (current + steps).coerceIn(0, maximum), 0)
            callback(audio.getStreamVolume(AudioManager.STREAM_MUSIC), maximum)
        }
        true
    }.focusRequester(focus).focusable()
}
