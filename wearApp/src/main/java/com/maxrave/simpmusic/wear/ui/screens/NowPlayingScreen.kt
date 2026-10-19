package com.maxrave.simpmusic.wear.ui.screens

import android.content.Context
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import com.maxrave.domain.mediaservice.handler.SimpleMediaState
import com.maxrave.domain.repository.LyricsCanvasRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.wear.ui.components.rotaryVolume
import com.maxrave.simpmusic.wear.ui.components.WatchPlayerFace
import com.maxrave.simpmusic.wear.ui.components.PlayerDrawer
import com.maxrave.simpmusic.wear.ui.components.PlayerDrawerState
import com.maxrave.simpmusic.wear.ui.components.PlayerControls
import com.maxrave.simpmusic.wear.ui.util.openWatchAudioOutput
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.WearLoadingState
import com.maxrave.simpmusic.wear.ui.components.WearScreenHeader
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CancellationException
import com.maxrave.simpmusic.wear.ui.optionalValue
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
    val playerState = mediaPlayerHandler.player.playbackState
    val isLoading = playerState == com.maxrave.domain.data.player.PlayerConstants.STATE_BUFFERING ||
        (playerState == com.maxrave.domain.data.player.PlayerConstants.STATE_IDLE && !controlState.isPlaying && simpleState is SimpleMediaState.Loading)
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

    if (!nowPlayingState.isNotEmpty()) {
        WearList {
            item { WearScreenHeader("Player", onBack = onBack) }
            item { WearEmptyState("Find your next favorite", "Pick a song in Discover or your library. Your player will appear here.") }
            item { FilledTonalButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Browse music") } }
        }
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
    val progressLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(nowPlayingState.mediaItem.mediaId, controlState.isPlaying, durationMs, wearBatterySaverEnabled, progressLifecycleOwner) {
        progressMs = rawProgressMs
        if (!controlState.isPlaying) return@LaunchedEffect
        progressLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (controlState.isPlaying) {
                val latest = mediaPlayerHandler.getProgress()
                progressMs =
                    if (durationMs > 0L) {
                        latest.coerceIn(0L, durationMs)
                    } else {
                        latest.coerceAtLeast(0L)
                    }
                delay(if (wearBatterySaverEnabled) 1000L else 250L)
            }
        }
    }
    LaunchedEffect(rawProgressMs, controlState.isPlaying) {
        if (!controlState.isPlaying) progressMs = rawProgressMs
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
    val currentDownloadState = detailTrack?.videoId?.let { downloadTaskState[it] } ?: DownloadState.STATE_NOT_DOWNLOADED
    val canTriggerDownload = detailTrack != null && currentDownloadState == DownloadState.STATE_NOT_DOWNLOADED
    val canRemoveDownload = detailTrack != null && currentDownloadState == DownloadState.STATE_DOWNLOADED
    val downloadEnabled = canTriggerDownload || canRemoveDownload
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
    LaunchedEffect(artworkUrl, playerStyle, nowPlayingState.mediaItem.mediaId, defaultImmersiveTint, wearBatterySaverEnabled) {
        immersiveTint =
            if (playerStyle == WEAR_PLAYER_STYLE_IMMERSIVE && !wearBatterySaverEnabled && !artworkUrl.isNullOrBlank()) {
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
    var lyricsRequested by remember(currentMediaId) { mutableStateOf(false) }
    var transportPending by remember { mutableStateOf(false) }
    var seekTarget by remember { mutableStateOf<String?>(null) }
    var lyricsUiState by remember(currentMediaId) { mutableStateOf<WearLyricsUiState>(WearLyricsUiState.Loading) }
    LaunchedEffect(currentMediaId, lyricsRequested, title, artist, durationSeconds, currentDownloadState) {
        if (currentMediaId.isBlank() || !lyricsRequested) {
            lyricsUiState = WearLyricsUiState.Empty
            return@LaunchedEffect
        }
        lyricsUiState = WearLyricsUiState.Loading
        lyricsUiState = try {
            loadWearLyricsState(
                lyricsCanvasRepository = lyricsCanvasRepository,
                dataStoreManager = dataStoreManager,
                mediaId = currentMediaId,
                title = title,
                artist = artist,
                durationSeconds = durationSeconds,
                preferOfflineFirst = currentDownloadState == DownloadState.STATE_DOWNLOADED,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WearLyricsUiState.Empty
        }
    }
    LaunchedEffect(currentMediaId) { if (seekTarget != currentMediaId) seekTarget = null }
    if (seekTarget != null && durationMs > 0) {
        val targetId = seekTarget
        SeekScreen(effectiveProgressMs, durationMs, onApply = { position ->
            scope.launch {
                val currentDuration = mediaPlayerHandler.getPlayerDuration()
                if (mediaPlayerHandler.player.currentMediaItem?.mediaId == targetId && currentDuration > 0) {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.UpdateProgress(position.coerceIn(0, currentDuration).toFloat() / currentDuration * 100f))
                }
                seekTarget = null
            }
        }, onCancel = { seekTarget = null })
        return
    }

    suspend fun dispatchTransportWithOffload(
        command: String,
        fallback: suspend () -> Unit,
    ): Boolean {
        if (transportPending) return false
        transportPending = true
        try {
        if (!offloadActive) {
            fallback()
            return false
        }
        val sent =
            sendRemoteCommandToPhone(
                context = context,
                dataStoreManager = dataStoreManager,
                command = command,
            )
        if (sent) return true
        fallback()
        Toast.makeText(context, "Phone not reachable, using watch playback", Toast.LENGTH_SHORT).show()
        return false
        } finally { transportPending = false }
    }

    run {
        var volumeFeedback by remember { mutableStateOf<Float?>(null) }
        var volumeFeedbackAt by remember { mutableLongStateOf(0L) }
        LaunchedEffect(volumeFeedbackAt) {
            if (volumeFeedbackAt > 0L) { delay(1500); volumeFeedback = null }
        }
        val drawer = rememberSaveable(nowPlayingState.mediaItem.mediaId, saver = PlayerDrawerState.Saver) { PlayerDrawerState() }
        val drawerClosed by remember(drawer) { derivedStateOf { drawer.fraction == 0f && !drawer.dragging } }
        val advancedExpanded = drawer.expanded
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
            // A bright cover must not wash out the player labels and transport controls.
            lerp(Color.Black, immersiveTint, 0.08f).copy(alpha = 0.58f)
        val immersiveSheetColor =
            lerp(MaterialTheme.colorScheme.surfaceContainerHigh, immersiveTint, 0.30f).copy(alpha = 0.96f)

        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            if (playerStyle == WEAR_PLAYER_STYLE_IMMERSIVE && !artworkUrl.isNullOrBlank()) {
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

            PlayerDrawer(drawer, expandedSheetListState, immersiveSheetColor, player = { dragSheetModifier ->
            WatchPlayerFace(
                title = title, artist = artist, artwork = artworkUrl, immersive = playerStyle == WEAR_PLAYER_STYLE_IMMERSIVE,
                isPlaying = controlState.isPlaying, loading = showLoadingUi && !controlState.isPlaying && !retrySuggested,
                status = when {
                    offloadActive -> "Phone controls"
                    retrySuggested -> "Taking longer · Tap play to retry"
                    showLoadingUi -> if (durationMs > 0) "Buffering…" else "Finding stream…"
                    currentDownloadState == DownloadState.STATE_DOWNLOADED -> "On watch · Downloaded"
                    else -> "On watch"
                }, progress = progressFraction,
                time = if (durationMs > 0) "${formatDurationMs(effectiveProgressMs)} / ${formatDurationMs(durationMs)}" else "— / —",
                canPrevious = controlState.isPreviousAvailable && !transportPending, canNext = controlState.isNextAvailable && !transportPending,
                canToggle = !transportPending && (!showLoadingUi || retrySuggested || controlState.isPlaying),
                canSeek = durationMs > 0 && !offloadActive,
                onPrevious = { scope.launch { dispatchTransportWithOffload(WearCompanionBridge.REMOTE_PREVIOUS) { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous) } } },
                onToggle = { scope.launch { dispatchTransportWithOffload(WearCompanionBridge.REMOTE_PLAY_PAUSE) {
                    if (!controlState.isPlaying && retrySuggested) mediaPlayerHandler.retryCurrentStream()
                    else mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                } } },
                onNext = { scope.launch { dispatchTransportWithOffload(WearCompanionBridge.REMOTE_NEXT) { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next) } } },
                onSeek = { seekTarget = currentMediaId }, onQueue = onOpenQueue,
                onMore = { drawer.moveTo(scope, true) },
                isLiked = controlState.isLiked,
                onLike = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); mediaPlayerHandler.toggleLike() },
                onOutput = { context.openWatchAudioOutput() }, onVolume = onOpenVolumeSettings,
                canAdjustOutput = !offloadActive, drawerModifier = dragSheetModifier,
                volumeFraction = volumeFeedback,
                modifier = Modifier.rotaryVolume(drawerClosed && !offloadActive && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 190) { current, maximum ->
                    volumeFeedback = current.toFloat() / maximum
                    volumeFeedbackAt = SystemClock.elapsedRealtime()
                },
            )
            }) {
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
                                            ).clickable(role = Role.Tab) {
                                                sheetTab = tab
                                                if (tab == WearSheetTab.LYRICS) lyricsRequested = true
                                            }.semantics { this.selected = selected }.height(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(when (tab) {
                                        WearSheetTab.CONTROLS -> Icons.Filled.Tune
                                        WearSheetTab.LYRICS -> Icons.Filled.Lyrics
                                        WearSheetTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
                                    }, tab.label, Modifier.size(24.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                    when (sheetTab) {
                        WearSheetTab.CONTROLS -> {
                            item {
                                PlayerControls(controlState.isShuffle, controlState.repeatState, canRemoveDownload,
                                    downloadEnabled, downloadContentDescription, !offloadActive, queueTracks.isNotEmpty(),
                                    onShuffle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle) }
                                    }, onRepeat = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Repeat) }
                                    }, onQueue = onOpenQueue, onDownload = {
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
                                    }, onVolume = onOpenVolumeSettings, onHandoff = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch {
                                            val handedOff = sendQueueHandoffToPhone(context, dataStoreManager, queueTracks,
                                                queueCurrentIndex, queueData?.playlistId, queueData?.playlistName, queueData?.playlistType?.name)
                                            if (handedOff && controlState.isPlaying) mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                                            Toast.makeText(context, if (handedOff) "Phone confirmed handoff" else "Phone handoff not confirmed", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                            }
                            if (phoneOffloadEnabled) item {
                                Text(if (offloadActive) "Controls on phone" else "Controls on watch",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable {
                                        watchOnlyOverride = !watchOnlyOverride
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }.padding(vertical = 8.dp))
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
                                                    ).clickable(role = Role.Tab) { lyricsDisplayMode = mode }
                                                    .semantics { this.selected = selected }.height(48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(when (mode) {
                                                WearLyricsDisplayMode.ORIGINAL -> Icons.Filled.Notes
                                                WearLyricsDisplayMode.TRANSLATED -> Icons.Filled.Translate
                                                WearLyricsDisplayMode.BOTH -> Icons.Filled.ViewAgenda
                                            }, if (mode == WearLyricsDisplayMode.BOTH) "Original and translated lyrics"
                                                else "${mode.label} lyrics", Modifier.size(24.dp), tint = Color.White)
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
                                    Column(
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
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
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
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    scope.launch { mediaPlayerHandler.moveItemUp(index) }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(48.dp),
                                            ) {
                                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                                            }
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    scope.launch { mediaPlayerHandler.moveItemDown(index) }
                                                },
                                                enabled = index < queueTracks.lastIndex,
                                                modifier = Modifier.size(48.dp),
                                            ) {
                                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                                            }
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    mediaPlayerHandler.removeMediaItem(index)
                                                },
                                                modifier = Modifier.size(48.dp),
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
        return
    }

}

private enum class WearSheetTab(
    val label: String,
) {
    CONTROLS("Controls"),
    LYRICS("Lyrics"),
    QUEUE("Queue"),
}

private const val LYRICS_LIST_PREFIX_ITEMS = 3

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
    val savedOriginalLines = lyricsCanvasRepository.getSavedLyrics(mediaId).firstOrNull()?.lines.toTimedLyricsLines()
    val savedTranslatedLines =
        lyricsCanvasRepository
            .getSavedTranslatedLyrics(mediaId, translationLanguage).firstOrNull()
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
            val resource = lyricsCanvasRepository.getSimpMusicLyrics(mediaId).optionalValue()
            val lyrics = (resource as? Resource.Success)?.data
            if (lyrics?.lines.isNullOrEmpty()) {
                null
            } else {
                val translatedResource = lyricsCanvasRepository.getSimpMusicTranslatedLyrics(mediaId, translationLanguage).optionalValue()
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
                    ).optionalValue()
            val lyrics = (resource as? Resource.Success)?.data
            if (lyrics?.lines.isNullOrEmpty()) {
                null
            } else {
                val translatedLyrics =
                    (lyricsCanvasRepository.getAITranslationLyrics(lyrics, translationLanguage).optionalValue() as? Resource.Success)?.data
                WearLyricsResult(
                    source = "LrcLib",
                    lyrics = lyrics,
                    translatedLyrics = translatedLyrics,
                )
            }
        }

        DataStoreManager.YOUTUBE -> {
            val language = dataStoreManager.youtubeSubtitleLanguage.first()
            val resource = lyricsCanvasRepository.getYouTubeCaption(language, mediaId).optionalValue()
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
    dataStoreManager: DataStoreManager,
    command: String,
): Boolean {
    val nodeId = getConnectedPhoneNodeId(context) ?: return false
    val requestId = UUID.randomUUID().toString()
    val envelope =
        JSONObject()
            .put("requestId", requestId)
            .put("action", WearCompanionBridge.ACTION_REMOTE)
            .put(
                "payload",
                JSONObject().put("command", command),
            ).toString()
            .toByteArray(Charsets.UTF_8)
    val delivered = sendCompanionEnvelopeToPhone(context, nodeId, envelope)
    if (!delivered) return false
    return awaitCompanionRemoteAck(
        dataStoreManager = dataStoreManager,
        requestId = requestId,
    )
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
