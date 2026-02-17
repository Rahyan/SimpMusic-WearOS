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
import com.maxrave.domain.data.entities.PodcastsEntity
import com.maxrave.domain.repository.PodcastRepository
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.WearList
import org.koin.core.context.GlobalContext

@Composable
fun FavoritePodcastsScreen(
    onBack: () -> Unit,
    openPodcast: (String) -> Unit,
) {
    val podcastRepository: PodcastRepository = remember { GlobalContext.get().get() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val podcasts by podcastRepository.getFavoritePodcasts().collectAsStateWithLifecycle(initialValue = emptyList())

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
                    text = "Favorite podcasts",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (podcasts.isEmpty()) {
            item {
                WearEmptyState(
                    title = "No favorite podcasts yet.",
                    hint = "Favorite podcasts from phone first, then open them on watch.",
                )
            }
            return@WearList
        }

        items(podcasts.size) { index ->
            val podcast = podcasts[index]
            PodcastRow(
                podcast = podcast,
                onClick = { openPodcast(podcast.podcastId) },
            )
        }
    }
}

@Composable
private fun PodcastRow(
    podcast: PodcastsEntity,
    onClick: () -> Unit,
) {
    val subtitleParts = mutableListOf<String>()
    if (podcast.authorName.isNotBlank()) subtitleParts.add(podcast.authorName)
    subtitleParts.add("${podcast.listEpisodes.size} episodes")
    val subtitle = subtitleParts.joinToString(" • ")

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        Text(
            text = podcast.title,
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
