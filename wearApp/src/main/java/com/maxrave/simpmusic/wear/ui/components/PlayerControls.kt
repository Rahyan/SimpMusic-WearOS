package com.maxrave.simpmusic.wear.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.maxrave.domain.mediaservice.handler.RepeatState

private data class ControlSpec(val description: String, val icon: ImageVector, val action: () -> Unit,
    val selected: Boolean = false, val enabled: Boolean = true)

@Composable
fun PlayerControls(shuffle: Boolean, repeat: RepeatState, downloaded: Boolean, downloadEnabled: Boolean,
    downloadDescription: String, canAdjustVolume: Boolean, canHandoff: Boolean,
    onShuffle: () -> Unit, onRepeat: () -> Unit, onQueue: () -> Unit, onDownload: () -> Unit,
    onVolume: () -> Unit, onHandoff: () -> Unit) {
    val controls = listOf(
        ControlSpec(if (shuffle) "Shuffle on" else "Shuffle off", Icons.Filled.Shuffle, onShuffle, selected = shuffle),
        ControlSpec(when (repeat) {
                RepeatState.None -> "Repeat off"
                RepeatState.One -> "Repeat one"
                RepeatState.All -> "Repeat all"
            },
            if (repeat == RepeatState.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat, onRepeat,
            selected = repeat != RepeatState.None),
        ControlSpec("Queue", Icons.AutoMirrored.Filled.QueueMusic, onQueue),
        ControlSpec(downloadDescription, Icons.Filled.Download, onDownload,
            selected = downloaded, enabled = downloadEnabled),
        ControlSpec("Volume", Icons.AutoMirrored.Filled.VolumeUp, onVolume, enabled = canAdjustVolume),
        ControlSpec("Continue on phone", Icons.Filled.PhoneAndroid, onHandoff, enabled = canHandoff),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        controls.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { control ->
                    PlayerControl(control.icon, control.description, Modifier.weight(1f), control.selected,
                        control.enabled, control.action)
                }
            }
        }
    }
}
