package com.maxrave.simpmusic.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import com.maxrave.simpmusic.wear.ui.util.formatDurationMs

@Composable fun SeekScreen(positionMs: Long, durationMs: Long, onApply: (Long) -> Unit, onCancel: () -> Unit) {
    var target by remember { mutableLongStateOf(positionMs.coerceIn(0, durationMs)) }
    val focus = remember { FocusRequester() }
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp).onRotaryScrollEvent {
        rotation += it.verticalScrollPixels
        val steps = (rotation / 24f).toInt()
        rotation -= steps * 24f
        target = (target + steps * 5_000L).coerceIn(0, durationMs)
        true
    }.focusRequester(focus).focusable(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly) {
        Text("Seek", style = MaterialTheme.typography.titleSmall)
        Text(formatDurationMs(target), style = MaterialTheme.typography.titleLarge)
        Canvas(Modifier.fillMaxWidth().height(24.dp).pointerInput(durationMs) {
            detectTapGestures { target = (durationMs * (it.x / size.width).coerceIn(0f, 1f)).toLong() }
        }) {
            drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx())
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width * target.toFloat() / durationMs, size.height / 2), 3.dp.toPx())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton({ target = (target - 10_000).coerceAtLeast(0) }, Modifier.size(48.dp)) { Icon(Icons.Default.Remove, "Back ten seconds") }
            IconButton({ target = (target + 10_000).coerceAtMost(durationMs) }, Modifier.size(48.dp)) { Icon(Icons.Default.Add, "Forward ten seconds") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onCancel, modifier = Modifier.weight(1f).height(48.dp)) { Text("Cancel", style = MaterialTheme.typography.labelSmall) }
            FilledTonalButton({ onApply(target) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Apply", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
