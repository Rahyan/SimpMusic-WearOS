package com.maxrave.simpmusic.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.PodcastsEntity
import com.maxrave.domain.data.type.PlaylistType
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.WearList
import org.koin.core.context.GlobalContext

@Composable
fun DownloadedCollectionsScreen(
    onBack: () -> Unit,
    openAlbum: (String) -> Unit,
    openPlaylist: (String) -> Unit,
    openLocalPlaylist: (Long) -> Unit,
    openPodcast: (String) -> Unit,
) {
    val playlistRepository: PlaylistRepository = remember { GlobalContext.get().get() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val downloading by playlistRepository.getAllDownloadingPlaylist().collectAsStateWithLifecycle(initialValue = emptyList())
    val downloaded by playlistRepository.getAllDownloadedPlaylist().collectAsStateWithLifecycle(initialValue = emptyList())

    WearList(state = listState) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (downloading.isEmpty() && downloaded.isEmpty()) {
            item {
                WearEmptyState(
                    title = "No downloaded collections yet.",
                    hint = "Download playlists, albums, or podcasts from phone to sync here.",
                )
            }
            return@WearList
        }

        if (downloading.isNotEmpty()) {
            item {
                Text(
                    text = "Downloading",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(downloading.size) { index ->
                val item = downloading[index]
                CollectionRow(
                    item = item,
                    onOpenAlbum = openAlbum,
                    onOpenPlaylist = openPlaylist,
                    onOpenLocalPlaylist = openLocalPlaylist,
                    onOpenPodcast = openPodcast,
                )
            }
        }

        if (downloaded.isNotEmpty()) {
            item {
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(downloaded.size) { index ->
                val item = downloaded[index]
                CollectionRow(
                    item = item,
                    onOpenAlbum = openAlbum,
                    onOpenPlaylist = openPlaylist,
                    onOpenLocalPlaylist = openLocalPlaylist,
                    onOpenPodcast = openPodcast,
                )
            }
        }
    }
}

@Composable
private fun CollectionRow(
    item: PlaylistType,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenLocalPlaylist: (Long) -> Unit,
    onOpenPodcast: (String) -> Unit,
) {
    val (title, subtitle, onClick) =
        when (item) {
            is PlaylistEntity -> Triple(item.title, "Playlist • ${item.trackCount} tracks") { onOpenPlaylist(item.id) }
            is AlbumEntity -> Triple(item.title, "Album • ${item.trackCount} tracks") { onOpenAlbum(item.browseId) }
            is PodcastsEntity -> Triple(item.title, "Podcast • ${item.listEpisodes.size} episodes") { onOpenPodcast(item.podcastId) }
            is LocalPlaylistEntity -> {
                val count = item.tracks?.size ?: 0
                Triple(item.title, "Local playlist • $count tracks") { onOpenLocalPlaylist(item.id) }
            }
            else -> Triple("Collection", item.playlistType().name) {}
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
