package com.maxrave.simpmusic.wear.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.QuickActionChip
import com.maxrave.simpmusic.wear.ui.components.WearList
import com.maxrave.simpmusic.wear.ui.components.WearLoadingState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.koin.core.context.GlobalContext

private object WearLibraryScreenCache {
    var playlists: List<LocalPlaylistEntity> = emptyList()
    var downloadedTracks: List<com.maxrave.domain.data.model.browse.album.Track> = emptyList()
    var hasResolvedPlaylists: Boolean = false
    var hasResolvedDownloads: Boolean = false
    var scrollIndex: Int = 0
    var scrollOffset: Int = 0
}

@Composable
fun LibraryScreen(
    mediaPlayerHandler: MediaPlayerHandler,
    onBack: () -> Unit,
    openPlaylist: (Long) -> Unit,
    openDownloads: () -> Unit,
    openNowPlaying: () -> Unit,
    openSearch: () -> Unit,
    openOnlinePlaylists: () -> Unit,
    openLikedSongs: () -> Unit,
    openRecentPlays: () -> Unit,
    openFollowedArtists: () -> Unit,
    openLikedAlbums: () -> Unit,
    openFollowedReleases: () -> Unit,
    openLikedPlaylists: () -> Unit,
    openFavoritePodcasts: () -> Unit,
    openDownloadedCollections: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo: LocalPlaylistRepository = remember { GlobalContext.get().get() }
    val songRepository: SongRepository = remember { GlobalContext.get().get() }
    val playlists by remember(repo) { repo.getAllLocalPlaylists() }.collectAsStateWithLifecycle(initialValue = null)
    val downloadedSongs by remember(songRepository) { songRepository.getDownloadedSongs() }.collectAsStateWithLifecycle(initialValue = null)
    val listState =
        rememberSaveable(saver = LazyListState.Saver) {
            LazyListState(
                firstVisibleItemIndex = WearLibraryScreenCache.scrollIndex,
                firstVisibleItemScrollOffset = WearLibraryScreenCache.scrollOffset,
            )
        }
    var cachedPlaylists by remember { mutableStateOf(WearLibraryScreenCache.playlists) }
    var cachedDownloadedTracks by remember { mutableStateOf(WearLibraryScreenCache.downloadedTracks) }

    LaunchedEffect(playlists) {
        playlists?.let {
            cachedPlaylists = it
            WearLibraryScreenCache.playlists = it
            WearLibraryScreenCache.hasResolvedPlaylists = true
        }
    }
    LaunchedEffect(downloadedSongs) {
        downloadedSongs?.let { songs ->
            val mapped = songs.map { it.toTrack() }
            cachedDownloadedTracks = mapped
            WearLibraryScreenCache.downloadedTracks = mapped
            WearLibraryScreenCache.hasResolvedDownloads = true
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                WearLibraryScreenCache.scrollIndex = index
                WearLibraryScreenCache.scrollOffset = offset
            }
    }

    val displayPlaylists = playlists ?: cachedPlaylists
    val downloadedTracks = downloadedSongs?.map { it.toTrack() } ?: cachedDownloadedTracks

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
                    text = "Library",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { Spacer(Modifier.height(6.dp)) }

        item {
            FilledTonalButton(
                onClick = openDownloads,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text("Downloads (${downloadedTracks.size})")
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionChip(
                    label = "Search",
                    icon = Icons.Filled.Search,
                    onClick = openSearch,
                    modifier = Modifier.weight(1f),
                )
                QuickActionChip(
                    label = "Online",
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = openOnlinePlaylists,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionChip(
                    label = "Liked",
                    icon = Icons.Filled.Favorite,
                    onClick = openLikedSongs,
                    modifier = Modifier.weight(1f),
                )
                QuickActionChip(
                    label = "Recent",
                    icon = Icons.Filled.History,
                    onClick = openRecentPlays,
                    modifier = Modifier.weight(1f),
                )
                QuickActionChip(
                    label = "Artists",
                    icon = Icons.Filled.Person,
                    onClick = openFollowedArtists,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionChip(
                    label = "Albums",
                    icon = Icons.Filled.Album,
                    onClick = openLikedAlbums,
                    modifier = Modifier.weight(1f),
                )
                QuickActionChip(
                    label = "Releases",
                    icon = Icons.Filled.LibraryMusic,
                    onClick = openFollowedReleases,
                    modifier = Modifier.weight(1f),
                )
                QuickActionChip(
                    label = "Playlists",
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = openLikedPlaylists,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionChip(
                    label = "Podcasts",
                    icon = Icons.Filled.LibraryMusic,
                    onClick = openFavoritePodcasts,
                    modifier = Modifier.weight(1f),
                )
                QuickActionChip(
                    label = "Collections",
                    icon = Icons.Filled.Download,
                    onClick = openDownloadedCollections,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        item {
            Button(
                onClick = {
                    scope.launch {
                        val tracks = downloadedTracks
                        val firstTrack = tracks.firstOrNull() ?: return@launch
                        mediaPlayerHandler.resetSongAndQueue()
                        mediaPlayerHandler.setQueueData(
                            QueueData.Data(
                                listTracks = tracks,
                                firstPlayedTrack = firstTrack,
                                playlistId = "wear_downloads",
                                playlistName = "Downloads",
                                playlistType = PlaylistType.PLAYLIST,
                                continuation = null,
                            ),
                        )
                        mediaPlayerHandler.loadMediaItem(
                            anyTrack = firstTrack,
                            type = Config.PLAYLIST_CLICK,
                            index = 0,
                        )
                        Toast.makeText(context, "Playing downloaded songs", Toast.LENGTH_SHORT).show()
                        openNowPlaying()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = downloadedTracks.isNotEmpty(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text("Play downloaded")
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        item {
            Text(
                text = "Local playlists",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val hasLibrarySnapshot =
            WearLibraryScreenCache.hasResolvedPlaylists ||
                WearLibraryScreenCache.hasResolvedDownloads ||
                cachedPlaylists.isNotEmpty() ||
                cachedDownloadedTracks.isNotEmpty()
        if (!hasLibrarySnapshot && playlists == null && downloadedSongs == null) {
            item {
                WearLoadingState("Loading library...")
            }
            return@WearList
        }

        if (displayPlaylists.isEmpty()) {
            item {
                WearEmptyState(
                    title = "No local playlists yet.",
                    hint = "Create playlists on phone or add music first.",
                )
            }
            return@WearList
        }

        items(displayPlaylists.size) { index ->
            val pl = displayPlaylists[index]
            PlaylistRow(playlist = pl, onClick = { openPlaylist(pl.id) })
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: LocalPlaylistEntity,
    onClick: () -> Unit,
) {
    val count = playlist.tracks?.size ?: 0
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(16.dp),
                ).border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primaryDim.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$count tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
