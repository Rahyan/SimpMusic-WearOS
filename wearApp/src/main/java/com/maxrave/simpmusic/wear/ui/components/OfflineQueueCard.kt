package com.maxrave.simpmusic.wear.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import com.maxrave.common.WatchOfflinePack

@Composable fun OfflineQueueCard(pack: WatchOfflinePack, onPlay: () -> Unit, onRetry: () -> Unit) {
    if (pack.total == 0) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Offline queue", style = MaterialTheme.typography.titleSmall)
        Text("${pack.ready} / ${pack.total} ready · ${pack.readySeconds / 60} min",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(pack.message, style = MaterialTheme.typography.labelSmall)
        FilledTonalButton(onPlay, modifier = Modifier.fillMaxWidth(), enabled = pack.ready > 0) {
            Text(if (pack.ready == pack.total) "Play offline queue" else "Play ready tracks")
        }
        if (pack.ready < pack.total) FilledTonalButton(onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry downloads") }
    }
}
