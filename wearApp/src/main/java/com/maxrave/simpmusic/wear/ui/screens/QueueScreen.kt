package com.maxrave.simpmusic.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.simpmusic.wear.ui.components.WearScreenHeader
import com.maxrave.simpmusic.wear.ui.components.WearTrackRow
import com.maxrave.simpmusic.wear.ui.components.WearList
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.WearLoadingState

@Composable
fun QueueScreen(
    mediaPlayerHandler: MediaPlayerHandler,
    onBack: () -> Unit,
) {
    val queueData = mediaPlayerHandler.queueData.collectAsStateWithLifecycle().value ?: QueueData()
    val currentIndex = mediaPlayerHandler.currentSongIndex.collectAsStateWithLifecycle().value
    val tracks = queueData.data.listTracks
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparingOffline by remember { mutableStateOf(false) }

    WearList(state = listState) {
        item { WearScreenHeader("Up next", "${tracks.size} tracks", onBack = onBack) }

        item { Spacer(Modifier.height(6.dp)) }

        if (queueData.queueState == QueueData.StateSource.STATE_INITIALIZING) {
            item { WearLoadingState("Loading queue...") }
            return@WearList
        }

        if (tracks.isEmpty()) {
            item {
                WearEmptyState(
                    title = "Queue is empty.",
                    hint = "Add a song from Discover or Library.",
                )
            }
            return@WearList
        }

        item {
            androidx.wear.compose.material3.FilledTonalButton(
                onClick = {
                    val selected = tracks.toList()
                    preparingOffline = true
                    scope.launch {
                        try {
                            com.maxrave.simpmusic.wear.offline.OfflineQueue(context.applicationContext).prepare(selected, true)
                            android.widget.Toast.makeText(context, "Queue saved · Downloads wait for Wi-Fi and charging", android.widget.Toast.LENGTH_LONG).show()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            android.widget.Toast.makeText(context, error.message ?: "Couldn’t prepare the offline queue", android.widget.Toast.LENGTH_LONG).show()
                        } finally { preparingOffline = false }
                    }
                }, enabled = !preparingOffline && tracks.size <= 100, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (preparingOffline) "Preparing…" else "Keep queue offline") }
            Text(if (tracks.size > 100) "Choose up to 100 tracks for an offline queue." else "Wi-Fi + charging · Audio only",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            QueueRow(
                track = track,
                isCurrent = index == currentIndex,
                onClick = { mediaPlayerHandler.playMediaItemInMediaSource(index) },
                onRemove = { mediaPlayerHandler.removeMediaItem(index) },
            )
        }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    WearTrackRow(track.title, track.artists?.joinToString { it.name }.orEmpty(), track.thumbnails?.lastOrNull()?.url,
        current = isCurrent, onClick = onClick, trailing = {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove ${track.title} from queue") }
        })
}
