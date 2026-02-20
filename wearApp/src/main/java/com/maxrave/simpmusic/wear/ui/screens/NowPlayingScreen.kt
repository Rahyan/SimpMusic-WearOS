package com.maxrave.simpmusic.wear.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Watch
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.wearable.Wearable
import com.maxrave.common.WearCompanionBridge
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.LyricsEntity
import com.maxrave.domain.data.entities.TranslatedLyricsEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.domain.mediaservice.handler.SimpleMediaState
import com.maxrave.domain.repository.LyricsCanvasRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.wear.ui.components.PlaybackControlIcon
import com.maxrave.simpmusic.wear.ui.components.ThinProgressBar
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.WearLoadingState
import com.maxrave.simpmusic.wear.ui.components.WearList
import com.maxrave.simpmusic.wear.ui.theme.KEY_WEAR_PLAYER_STYLE
import com.maxrave.simpmusic.wear.ui.theme.KEY_WEAR_BATTERY_SAVER_MODE
import com.maxrave.simpmusic.wear.ui.theme.KEY_WEAR_PHONE_OFFLOAD_CONTROLS
import com.maxrave.simpmusic.wear.ui.theme.WEAR_PLAYER_STYLE_DEFAULT
import com.maxrave.simpmusic.wear.ui.theme.WEAR_PLAYER_STYLE_IMMERSIVE
import com.maxrave.simpmusic.wear.ui.util.formatDurationMs
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    mediaPlayerHandler: MediaPlayerHandler,
    onBack: () -> Unit,
    onOpenVolumeSettings: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val downloadHandler: DownloadHandler = remember { GlobalContext.get().get() }
    val dataStoreManager: DataStoreManager = remember { GlobalContext.get().get() }
    val lyricsCanvasRepository: LyricsCanvasRepository = remember { GlobalContext.get().get() }

    val nowPlayingState = mediaPlayerHandler.nowPlayingState.collectAsStateWithLifecycle().value
    val controlState = mediaPlayerHandler.controlState.collectAsStateWithLifecycle().value
    val downloadTaskState = downloadHandler.downloadTask.collectAsStateWithLifecycle().value
    val simpleState = mediaPlayerHandler.simpleMediaState.collectAsStateWithLifecycle().value
    val playerStyle =
        dataStoreManager.getString(KEY_WEAR_PLAYER_STYLE)
            .collectAsStateWithLifecycle(initialValue = WEAR_PLAYER_STYLE_DEFAULT)
            .value ?: WEAR_PLAYER_STYLE_DEFAULT
    val wearBatterySaverEnabled =
        dataStoreManager.getString(KEY_WEAR_BATTERY_SAVER_MODE)
            .collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE)
            .value == DataStoreManager.TRUE
    val phoneOffloadEnabled =
        dataStoreManager.getString(KEY_WEAR_PHONE_OFFLOAD_CONTROLS)
            .collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE)
            .value == DataStoreManager.TRUE
    var watchOnlyOverride by rememberSaveable { mutableStateOf(false) }
    val offloadActive = phoneOffloadEnabled && !watchOnlyOverride
    LaunchedEffect(phoneOffloadEnabled) {
        if (!phoneOffloadEnabled) watchOnlyOverride = false
    }
    val isLoading = simpleState is SimpleMediaState.Loading || simpleState is SimpleMediaState.Buffering
    val detailTrack = nowPlayingState.track ?: nowPlayingState.songEntity?.toTrack()
    var showDetails by rememberSaveable(nowPlayingState.mediaItem.mediaId) { mutableStateOf(false) }

    if (showDetails && detailTrack != null) {
        SongDetailsScreen(
            track = detailTrack,
            mediaPlayerHandler = mediaPlayerHandler,
            onBack = { showDetails = false },
            onPlayRequested = {
                mediaPlayerHandler.loadMediaItem(
                    anyTrack = detailTrack,
                    type = Config.SONG_CLICK,
                    index = null,
                )
            },
            onOpenNowPlaying = { showDetails = false },
        )
        return
    }

    val title =
        nowPlayingState.songEntity?.title
            ?: nowPlayingState.track?.title
            ?: nowPlayingState.mediaItem.metadata.title
            ?: "Nothing playing"
    val artistFromSong = nowPlayingState.songEntity?.artistName?.joinToString()
    val artist =
        artistFromSong
            ?: nowPlayingState.track?.artists?.joinToString { it.name }
            ?: nowPlayingState.mediaItem.metadata.artist
            ?: ""
    val artworkUrl =
        nowPlayingState.track?.thumbnails?.lastOrNull()?.url
            ?: nowPlayingState.songEntity?.thumbnails
            ?: nowPlayingState.mediaItem.metadata.artworkUri

    val durationMs =
        when (simpleState) {
            is SimpleMediaState.Ready -> simpleState.duration
            is SimpleMediaState.Loading -> simpleState.duration
            else -> mediaPlayerHandler.getPlayerDuration()
        }

    val rawProgressMs =
        when (simpleState) {
            is SimpleMediaState.Progress -> simpleState.progress
            is SimpleMediaState.Buffering -> simpleState.position
            else -> mediaPlayerHandler.getProgress()
        }
    var progressMs by rememberSaveable { mutableLongStateOf(rawProgressMs) }
    LaunchedEffect(rawProgressMs, controlState.isPlaying, durationMs) {
        progressMs = rawProgressMs
        if (!controlState.isPlaying) return@LaunchedEffect
        while (controlState.isPlaying) {
            delay(1000L)
            val latest = mediaPlayerHandler.getProgress()
            progressMs =
                if (durationMs > 0L) {
                    latest.coerceIn(0L, durationMs)
                } else {
                    latest.coerceAtLeast(0L)
                }
        }
    }
    val effectiveProgressMs =
        if (wearBatterySaverEnabled) {
            (progressMs / 1000L) * 1000L
        } else {
            progressMs
        }

    val progressFraction =
        if (durationMs > 0L) {
            effectiveProgressMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()
        } else {
            0f
        }
    val isResolvingStream =
        nowPlayingState.isNotEmpty() &&
            !controlState.isPlaying &&
            durationMs <= 0L &&
            effectiveProgressMs <= 0L
    val showLoadingUi = isLoading || isResolvingStream
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val currentDownloadState = detailTrack?.videoId?.let { downloadTaskState[it] } ?: DownloadState.STATE_NOT_DOWNLOADED
    val canTriggerDownload = detailTrack != null && currentDownloadState == DownloadState.STATE_NOT_DOWNLOADED
    val canRemoveDownload = detailTrack != null && currentDownloadState == DownloadState.STATE_DOWNLOADED
    val downloadEnabled = canTriggerDownload || canRemoveDownload
    val downloadIconTint =
        when (currentDownloadState) {
            DownloadState.STATE_NOT_DOWNLOADED -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.primary
        }
    val downloadContentDescription =
        when {
            detailTrack == null -> "Download unavailable"
            currentDownloadState == DownloadState.STATE_DOWNLOADED -> "Remove download"
            currentDownloadState == DownloadState.STATE_PREPARING -> "Preparing download"
            currentDownloadState == DownloadState.STATE_DOWNLOADING -> "Downloading"
            else -> "Download"
        }
    var retrySuggested by rememberSaveable(nowPlayingState.mediaItem.mediaId) { mutableStateOf(false) }
    LaunchedEffect(showLoadingUi, controlState.isPlaying, nowPlayingState.mediaItem.mediaId, wearBatterySaverEnabled) {
        if (!showLoadingUi || controlState.isPlaying || !nowPlayingState.isNotEmpty()) {
            retrySuggested = false
            return@LaunchedEffect
        }
        retrySuggested = false
        delay(if (wearBatterySaverEnabled) 18_000L else 12_000L)
        if (showLoadingUi && !controlState.isPlaying && nowPlayingState.isNotEmpty()) {
            retrySuggested = true
        }
    }
    val defaultImmersiveTint = MaterialTheme.colorScheme.primary
    var immersiveTint by remember(nowPlayingState.mediaItem.mediaId) {
        mutableStateOf(defaultImmersiveTint)
    }
    LaunchedEffect(artworkUrl, nowPlayingState.mediaItem.mediaId, defaultImmersiveTint, wearBatterySaverEnabled) {
        immersiveTint =
            if (!wearBatterySaverEnabled && !artworkUrl.isNullOrBlank()) {
                loadArtworkAccentColor(context, artworkUrl) ?: defaultImmersiveTint
            } else {
                defaultImmersiveTint
            }
    }
    val currentMediaId = nowPlayingState.mediaItem.mediaId.ifBlank { detailTrack?.videoId.orEmpty() }
    val durationSeconds = if (durationMs > 0L) (durationMs / 1000L).toInt() else (detailTrack?.durationSeconds ?: 0)
    val queueData = mediaPlayerHandler.queueData.collectAsStateWithLifecycle().value?.data
    val queueTracks = queueData?.listTracks.orEmpty()
    val queueCurrentIndex = mediaPlayerHandler.currentSongIndex.collectAsStateWithLifecycle().value
    var lyricsUiState by remember(currentMediaId) { mutableStateOf<WearLyricsUiState>(WearLyricsUiState.Loading) }
    LaunchedEffect(currentMediaId, title, artist, durationSeconds, currentDownloadState) {
        if (currentMediaId.isBlank()) {
            lyricsUiState = WearLyricsUiState.Empty
            return@LaunchedEffect
        }
        lyricsUiState = WearLyricsUiState.Loading
        lyricsUiState =
            loadWearLyricsState(
                lyricsCanvasRepository = lyricsCanvasRepository,
                dataStoreManager = dataStoreManager,
                mediaId = currentMediaId,
                title = title,
                artist = artist,
                durationSeconds = durationSeconds,
                preferOfflineFirst = currentDownloadState == DownloadState.STATE_DOWNLOADED,
            )
    }
    suspend fun dispatchTransportWithOffload(
        command: String,
        fallback: suspend () -> Unit,
    ): Boolean {
        if (!offloadActive) {
            fallback()
            return false
        }
        val sent = sendRemoteCommandToPhone(context, command)
        if (sent) return true
        fallback()
        Toast.makeText(context, "Phone not reachable, using watch playback", Toast.LENGTH_SHORT).show()
        return false
    }

    if (playerStyle == WEAR_PLAYER_STYLE_IMMERSIVE) {
        var advancedExpanded by rememberSaveable(nowPlayingState.mediaItem.mediaId) { mutableStateOf(false) }
        var sheetTab by rememberSaveable(nowPlayingState.mediaItem.mediaId) { mutableStateOf(WearSheetTab.CONTROLS) }
        var lyricsDisplayMode by rememberSaveable(nowPlayingState.mediaItem.mediaId) { mutableStateOf(WearLyricsDisplayMode.ORIGINAL) }
        val expandedSheetListState = rememberSaveable(nowPlayingState.mediaItem.mediaId, saver = LazyListState.Saver) { LazyListState() }
        val displayedLyricsLines =
            remember(lyricsUiState, lyricsDisplayMode) {
                (lyricsUiState as? WearLyricsUiState.Ready)?.let { state ->
                    buildDisplayedLyricsLines(
                        mode = lyricsDisplayMode,
                        original = state.originalLines,
                        translated = state.translatedLines,
                    )
                }.orEmpty()
            }
        val hasSyncedLyrics = displayedLyricsLines.any { it.startTimeMs > 0L }
        val activeLyricIndex =
            if (hasSyncedLyrics) {
                findActiveLyricIndex(displayedLyricsLines, effectiveProgressMs)
            } else {
                -1
            }
        LaunchedEffect(advancedExpanded, sheetTab, activeLyricIndex, displayedLyricsLines.size) {
            if (!advancedExpanded || sheetTab != WearSheetTab.LYRICS || activeLyricIndex < 0 || displayedLyricsLines.isEmpty()) return@LaunchedEffect
            centerLyricLineInSheet(
                listState = expandedSheetListState,
                lyricLineIndex = activeLyricIndex,
            )
        }
        val immersiveOverlayColor =
            lerp(MaterialTheme.colorScheme.surfaceContainerHigh, immersiveTint, 0.34f).copy(alpha = 0.72f)
        val immersiveSheetColor =
            lerp(MaterialTheme.colorScheme.surfaceContainerHigh, immersiveTint, 0.30f).copy(alpha = 0.96f)

        val dragSheetModifier =
            Modifier.pointerInput(advancedExpanded) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (!advancedExpanded && totalDrag < -28f) {
                            advancedExpanded = true
                        } else if (advancedExpanded && totalDrag > 28f) {
                            advancedExpanded = false
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f },
                )
            }
        val expandedSheetDragModifier =
            Modifier.pointerInput(advancedExpanded, expandedSheetListState) {
                if (!advancedExpanded) return@pointerInput
                var downwardDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) {
                            downwardDrag += dragAmount
                        }
                    },
                    onDragEnd = {
                        if (downwardDrag > 30f) advancedExpanded = false
                        downwardDrag = 0f
                    },
                    onDragCancel = { downwardDrag = 0f },
                )
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(immersiveOverlayColor),
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(36.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color =
                        if (detailTrack != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    textAlign = TextAlign.Center,
                    modifier =
                        (if (detailTrack != null) {
                            Modifier
                                .fillMaxWidth()
                                .clickable { showDetails = true }
                        } else {
                            Modifier.fillMaxWidth()
                        }).basicMarquee(),
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                dispatchTransportWithOffload(WearCompanionBridge.REMOTE_PREVIOUS) {
                                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
                                }
                            }
                        },
                        enabled = controlState.isPreviousAvailable,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                    }

                    CircularProgressPlayButton(
                        progressFraction = progressFraction,
                        isPlaying = controlState.isPlaying,
                        isLoading = showLoadingUi && !controlState.isPlaying && !retrySuggested,
                        progressColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                dispatchTransportWithOffload(WearCompanionBridge.REMOTE_PLAY_PAUSE) {
                                    if (!controlState.isPlaying && nowPlayingState.isNotEmpty() && (retrySuggested || showLoadingUi)) {
                                        mediaPlayerHandler.retryCurrentStream()
                                    } else {
                                        mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                                    }
                                }
                            }
                        },
                    )

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                dispatchTransportWithOffload(WearCompanionBridge.REMOTE_NEXT) {
                                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                                }
                            }
                        },
                        enabled = controlState.isNextAvailable,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                    }
                }

                Spacer(Modifier.height(4.dp))
                if (durationMs > 0L) {
                    Text(
                        text = "${formatDurationMs(effectiveProgressMs)}/${formatDurationMs(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text =
                            when {
                                retrySuggested -> "Stream timed out. Tap play to retry."
                                controlState.isPlaying -> "Buffering..."
                                else -> "Resolving stream..."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.weight(1f))
            }

            AnimatedVisibility(
                visible = advancedExpanded,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(immersiveSheetColor)
                            .padding(horizontal = 14.dp)
                            .then(expandedSheetDragModifier),
                    state = expandedSheetListState,
                    contentPadding = PaddingValues(top = 24.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(34.dp)
                                        .height(5.dp)
                                        .then(dragSheetModifier)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                                        .clickable { advancedExpanded = false },
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WearSheetTab.values().forEach { tab ->
                                val selected = tab == sheetTab
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.68f)
                                                },
                                            ).border(
                                                width = 1.dp,
                                                color =
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                                                    } else {
                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                                    },
                                                shape = RoundedCornerShape(999.dp),
                                            ).clickable {
                                                sheetTab = tab
                                            }.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color =
                                            if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    when (sheetTab) {
                        WearSheetTab.CONTROLS -> {
                            item {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                                            .clickable(enabled = phoneOffloadEnabled) {
                                                watchOnlyOverride = !watchOnlyOverride
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (offloadActive) Icons.Filled.PhoneAndroid else Icons.Filled.Watch,
                                        contentDescription = "Offload mode",
                                        tint = if (offloadActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text =
                                            when {
                                                offloadActive -> "Offload mode: Phone"
                                                phoneOffloadEnabled -> "Offload mode: Watch override"
                                                else -> "Offload mode: Off"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle) }
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Shuffle,
                                            contentDescription = "Shuffle",
                                            tint = if (controlState.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Repeat) }
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        val (icon, tint) =
                                            when (controlState.repeatState) {
                                                RepeatState.None -> Icons.Filled.Repeat to MaterialTheme.colorScheme.onSurface
                                                RepeatState.All -> Icons.Filled.Repeat to MaterialTheme.colorScheme.primary
                                                RepeatState.One -> Icons.Filled.RepeatOne to MaterialTheme.colorScheme.primary
                                            }
                                        Icon(icon, contentDescription = "Repeat", tint = tint)
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            mediaPlayerHandler.toggleLike()
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            if (controlState.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = "Like",
                                            tint = if (controlState.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onOpenQueue()
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch {
                                                val track = detailTrack ?: return@launch
                                                if (canRemoveDownload) {
                                                    downloadHandler.removeDownload(track.videoId)
                                                    Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                                                } else if (canTriggerDownload) {
                                                    val thumb = track.thumbnails?.lastOrNull()?.url ?: "https://i.ytimg.com/vi/${track.videoId}/maxresdefault.jpg"
                                                    downloadHandler.downloadTrack(track.videoId, track.title, thumb)
                                                    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(40.dp),
                                        enabled = downloadEnabled,
                                    ) {
                                        Icon(
                                            Icons.Filled.Download,
                                            contentDescription = downloadContentDescription,
                                            tint = downloadIconTint,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onOpenVolumeSettings()
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume")
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch {
                                                val handedOff =
                                                    sendQueueHandoffToPhone(
                                                        context = context,
                                                        dataStoreManager = dataStoreManager,
                                                        queueTracks = queueTracks,
                                                        currentIndex = queueCurrentIndex,
                                                        playlistId = queueData?.playlistId,
                                                        playlistName = queueData?.playlistName,
                                                        playlistType = queueData?.playlistType?.name,
                                                    )
                                                if (handedOff && controlState.isPlaying) {
                                                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                                                }
                                                Toast
                                                    .makeText(
                                                        context,
                                                        if (handedOff) "Phone confirmed handoff" else "Phone handoff not confirmed",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                            }
                                        },
                                        modifier = Modifier.size(40.dp),
                                        enabled = queueTracks.isNotEmpty(),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Continue on phone")
                                    }
                                }
                            }
                        }

                        WearSheetTab.LYRICS -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    WearLyricsDisplayMode.values().forEach { mode ->
                                        val selected = mode == lyricsDisplayMode
                                        Box(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(
                                                        if (selected) {
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.66f)
                                                        },
                                                    ).clickable { lyricsDisplayMode = mode }
                                                    .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = mode.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color =
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                            when (val state = lyricsUiState) {
                                WearLyricsUiState.Loading -> {
                                    item { WearLoadingState(message = "Loading lyrics...") }
                                }

                                WearLyricsUiState.Empty -> {
                                    item {
                                        WearEmptyState(
                                            title = "Lyrics unavailable.",
                                            hint = "Try another track.",
                                        )
                                    }
                                }

                                is WearLyricsUiState.Ready -> {
                                    item {
                                        Text(
                                            text = "Source: ${state.source}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (displayedLyricsLines.isEmpty()) {
                                        item {
                                            WearEmptyState(
                                                title = "No lines for this mode.",
                                                hint = "Switch lyric mode above.",
                                            )
                                        }
                                    } else {
                                        items(displayedLyricsLines.size) { index ->
                                            val line = displayedLyricsLines[index]
                                            val isActive = index == activeLyricIndex
                                            Column(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(
                                                            if (isActive) {
                                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
                                                            } else {
                                                                Color.Transparent
                                                            },
                                                        ).clickable(enabled = hasSyncedLyrics && durationMs > 0L && line.startTimeMs >= 0L) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            scope.launch {
                                                                val seekPositionMs = line.startTimeMs.coerceIn(0L, durationMs)
                                                                val progress = seekPositionMs.toFloat() / durationMs.toFloat() * 100f
                                                                mediaPlayerHandler.onPlayerEvent(PlayerEvent.UpdateProgress(progress))
                                                            }
                                                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                                            ) {
                                                Text(
                                                    text = line.text,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color =
                                                        if (isActive) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        },
                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                )
                                                if (!line.translatedText.isNullOrBlank()) {
                                                    Text(
                                                        text = line.translatedText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                            if (isActive) {
                                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WearSheetTab.QUEUE -> {
                            if (queueTracks.isEmpty()) {
                                item {
                                    WearEmptyState(
                                        title = "Queue is empty.",
                                        hint = "Add songs from library or discover.",
                                    )
                                }
                            } else {
                                items(queueTracks.size) { index ->
                                    val queueTrack = queueTracks[index]
                                    val isCurrent = index == queueCurrentIndex
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isCurrent) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.34f)
                                                    },
                                                ).clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    mediaPlayerHandler.playMediaItemInMediaSource(index)
                                                }.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = queueTrack.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color =
                                                    if (isCurrent) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                            val artistLabel = queueTrack.artists?.joinToString { it.name }.orEmpty()
                                            if (artistLabel.isNotBlank()) {
                                                Text(
                                                    text = artistLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color =
                                                        if (isCurrent) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f)
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    scope.launch { mediaPlayerHandler.moveItemUp(index) }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(26.dp),
                                            ) {
                                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                                            }
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    scope.launch { mediaPlayerHandler.moveItemDown(index) }
                                                },
                                                enabled = index < queueTracks.lastIndex,
                                                modifier = Modifier.size(26.dp),
                                            ) {
                                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                                            }
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    mediaPlayerHandler.removeMediaItem(index)
                                                },
                                                modifier = Modifier.size(26.dp),
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!advancedExpanded) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .width(28.dp)
                            .height(4.dp)
                            .then(dragSheetModifier)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)),
                )
            }
        }
        return
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
                    text = "Now playing",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { Spacer(Modifier.height(6.dp)) }

        item {
            ArtworkThumb(
                title = title,
                artworkUrl = artworkUrl,
                onClick = if (detailTrack != null) ({ showDetails = true }) else null,
            )
        }

        item { Spacer(Modifier.height(2.dp)) }

        item {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (detailTrack != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier =
                    if (detailTrack != null) {
                        Modifier
                            .fillMaxWidth()
                            .clickable { showDetails = true }
                    } else {
                        Modifier.fillMaxWidth()
                    },
            )
        }
        if (artist.isNotBlank()) {
            item {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                text =
                    when {
                        controlState.isPlaying -> "Playing"
                        showLoadingUi -> "Connecting..."
                        else -> "Paused"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        if (durationMs > 0L) {
            item {
                ThinProgressBar(progress = progressFraction)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatDurationMs(effectiveProgressMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                    )
                    Text(
                        text = formatDurationMs(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                }
            }
        } else {
            item {
                Text(
                    text =
                        when {
                            retrySuggested -> "Stream timed out. Tap play to retry."
                            controlState.isPlaying -> "Buffering..."
                            else -> "Resolving stream..."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item { Spacer(Modifier.height(6.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            dispatchTransportWithOffload(WearCompanionBridge.REMOTE_PREVIOUS) {
                                mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
                            }
                        }
                    },
                    enabled = controlState.isPreviousAvailable,
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            dispatchTransportWithOffload(WearCompanionBridge.REMOTE_PLAY_PAUSE) {
                                if (!controlState.isPlaying && nowPlayingState.isNotEmpty() && (retrySuggested || showLoadingUi)) {
                                    mediaPlayerHandler.retryCurrentStream()
                                } else {
                                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                                }
                            }
                        }
                    },
                ) {
                    PlaybackControlIcon(
                        isPlaying = controlState.isPlaying,
                        isLoading = showLoadingUi && !controlState.isPlaying && !retrySuggested,
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            dispatchTransportWithOffload(WearCompanionBridge.REMOTE_NEXT) {
                                mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                            }
                        }
                    },
                    enabled = controlState.isNextAvailable,
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }
        }

        item { Spacer(Modifier.height(6.dp)) }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle) }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (controlState.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Repeat) }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        val (icon, tint) =
                            when (controlState.repeatState) {
                                RepeatState.None -> Icons.Filled.Repeat to MaterialTheme.colorScheme.onSurface
                                RepeatState.All -> Icons.Filled.Repeat to MaterialTheme.colorScheme.primary
                                RepeatState.One -> Icons.Filled.RepeatOne to MaterialTheme.colorScheme.primary
                            }
                        Icon(icon, contentDescription = "Repeat", tint = tint)
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mediaPlayerHandler.toggleLike()
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (controlState.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (controlState.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(0.88f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                val track = detailTrack ?: return@launch
                                if (canRemoveDownload) {
                                    downloadHandler.removeDownload(track.videoId)
                                    Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                                } else if (canTriggerDownload) {
                                    val thumb = track.thumbnails?.lastOrNull()?.url ?: "https://i.ytimg.com/vi/${track.videoId}/maxresdefault.jpg"
                                    downloadHandler.downloadTrack(track.videoId, track.title, thumb)
                                    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        enabled = downloadEnabled,
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = downloadContentDescription,
                            tint = downloadIconTint,
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenVolumeSettings()
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume")
                    }
                    IconButton(
                        onClick = {
                            val nextOverride = !watchOnlyOverride
                            watchOnlyOverride = nextOverride
                            val nextOffloadActive = phoneOffloadEnabled && !nextOverride
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast
                                .makeText(
                                    context,
                                    if (nextOffloadActive) "Offload to phone enabled" else "Watch-only control enabled",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        },
                        modifier = Modifier.size(40.dp),
                        enabled = phoneOffloadEnabled,
                    ) {
                        Icon(
                            imageVector = if (offloadActive) Icons.Filled.PhoneAndroid else Icons.Filled.Watch,
                            contentDescription = "Toggle watch-only override",
                            tint = if (offloadActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularProgressPlayButton(
    progressFraction: Float,
    isPlaying: Boolean,
    isLoading: Boolean,
    progressColor: Color,
    onClick: () -> Unit,
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
    val buttonSize = 42.dp
    val ringStroke = 2.5f
    val outerSize = 52.dp
    Box(
        modifier = Modifier.size(outerSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = ringStroke.dp.toPx()
            drawCircle(
                color = trackColor,
                style = Stroke(width = stroke),
            )
            if (progressFraction > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = (progressFraction.coerceIn(0f, 1f) * 360f),
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .size(buttonSize)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f))
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PlaybackControlIcon(
                isPlaying = isPlaying,
                isLoading = isLoading,
            )
        }
    }
}

@Composable
private fun ArtworkThumb(
    title: String,
    artworkUrl: String?,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(86.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        val contentModifier =
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .then(contentModifier),
        contentAlignment = Alignment.Center,
        ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(86.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
    }
}

private enum class WearSheetTab(
    val label: String,
) {
    CONTROLS("Controls"),
    LYRICS("Lyrics"),
    QUEUE("Queue"),
}

private const val LYRICS_LIST_PREFIX_ITEMS = 4

private enum class WearLyricsDisplayMode(
    val label: String,
) {
    ORIGINAL("Original"),
    TRANSLATED("Translated"),
    BOTH("Both"),
}

private sealed interface WearLyricsUiState {
    data object Loading : WearLyricsUiState

    data object Empty : WearLyricsUiState

    data class Ready(
        val source: String,
        val originalLines: List<WearTimedLyricLine>,
        val translatedLines: List<WearTimedLyricLine>,
    ) : WearLyricsUiState
}

private data class WearLyricsResult(
    val source: String,
    val lyrics: Lyrics,
    val translatedLyrics: Lyrics? = null,
)

private data class WearTimedLyricLine(
    val startTimeMs: Long,
    val text: String,
)

private data class WearDisplayedLyricLine(
    val startTimeMs: Long,
    val text: String,
    val translatedText: String? = null,
)

private suspend fun loadWearLyricsState(
    lyricsCanvasRepository: LyricsCanvasRepository,
    dataStoreManager: DataStoreManager,
    mediaId: String,
    title: String,
    artist: String,
    durationSeconds: Int,
    preferOfflineFirst: Boolean,
): WearLyricsUiState {
    val provider = dataStoreManager.lyricsProvider.first()
    val translationLanguage = dataStoreManager.translationLanguage.first().ifBlank { "en" }.take(2)
    val savedOriginalLines = lyricsCanvasRepository.getSavedLyrics(mediaId).first()?.lines.toTimedLyricsLines()
    val savedTranslatedLines =
        lyricsCanvasRepository
            .getSavedTranslatedLyrics(mediaId, translationLanguage).first()
            ?.lines
            .toTimedLyricsLines()
    if (preferOfflineFirst && savedOriginalLines.isNotEmpty()) {
        return WearLyricsUiState.Ready(
            source = "Offline pinned",
            originalLines = savedOriginalLines,
            translatedLines = savedTranslatedLines,
        )
    }

    suspend fun toReadyState(result: WearLyricsResult): WearLyricsUiState {
        persistLyricsForOffline(
            lyricsCanvasRepository = lyricsCanvasRepository,
            mediaId = mediaId,
            translationLanguage = translationLanguage,
            result = result,
        )
        val originalLines = result.lyrics.lines.toTimedLyricsLines()
        val translatedFromResult = result.translatedLyrics?.lines.toTimedLyricsLines()
        return WearLyricsUiState.Ready(
            source = result.source,
            originalLines = originalLines,
            translatedLines = if (!translatedFromResult.isNullOrEmpty()) translatedFromResult else savedTranslatedLines,
        )
    }

    val providerResult =
        fetchLyricsByProvider(
            lyricsCanvasRepository = lyricsCanvasRepository,
            dataStoreManager = dataStoreManager,
            provider = provider,
            mediaId = mediaId,
            title = title,
            artist = artist,
            durationSeconds = durationSeconds,
            translationLanguage = translationLanguage,
        )
    if (providerResult != null) return toReadyState(providerResult)

    val fallbackOrder =
        listOf(
            DataStoreManager.SIMPMUSIC,
            DataStoreManager.LRCLIB,
            DataStoreManager.YOUTUBE,
        ).filter { it != provider }

    for (fallback in fallbackOrder) {
        val fallbackResult =
            fetchLyricsByProvider(
                lyricsCanvasRepository = lyricsCanvasRepository,
                dataStoreManager = dataStoreManager,
                provider = fallback,
                mediaId = mediaId,
                title = title,
                artist = artist,
                durationSeconds = durationSeconds,
                translationLanguage = translationLanguage,
            )
        if (fallbackResult != null) return toReadyState(fallbackResult)
    }

    if (savedOriginalLines.isNotEmpty()) {
        return WearLyricsUiState.Ready(
            source = "Offline",
            originalLines = savedOriginalLines,
            translatedLines = savedTranslatedLines,
        )
    }
    return WearLyricsUiState.Empty
}

private suspend fun fetchLyricsByProvider(
    lyricsCanvasRepository: LyricsCanvasRepository,
    dataStoreManager: DataStoreManager,
    provider: String,
    mediaId: String,
    title: String,
    artist: String,
    durationSeconds: Int,
    translationLanguage: String,
): WearLyricsResult? {
    val safeArtist = artist.ifBlank { "Unknown artist" }
    val safeDuration = durationSeconds.takeIf { it > 0 }
    return when (provider) {
        DataStoreManager.SIMPMUSIC -> {
            val resource = lyricsCanvasRepository.getSimpMusicLyrics(mediaId).first()
            val lyrics = (resource as? Resource.Success)?.data
            if (lyrics?.lines.isNullOrEmpty()) {
                null
            } else {
                val translatedResource = lyricsCanvasRepository.getSimpMusicTranslatedLyrics(mediaId, translationLanguage).first()
                val translatedLyrics = (translatedResource as? Resource.Success)?.data
                WearLyricsResult(
                    source = "SimpMusic",
                    lyrics = lyrics,
                    translatedLyrics = translatedLyrics,
                )
            }
        }

        DataStoreManager.LRCLIB -> {
            val resource =
                lyricsCanvasRepository
                    .getLrclibLyricsData(
                        sartist = safeArtist,
                        strack = title,
                        duration = safeDuration,
                    ).first()
            val lyrics = (resource as? Resource.Success)?.data
            if (lyrics?.lines.isNullOrEmpty()) {
                null
            } else {
                val translatedLyrics =
                    (lyricsCanvasRepository.getAITranslationLyrics(lyrics, translationLanguage).first() as? Resource.Success)?.data
                WearLyricsResult(
                    source = "LrcLib",
                    lyrics = lyrics,
                    translatedLyrics = translatedLyrics,
                )
            }
        }

        DataStoreManager.YOUTUBE -> {
            val language = dataStoreManager.youtubeSubtitleLanguage.first()
            val resource = lyricsCanvasRepository.getYouTubeCaption(language, mediaId).first()
            val payload = (resource as? Resource.Success)?.data
            val lyrics = payload?.first
            if (lyrics?.lines.isNullOrEmpty()) {
                null
            } else {
                WearLyricsResult(
                    source = "YouTube",
                    lyrics = lyrics,
                    translatedLyrics = payload.second,
                )
            }
        }

        else -> null
    }
}

private suspend fun persistLyricsForOffline(
    lyricsCanvasRepository: LyricsCanvasRepository,
    mediaId: String,
    translationLanguage: String,
    result: WearLyricsResult,
) {
    if (!result.lyrics.lines.isNullOrEmpty()) {
        lyricsCanvasRepository.insertLyrics(
            LyricsEntity(
                videoId = mediaId,
                error = false,
                lines = result.lyrics.lines,
                syncType = result.lyrics.syncType,
            ),
        )
    }
    val translatedLyrics = result.translatedLyrics
    if (!translatedLyrics?.lines.isNullOrEmpty()) {
        lyricsCanvasRepository.insertTranslatedLyrics(
            TranslatedLyricsEntity(
                videoId = mediaId,
                language = translationLanguage,
                error = false,
                lines = translatedLyrics.lines,
                syncType = translatedLyrics.syncType,
            ),
        )
    }
}

private fun buildDisplayedLyricsLines(
    mode: WearLyricsDisplayMode,
    original: List<WearTimedLyricLine>,
    translated: List<WearTimedLyricLine>,
): List<WearDisplayedLyricLine> =
    when (mode) {
        WearLyricsDisplayMode.ORIGINAL -> {
            original.map { line ->
                WearDisplayedLyricLine(
                    startTimeMs = line.startTimeMs,
                    text = line.text,
                )
            }
        }

        WearLyricsDisplayMode.TRANSLATED -> {
            (if (translated.isNotEmpty()) translated else original).map { line ->
                WearDisplayedLyricLine(
                    startTimeMs = line.startTimeMs,
                    text = line.text,
                )
            }
        }

        WearLyricsDisplayMode.BOTH -> {
            if (translated.isEmpty()) {
                original.map { line ->
                    WearDisplayedLyricLine(
                        startTimeMs = line.startTimeMs,
                        text = line.text,
                    )
                }
            } else {
                val maxLines = max(original.size, translated.size)
                buildList(maxLines) {
                    for (index in 0 until maxLines) {
                        val originalLine = original.getOrNull(index)
                        val translatedLine = translated.getOrNull(index)
                        val primaryText = originalLine?.text ?: translatedLine?.text ?: continue
                        add(
                            WearDisplayedLyricLine(
                                startTimeMs = originalLine?.startTimeMs ?: translatedLine?.startTimeMs ?: -1L,
                                text = primaryText,
                                translatedText = translatedLine?.text?.takeIf { it != primaryText },
                            ),
                        )
                    }
                }
            }
        }
    }

private fun findActiveLyricIndex(
    lines: List<WearDisplayedLyricLine>,
    progressMs: Long,
): Int =
    lines
        .indexOfLast { line ->
            line.startTimeMs >= 0L && line.startTimeMs <= progressMs + 250L
        }.takeIf { it >= 0 } ?: 0

private suspend fun centerLyricLineInSheet(
    listState: LazyListState,
    lyricLineIndex: Int,
) {
    val targetIndex = lyricLineIndex + LYRICS_LIST_PREFIX_ITEMS
    val initialVisibleInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
    if (initialVisibleInfo == null) {
        listState.animateScrollToItem(targetIndex)
    }
    val targetInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex } ?: return
    val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
    val itemCenter = targetInfo.offset + (targetInfo.size / 2)
    val delta = (itemCenter - viewportCenter).toFloat()
    if (abs(delta) > 2f) {
        listState.animateScrollBy(delta)
    }
}

private fun List<Line>?.toTimedLyricsLines(): List<WearTimedLyricLine> =
    this
        .orEmpty()
        .flatMap { line ->
            val startTime = line.startTimeMs.toLongOrNull() ?: -1L
            line.words
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { words ->
                    WearTimedLyricLine(
                        startTimeMs = startTime,
                        text = words,
                    )
                }.toList()
        }

private suspend fun sendQueueHandoffToPhone(
    context: Context,
    dataStoreManager: DataStoreManager,
    queueTracks: List<Track>,
    currentIndex: Int,
    playlistId: String?,
    playlistName: String?,
    playlistType: String?,
): Boolean {
    if (queueTracks.isEmpty()) return false
    val nodeId = getConnectedPhoneNodeId(context) ?: return false
    val requestId = UUID.randomUUID().toString()

    val tracksJson =
        JSONArray().apply {
            queueTracks.forEach { track ->
                put(track.toCompanionJson())
            }
        }
    val payload =
        JSONObject()
            .put("command", WearCompanionBridge.REMOTE_HANDOFF_QUEUE_TO_PHONE)
            .put("tracks", tracksJson.toString())
            .put("index", currentIndex.coerceIn(0, queueTracks.lastIndex))
            .put("playlistId", playlistId.orEmpty())
            .put("playlistName", playlistName.orEmpty())
            .put("playlistType", playlistType.orEmpty())
    val envelope =
        JSONObject()
            .put("requestId", requestId)
            .put("action", WearCompanionBridge.ACTION_REMOTE)
            .put("payload", payload)
            .toString()
            .toByteArray(Charsets.UTF_8)
    val delivered = sendCompanionEnvelopeToPhone(context, nodeId, envelope)
    if (!delivered) return false
    return awaitCompanionRemoteAck(
        dataStoreManager = dataStoreManager,
        requestId = requestId,
    )
}

private suspend fun sendRemoteCommandToPhone(
    context: Context,
    command: String,
): Boolean {
    val nodeId = getConnectedPhoneNodeId(context) ?: return false
    val envelope =
        JSONObject()
            .put("requestId", UUID.randomUUID().toString())
            .put("action", WearCompanionBridge.ACTION_REMOTE)
            .put(
                "payload",
                JSONObject().put("command", command),
            ).toString()
            .toByteArray(Charsets.UTF_8)
    return sendCompanionEnvelopeToPhone(context, nodeId, envelope)
}

private suspend fun getConnectedPhoneNodeId(context: Context): String? {
    val nodeClient = Wearable.getNodeClient(context.applicationContext)
    val nodes =
        suspendCancellableCoroutine<List<com.google.android.gms.wearable.Node>> { continuation ->
            nodeClient.connectedNodes
                .addOnSuccessListener { connectedNodes ->
                    if (continuation.isActive) continuation.resume(connectedNodes)
                }.addOnFailureListener {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        }
    return nodes.firstOrNull()?.id
}

private suspend fun sendCompanionEnvelopeToPhone(
    context: Context,
    nodeId: String,
    envelope: ByteArray,
): Boolean {
    val messageClient = Wearable.getMessageClient(context.applicationContext)
    return suspendCancellableCoroutine { continuation ->
        messageClient.sendMessage(nodeId, WearCompanionBridge.PATH_REQUEST, envelope)
            .addOnSuccessListener {
                if (continuation.isActive) continuation.resume(true)
            }.addOnFailureListener {
                if (continuation.isActive) continuation.resume(false)
            }
    }
}

private suspend fun awaitCompanionRemoteAck(
    dataStoreManager: DataStoreManager,
    requestId: String,
    timeoutMs: Long = 6_000L,
): Boolean {
    val acknowledged =
        withTimeoutOrNull(timeoutMs) {
            var ack: Boolean? = null
            while (ack == null) {
                val raw = dataStoreManager.getString(WearCompanionBridge.KEY_LAST_RESPONSE).first().orEmpty()
                val response = runCatching { JSONObject(raw) }.getOrNull()
                if (response?.optString("requestId") == requestId &&
                    response.optString("action") == WearCompanionBridge.ACTION_REMOTE
                ) {
                    ack = response.optBoolean("ok", false)
                } else {
                    delay(200L)
                }
            }
            ack
        }
    return acknowledged ?: false
}

private fun Track.toCompanionJson(): JSONObject {
    val artistsArray =
        JSONArray().apply {
            artists.orEmpty().forEach { artist ->
                put(
                    JSONObject()
                        .put("id", artist.id.orEmpty())
                        .put("name", artist.name),
                )
            }
        }
    val thumbnailsArray =
        JSONArray().apply {
            thumbnails.orEmpty().forEach { thumb ->
                put(
                    JSONObject()
                        .put("url", thumb.url)
                        .put("width", thumb.width)
                        .put("height", thumb.height),
                )
            }
        }
    val albumJson =
        album?.let {
            JSONObject()
                .put("id", it.id)
                .put("name", it.name)
        }

    return JSONObject()
        .put("title", title)
        .put("videoId", videoId)
        .put("duration", duration.orEmpty())
        .put("durationSeconds", durationSeconds ?: 0)
        .put("isAvailable", isAvailable)
        .put("isExplicit", isExplicit)
        .put("likeStatus", likeStatus.orEmpty())
        .put("videoType", videoType.orEmpty())
        .put("category", category.orEmpty())
        .put("resultType", resultType.orEmpty())
        .put("year", year.orEmpty())
        .put("artists", artistsArray)
        .put("thumbnails", thumbnailsArray)
        .apply {
            if (albumJson != null) put("album", albumJson)
        }
}

private suspend fun loadArtworkAccentColor(
    context: Context,
    artworkUrl: String,
): Color? =
    withContext(Dispatchers.IO) {
        runCatching {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()
            val result = context.imageLoader.execute(request)
            if (result is ErrorResult) return@runCatching null
            val bitmap = result.image?.toBitmap() ?: return@runCatching null
            deriveArtworkAccentColor(bitmap)
        }.getOrNull()
    }

private fun deriveArtworkAccentColor(bitmap: Bitmap): Color {
    val xStep = max(1, bitmap.width / 18)
    val yStep = max(1, bitmap.height / 18)
    var redTotal = 0L
    var greenTotal = 0L
    var blueTotal = 0L
    var samples = 0

    for (y in 0 until bitmap.height step yStep) {
        for (x in 0 until bitmap.width step xStep) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = AndroidColor.alpha(pixel)
            if (alpha < 180) continue

            val red = AndroidColor.red(pixel)
            val green = AndroidColor.green(pixel)
            val blue = AndroidColor.blue(pixel)
            val maxChannel = max(red, max(green, blue))
            val minChannel = min(red, min(green, blue))
            val saturation = if (maxChannel == 0) 0f else (maxChannel - minChannel).toFloat() / maxChannel.toFloat()
            val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
            if (saturation < 0.22f || luminance < 0.14f || luminance > 0.82f) continue

            redTotal += red
            greenTotal += green
            blueTotal += blue
            samples++
        }
    }

    if (samples == 0) {
        val centerPixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        return Color(centerPixel)
    }

    val red = (redTotal / samples).toInt().coerceIn(0, 255)
    val green = (greenTotal / samples).toInt().coerceIn(0, 255)
    val blue = (blueTotal / samples).toInt().coerceIn(0, 255)
    return Color(
        red = red / 255f,
        green = green / 255f,
        blue = blue / 255f,
        alpha = 1f,
    )
}
