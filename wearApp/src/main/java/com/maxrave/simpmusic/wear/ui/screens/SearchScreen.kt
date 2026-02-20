package com.maxrave.simpmusic.wear.ui.screens

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.WearErrorState
import com.maxrave.simpmusic.wear.ui.components.WearList
import com.maxrave.simpmusic.wear.ui.components.WearLoadingState
import com.maxrave.simpmusic.wear.ui.util.friendlyNetworkError
import kotlinx.coroutines.flow.collect
import org.koin.core.context.GlobalContext

private const val SEARCH_REMOTE_INPUT_KEY = "wear_search_query"

@Composable
fun SearchScreen(
    mediaPlayerHandler: MediaPlayerHandler,
    onBack: () -> Unit,
    openNowPlaying: () -> Unit,
    openPlaylistDirectory: () -> Unit,
    openPlaylist: (String) -> Unit,
    openAlbum: (String) -> Unit,
    openArtist: (String) -> Unit,
) {
    val context = LocalContext.current
    val searchRepository: SearchRepository = remember { GlobalContext.get().get() }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(WearSearchFilter.ALL) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    val listState = rememberSaveable(query, saver = LazyListState.Saver) { LazyListState() }

    var songTracks by remember(query) { mutableStateOf<List<Track>>(emptyList()) }
    var videoTracks by remember(query) { mutableStateOf<List<Track>>(emptyList()) }
    var albumResults by remember(query) { mutableStateOf<List<AlbumsResult>>(emptyList()) }
    var artistResults by remember(query) { mutableStateOf<List<ArtistsResult>>(emptyList()) }
    var playlistResults by remember(query) { mutableStateOf<List<PlaylistsResult>>(emptyList()) }
    var featuredPlaylists by remember(query) { mutableStateOf<List<PlaylistsResult>>(emptyList()) }
    var podcastResults by remember(query) { mutableStateOf<List<PlaylistsResult>>(emptyList()) }
    var searchLoading by remember(query) { mutableStateOf(false) }
    var searchError by remember(query) { mutableStateOf<String?>(null) }

    val keyboardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val data = result.data ?: return@rememberLauncherForActivityResult
            val input = RemoteInput.getResultsFromIntent(data)
            val typed = input?.getCharSequence(SEARCH_REMOTE_INPUT_KEY)?.toString()?.trim().orEmpty()
            if (typed.isBlank()) return@rememberLauncherForActivityResult
            query = typed
        }

    fun openWearKeyboard() {
        val remoteInputs =
            listOf(
                RemoteInput
                    .Builder(SEARCH_REMOTE_INPUT_KEY)
                    .setLabel("Search music")
                    .build(),
            )
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        keyboardLauncher.launch(intent)
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            selectedFilter = WearSearchFilter.ALL
            songTracks = emptyList()
            videoTracks = emptyList()
            albumResults = emptyList()
            artistResults = emptyList()
            playlistResults = emptyList()
            featuredPlaylists = emptyList()
            podcastResults = emptyList()
            searchLoading = false
            searchError = null
            return@LaunchedEffect
        }

        searchLoading = true
        searchError = null
        songTracks = emptyList()
        videoTracks = emptyList()
        albumResults = emptyList()
        artistResults = emptyList()
        playlistResults = emptyList()
        featuredPlaylists = emptyList()
        podcastResults = emptyList()

        var lastError: String? = null

        searchRepository.getSearchDataSong(query).collect { values ->
            when (values) {
                is Resource.Success -> songTracks = values.data.orEmpty().map { it.toTrack() }
                is Resource.Error -> lastError = values.message
            }
        }

        searchRepository.getSearchDataVideo(query).collect { values ->
            when (values) {
                is Resource.Success -> videoTracks = values.data.orEmpty().map { it.toTrack() }
                is Resource.Error -> if (lastError.isNullOrBlank()) lastError = values.message
            }
        }

        searchRepository.getSearchDataAlbum(query).collect { values ->
            when (values) {
                is Resource.Success ->
                    albumResults =
                        values.data
                            .orEmpty()
                            .filter { it.browseId.isNotBlank() }
                            .distinctBy { it.browseId }
                is Resource.Error -> if (lastError.isNullOrBlank()) lastError = values.message
            }
        }

        searchRepository.getSearchDataArtist(query).collect { values ->
            when (values) {
                is Resource.Success ->
                    artistResults =
                        values.data
                            .orEmpty()
                            .filter { it.browseId.isNotBlank() }
                            .distinctBy { it.browseId }
                is Resource.Error -> if (lastError.isNullOrBlank()) lastError = values.message
            }
        }

        searchRepository.getSearchDataPlaylist(query).collect { values ->
            when (values) {
                is Resource.Success ->
                    playlistResults =
                        values.data
                            .orEmpty()
                            .filter { it.browseId.isNotBlank() }
                            .distinctBy { it.browseId }
                is Resource.Error -> if (lastError.isNullOrBlank()) lastError = values.message
            }
        }

        searchRepository.getSearchDataFeaturedPlaylist(query).collect { values ->
            when (values) {
                is Resource.Success ->
                    featuredPlaylists =
                        values.data
                            .orEmpty()
                            .filter { it.browseId.isNotBlank() }
                            .distinctBy { it.browseId }
                is Resource.Error -> if (lastError.isNullOrBlank()) lastError = values.message
            }
        }

        searchRepository.getSearchDataPodcast(query).collect { values ->
            when (values) {
                is Resource.Success ->
                    podcastResults =
                        values.data
                            .orEmpty()
                            .filter { it.browseId.isNotBlank() }
                            .distinctBy { it.browseId }
                is Resource.Error -> if (lastError.isNullOrBlank()) lastError = values.message
            }
        }

        searchLoading = false

        val hasAnyResults =
            songTracks.isNotEmpty() ||
                videoTracks.isNotEmpty() ||
                albumResults.isNotEmpty() ||
                artistResults.isNotEmpty() ||
                playlistResults.isNotEmpty() ||
                featuredPlaylists.isNotEmpty() ||
                podcastResults.isNotEmpty()

        if (!hasAnyResults && !lastError.isNullOrBlank()) {
            searchError = lastError
        }
    }

    val trackForDetails = selectedTrack
    if (trackForDetails != null) {
        SongDetailsScreen(
            track = trackForDetails,
            mediaPlayerHandler = mediaPlayerHandler,
            onBack = { selectedTrack = null },
            onPlayRequested = {
                mediaPlayerHandler.loadMediaItem(
                    anyTrack = trackForDetails,
                    type = Config.SONG_CLICK,
                    index = null,
                )
            },
            onOpenNowPlaying = openNowPlaying,
            onOpenArtist = { artistId -> openArtist(artistId) },
        )
        return
    }

    val allPlaylists = (playlistResults + featuredPlaylists).distinctBy { it.browseId }

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
                    text = "Search",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { openWearKeyboard() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Open keyboard")
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = { openWearKeyboard() },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text("Search on watch")
            }
        }

        item {
            FilledTonalButton(
                onClick = openPlaylistDirectory,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Icon(Icons.Filled.QueueMusic, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Browse playlists")
            }
        }

        if (query.isBlank()) {
            item {
                WearEmptyState(
                    title = "No search yet.",
                    hint = "Search songs, playlists, artists, and podcasts from watch.",
                )
            }
            return@WearList
        }

        item {
            Text(
                text = "Results for \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    WearSearchFilterChip(
                        label = "All",
                        selected = selectedFilter == WearSearchFilter.ALL,
                        onClick = { selectedFilter = WearSearchFilter.ALL },
                        modifier = Modifier.weight(1f),
                    )
                    WearSearchFilterChip(
                        label = "Songs",
                        selected = selectedFilter == WearSearchFilter.SONGS,
                        onClick = { selectedFilter = WearSearchFilter.SONGS },
                        modifier = Modifier.weight(1f),
                    )
                    WearSearchFilterChip(
                        label = "Playlists",
                        selected = selectedFilter == WearSearchFilter.PLAYLISTS,
                        onClick = { selectedFilter = WearSearchFilter.PLAYLISTS },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    WearSearchFilterChip(
                        label = "Artists",
                        selected = selectedFilter == WearSearchFilter.ARTISTS,
                        onClick = { selectedFilter = WearSearchFilter.ARTISTS },
                        modifier = Modifier.weight(1f),
                    )
                    WearSearchFilterChip(
                        label = "Podcasts",
                        selected = selectedFilter == WearSearchFilter.PODCASTS,
                        onClick = { selectedFilter = WearSearchFilter.PODCASTS },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (searchLoading) {
            item { WearLoadingState("Searching all categories...") }
            return@WearList
        }

        val noResults =
            when (selectedFilter) {
                WearSearchFilter.ALL ->
                    songTracks.isEmpty() &&
                        videoTracks.isEmpty() &&
                        albumResults.isEmpty() &&
                        artistResults.isEmpty() &&
                        allPlaylists.isEmpty() &&
                        podcastResults.isEmpty()
                WearSearchFilter.SONGS -> songTracks.isEmpty() && videoTracks.isEmpty()
                WearSearchFilter.PLAYLISTS -> allPlaylists.isEmpty()
                WearSearchFilter.ARTISTS -> artistResults.isEmpty()
                WearSearchFilter.PODCASTS -> podcastResults.isEmpty()
            }

        if (noResults) {
            item {
                val msg = searchError
                if (!msg.isNullOrBlank()) {
                    WearErrorState(
                        message = context.friendlyNetworkError(msg),
                        actionLabel = "Retry",
                        onAction = { openWearKeyboard() },
                    )
                } else {
                    WearEmptyState(
                        title = "No results found.",
                        hint = "Try another keyword.",
                    )
                }
            }
            return@WearList
        }

        if ((selectedFilter == WearSearchFilter.ALL || selectedFilter == WearSearchFilter.SONGS) && songTracks.isNotEmpty()) {
            item { SectionHeader("Songs", songTracks.size) }
            items(songTracks.take(8).size) { index ->
                val track = songTracks[index]
                TrackRow(
                    title = track.title,
                    subtitle = track.artists?.joinToString { it.name }.orEmpty(),
                    onClick = { selectedTrack = track },
                )
            }
        }

        if ((selectedFilter == WearSearchFilter.ALL || selectedFilter == WearSearchFilter.SONGS) && videoTracks.isNotEmpty()) {
            item { SectionHeader("Videos", videoTracks.size) }
            items(videoTracks.take(6).size) { index ->
                val track = videoTracks[index]
                TrackRow(
                    title = track.title,
                    subtitle = track.artists?.joinToString { it.name }.orEmpty(),
                    onClick = { selectedTrack = track },
                )
            }
        }

        if (selectedFilter == WearSearchFilter.ALL && albumResults.isNotEmpty()) {
            item { SectionHeader("Albums", albumResults.size) }
            items(albumResults.take(8).size) { index ->
                val album = albumResults[index]
                TrackRow(
                    title = album.title,
                    subtitle = listOf(album.artists.joinToString { it.name }, album.year).filter { it.isNotBlank() }.joinToString(" • "),
                    onClick = { openAlbum(album.browseId) },
                )
            }
        }

        if ((selectedFilter == WearSearchFilter.ALL || selectedFilter == WearSearchFilter.ARTISTS) && artistResults.isNotEmpty()) {
            item { SectionHeader("Artists", artistResults.size) }
            items(artistResults.take(8).size) { index ->
                val artist = artistResults[index]
                TrackRow(
                    title = artist.artist,
                    subtitle = artist.subscribersOrCategory(),
                    onClick = { openArtist(artist.browseId) },
                )
            }
        }

        if ((selectedFilter == WearSearchFilter.ALL || selectedFilter == WearSearchFilter.PLAYLISTS) && allPlaylists.isNotEmpty()) {
            item { SectionHeader("Playlists", allPlaylists.size) }
            items(allPlaylists.take(10).size) { index ->
                val playlist = allPlaylists[index]
                TrackRow(
                    title = playlist.title,
                    subtitle = listOf(playlist.author, playlist.itemCount).filter { it.isNotBlank() }.joinToString(" • "),
                    onClick = { openPlaylist(playlist.browseId) },
                )
            }
        }

        if ((selectedFilter == WearSearchFilter.ALL || selectedFilter == WearSearchFilter.PODCASTS) && podcastResults.isNotEmpty()) {
            item { SectionHeader("Podcasts", podcastResults.size) }
            items(podcastResults.take(10).size) { index ->
                val podcast = podcastResults[index]
                TrackRow(
                    title = podcast.title,
                    subtitle = listOf(podcast.author, podcast.itemCount).filter { it.isNotBlank() }.joinToString(" • "),
                    onClick = { openPlaylist(podcast.browseId) },
                )
            }
        }
    }
}

private enum class WearSearchFilter {
    ALL,
    SONGS,
    PLAYLISTS,
    ARTISTS,
    PODCASTS,
}

@Composable
private fun WearSearchFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.48f)
                        },
                    shape = RoundedCornerShape(999.dp),
                ).border(
                    width = 1.dp,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        },
                    shape = RoundedCornerShape(999.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TrackRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
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
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun ArtistsResult.subscribersOrCategory(): String =
    listOf(resultType, category).filter { it.isNotBlank() }.joinToString(" • ")
