package com.maxrave.simpmusic.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import coil3.compose.AsyncImage
import com.maxrave.domain.mediaservice.handler.*
import com.maxrave.simpmusic.wear.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(mediaPlayerHandler: MediaPlayerHandler, openNowPlaying: () -> Unit, openQueue: () -> Unit, openLibrary: () -> Unit) {
    val scope = rememberCoroutineScope()
    val now by mediaPlayerHandler.nowPlayingState.collectAsStateWithLifecycle()
    val control by mediaPlayerHandler.controlState.collectAsStateWithLifecycle()
    val media by mediaPlayerHandler.simpleMediaState.collectAsStateWithLifecycle()
    val queue by mediaPlayerHandler.queueData.collectAsStateWithLifecycle()
    val title = now.songEntity?.title ?: now.track?.title ?: now.mediaItem.metadata.title.orEmpty()
    val artist = now.songEntity?.artistName?.joinToString() ?: now.track?.artists?.joinToString { it.name } ?: now.mediaItem.metadata.artist.orEmpty()
    val art = now.songEntity?.thumbnails ?: now.track?.thumbnails?.lastOrNull()?.url
    val hasTrack = now.mediaItem.mediaId.isNotBlank()
    WearList {
        item { WearScreenHeader("Your music", if (hasTrack) "Ready when you are" else "Find something you love") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(enabled = hasTrack, onClick = openNowPlaying).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    if (!art.isNullOrBlank()) AsyncImage(art, "Album artwork", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.height(10.dp))
                Text(title.ifBlank { "Nothing playing yet" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(artist.ifBlank { "Open your library to get started" }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (hasTrack) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous) } }, enabled = control.isPreviousAvailable) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                    IconButton(onClick = { scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause) } }) {
                        PlaybackControlIcon(control.isPlaying, (media is SimpleMediaState.Loading || media is SimpleMediaState.Buffering) && !control.isPlaying)
                    }
                    IconButton(onClick = { scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next) } }, enabled = control.isNextAvailable) { Icon(Icons.Filled.SkipNext, "Next") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionChip("Library", openLibrary, Modifier.weight(1f), Icons.Filled.LibraryMusic)
                QuickActionChip("Queue · ${queue?.data?.listTracks?.size ?: 0}", openQueue, Modifier.weight(1f), Icons.AutoMirrored.Filled.QueueMusic)
            }
        }
        if (hasTrack) item { FilledTonalButton(onClick = openNowPlaying, modifier = Modifier.fillMaxWidth()) { Text("Open player") } }
    }
}
