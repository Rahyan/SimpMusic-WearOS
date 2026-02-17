package com.maxrave.simpmusic.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.EpisodeEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.PodcastRepository
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.WearList
import com.maxrave.simpmusic.wear.ui.components.WearLoadingState
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Composable
fun PodcastEpisodesScreen(
    podcastId: String,
    mediaPlayerHandler: MediaPlayerHandler,
    onBack: () -> Unit,
    openNowPlaying: () -> Unit,
) {
    val podcastRepository: PodcastRepository = remember { GlobalContext.get().get() }
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(podcastId, saver = LazyListState.Saver) { LazyListState() }
    val podcast by podcastRepository.getPodcast(podcastId).collectAsStateWithLifecycle(initialValue = null)
    val episodes by podcastRepository.getPodcastEpisodes(podcastId).collectAsStateWithLifecycle(initialValue = emptyList())
    val tracks = remember(episodes) { episodes.map { it.toTrack() } }

    fun play(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        scope.launch {
            mediaPlayerHandler.resetSongAndQueue()
            mediaPlayerHandler.setQueueData(
                QueueData.Data(
                    listTracks = tracks,
                    firstPlayedTrack = tracks.firstOrNull(),
                    playlistId = "podcast_$podcastId",
                    playlistName = podcast?.title ?: "Podcast",
                    playlistType = PlaylistType.PLAYLIST,
                    continuation = null,
                ),
            )
            mediaPlayerHandler.loadMediaItem(
                anyTrack = track,
                type = Config.PLAYLIST_CLICK,
                index = index,
            )
            openNowPlaying()
        }
    }

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
                    text = podcast?.title ?: "Podcast",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { play(0) },
                    enabled = tracks.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                }
            }
        }

        if (podcast == null && episodes.isEmpty()) {
            item { WearLoadingState("Loading podcast episodes...") }
            return@WearList
        }

        if (episodes.isEmpty()) {
            item { WearEmptyState("No episodes cached for this podcast yet.") }
            return@WearList
        }

        items(episodes.size) { index ->
            val episode = episodes[index]
            EpisodeRow(
                episode = episode,
                onClick = { play(index) },
            )
        }
    }
}

private fun EpisodeEntity.toTrack(): Track =
    Track(
        album = null,
        artists = listOf(Artist(id = authorId, name = authorName)),
        duration = durationString,
        durationSeconds = null,
        isAvailable = true,
        isExplicit = false,
        likeStatus = null,
        thumbnails =
            thumbnail
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(Thumbnail(height = 544, width = 544, url = it)) },
        title = title,
        videoId = videoId,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
        year = null,
    )

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    onClick: () -> Unit,
) {
    val subtitle = listOfNotNull(episode.authorName.takeIf { it.isNotBlank() }, episode.durationString).joinToString(" • ")

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        Text(
            text = episode.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
