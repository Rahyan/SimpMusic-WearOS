package com.maxrave.simpmusic.viewModel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.maxrave.common.WearCompanionBridge
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.PodcastsEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.PodcastRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.coroutines.resume

data class WatchConnectionUiState(
    val connected: Boolean = false,
    val nodeNames: List<String> = emptyList(),
)

data class WatchSyncSelection(
    val songs: Boolean = true,
    val playlists: Boolean = true,
    val albums: Boolean = true,
    val artists: Boolean = true,
    val podcasts: Boolean = true,
    val downloads: Boolean = true,
)

data class WatchCompanionActionState(
    val inProgress: Boolean = false,
    val activeAction: String = "",
    val lastSuccess: String = "",
    val lastFailure: String = "",
    val retryAvailable: Boolean = false,
)

data class WatchAutoSyncPolicyUiState(
    val batterySaver: Boolean = false,
    val unmeteredOnly: Boolean = false,
)

class WatchCompanionViewModel(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val podcastRepository: PodcastRepository,
) : BaseViewModel(application) {
    private val appContext = application.applicationContext
    private val nodeClient by lazy { Wearable.getNodeClient(appContext) }
    private val messageClient by lazy { Wearable.getMessageClient(appContext) }

    private val _connectionState = MutableStateFlow(WatchConnectionUiState())
    val connectionState: StateFlow<WatchConnectionUiState> = _connectionState.asStateFlow()

    private val _syncSelection = MutableStateFlow(WatchSyncSelection())
    val syncSelection: StateFlow<WatchSyncSelection> = _syncSelection.asStateFlow()

    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()

    private val _diagnostics = MutableStateFlow("")
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    private val _logText = MutableStateFlow("")
    val logText: StateFlow<String> = _logText.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _actionState = MutableStateFlow(WatchCompanionActionState())
    val actionState: StateFlow<WatchCompanionActionState> = _actionState.asStateFlow()
    private val _autoSyncPolicy = MutableStateFlow(WatchAutoSyncPolicyUiState())
    val autoSyncPolicy: StateFlow<WatchAutoSyncPolicyUiState> = _autoSyncPolicy.asStateFlow()
    private val _autoSyncStatsSummary = MutableStateFlow("No auto-sync run yet.")
    val autoSyncStatsSummary: StateFlow<String> = _autoSyncStatsSummary.asStateFlow()
    private var lastFailedActionId: String? = null

    init {
        observeBridgeState()
        refreshConnection()
    }

    fun setSongsSync(enabled: Boolean) {
        _syncSelection.value = _syncSelection.value.copy(songs = enabled)
    }

    fun setPlaylistsSync(enabled: Boolean) {
        _syncSelection.value = _syncSelection.value.copy(playlists = enabled)
    }

    fun setAlbumsSync(enabled: Boolean) {
        _syncSelection.value = _syncSelection.value.copy(albums = enabled)
    }

    fun setArtistsSync(enabled: Boolean) {
        _syncSelection.value = _syncSelection.value.copy(artists = enabled)
    }

    fun setPodcastsSync(enabled: Boolean) {
        _syncSelection.value = _syncSelection.value.copy(podcasts = enabled)
    }

    fun setDownloadsSync(enabled: Boolean) {
        _syncSelection.value = _syncSelection.value.copy(downloads = enabled)
    }

    fun setAutoSyncBatterySaver(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.putString(
                WearCompanionBridge.KEY_AUTO_SYNC_BATTERY_SAVER,
                if (enabled) DataStoreManager.TRUE else DataStoreManager.FALSE,
            )
        }
    }

    fun setAutoSyncUnmeteredOnly(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.putString(
                WearCompanionBridge.KEY_AUTO_SYNC_UNMETERED_ONLY,
                if (enabled) DataStoreManager.TRUE else DataStoreManager.FALSE,
            )
        }
    }

    fun resetAutoSyncDeltaCache() {
        viewModelScope.launch {
            listOf(
                WearCompanionBridge.KEY_AUTO_SYNC_SIG_SESSION,
                WearCompanionBridge.KEY_AUTO_SYNC_SIG_SONGS,
                WearCompanionBridge.KEY_AUTO_SYNC_SIG_PLAYLISTS,
                WearCompanionBridge.KEY_AUTO_SYNC_SIG_ALBUMS,
                WearCompanionBridge.KEY_AUTO_SYNC_SIG_ARTISTS,
                WearCompanionBridge.KEY_AUTO_SYNC_SIG_PODCASTS,
                WearCompanionBridge.KEY_AUTO_SYNC_SIG_DOWNLOADS,
            ).forEach { key ->
                dataStoreManager.putString(key, "")
            }
            dataStoreManager.putString(WearCompanionBridge.KEY_AUTO_SYNC_LAST_STATS, "")
            makeToast("Auto-sync delta cache reset.")
        }
    }

    fun refreshConnection() {
        viewModelScope.launch {
            val nodes = getConnectedNodes()
            _connectionState.value =
                WatchConnectionUiState(
                    connected = nodes.isNotEmpty(),
                    nodeNames = nodes.map { it.displayName },
                )
        }
    }

    fun requestDiagnostics() {
        viewModelScope.launch {
            runTrackedAction(ACTION_ID_DIAGNOSTICS, "Request diagnostics") {
                val sent =
                    sendSimpleAction(
                        action = WearCompanionBridge.ACTION_STATUS,
                        payload = JSONObject(),
                    )
                if (sent) {
                    ActionOutcome(success = true, detail = "Watch diagnostics request delivered.", toast = "Diagnostics requested.")
                } else {
                    ActionOutcome(success = false, detail = "Watch is unreachable for diagnostics request.")
                }
            }
        }
    }

    fun syncSessionAndSpotify() {
        viewModelScope.launch {
            runTrackedAction(ACTION_ID_SESSION_SYNC, "Sync phone session + Spotify") {
                val cookie = dataStoreManager.cookie.first()
                if (cookie.isBlank()) {
                    ActionOutcome(
                        success = false,
                        detail = "No phone session cookie available.",
                        toast = "No phone session found. Sign in on phone first.",
                    )
                } else {
                    val payload =
                        JSONObject()
                            .put("cookie", cookie)
                            .put("spdc", dataStoreManager.spdc.first())
                            .put("spotifyClientToken", dataStoreManager.spotifyClientToken.first())
                            .put("spotifyClientTokenExpires", dataStoreManager.spotifyClientTokenExpires.first())
                            .put("spotifyPersonalToken", dataStoreManager.spotifyPersonalToken.first())
                            .put("spotifyPersonalTokenExpires", dataStoreManager.spotifyPersonalTokenExpires.first())
                            .put("spotifyLyrics", dataStoreManager.spotifyLyrics.first() == DataStoreManager.TRUE)
                            .put("spotifyCanvas", dataStoreManager.spotifyCanvas.first() == DataStoreManager.TRUE)

                    val sent = sendSimpleAction(WearCompanionBridge.ACTION_SYNC_SESSION, payload)
                    if (sent) {
                        ActionOutcome(success = true, detail = "Session + Spotify payload delivered.", toast = "Session sync sent to watch.")
                    } else {
                        ActionOutcome(success = false, detail = "Watch is unreachable for session sync.")
                    }
                }
            }
        }
    }

    fun syncSelectedData() {
        viewModelScope.launch {
            runTrackedAction(ACTION_ID_SELECTIVE_SYNC, "Sync selected library data") {
                val node = getPrimaryNode() ?: run {
                    refreshConnection()
                    return@runTrackedAction ActionOutcome(
                        success = false,
                        detail = "No connected watch for selective sync.",
                        toast = "No watch connected.",
                    )
                }

                _syncing.value = true
                try {
                    val selected = syncSelection.value
                    val requestId = UUID.randomUUID().toString()
                    var sentCount = 0
                    var attemptedCount = 0

                    if (selected.songs) {
                        songRepository.getLikedSongs().first().take(MAX_SYNC_ITEMS).forEach { song ->
                            attemptedCount++
                            if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_SONG, requestId, songToJson(song))) {
                                sentCount++
                            }
                        }
                    }
                    if (selected.playlists) {
                        playlistRepository.getLikedPlaylists().first().take(MAX_SYNC_ITEMS).forEach { playlist ->
                            attemptedCount++
                            if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_PLAYLIST, requestId, playlistToJson(playlist))) {
                                sentCount++
                            }
                        }
                    }
                    if (selected.albums) {
                        albumRepository.getLikedAlbums().first().take(MAX_SYNC_ITEMS).forEach { album ->
                            attemptedCount++
                            if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_ALBUM, requestId, albumToJson(album))) {
                                sentCount++
                            }
                        }
                    }
                    if (selected.artists) {
                        artistRepository.getFollowedArtists().first().take(MAX_SYNC_ITEMS).forEach { artist ->
                            attemptedCount++
                            if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_ARTIST, requestId, artistToJson(artist.channelId, artist.name, artist.thumbnails, artist.followed))) {
                                sentCount++
                            }
                        }
                    }
                    if (selected.podcasts) {
                        podcastRepository.getFavoritePodcasts().first().take(MAX_SYNC_ITEMS).forEach { podcast ->
                            attemptedCount++
                            if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_PODCAST, requestId, podcastToJson(podcast))) {
                                sentCount++
                            }
                        }
                    }
                    if (selected.downloads) {
                        val downloadPayload = buildDownloadPayload()
                        attemptedCount++
                        if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_DOWNLOAD, requestId, downloadPayload)) {
                            sentCount++
                        }
                    }

                    if (attemptedCount == 0) {
                        ActionOutcome(
                            success = false,
                            detail = "No sync categories selected.",
                            toast = "Select at least one category to sync.",
                        )
                    } else if (sentCount == attemptedCount) {
                        ActionOutcome(
                            success = true,
                            detail = "All payloads delivered ($sentCount/$attemptedCount).",
                            toast = "Selective sync sent.",
                        )
                    } else {
                        ActionOutcome(
                            success = false,
                            detail = "Only $sentCount/$attemptedCount payloads delivered. Retry suggested.",
                            toast = "Sync partial: $sentCount/$attemptedCount delivered.",
                        )
                    }
                } finally {
                    _syncing.value = false
                }
            }
        }
    }

    fun remotePlayPause() = remoteCommand(ACTION_ID_REMOTE_PLAY_PAUSE, "Remote Play/Pause", WearCompanionBridge.REMOTE_PLAY_PAUSE)

    fun remoteNext() = remoteCommand(ACTION_ID_REMOTE_NEXT, "Remote Next", WearCompanionBridge.REMOTE_NEXT)

    fun remotePrevious() = remoteCommand(ACTION_ID_REMOTE_PREVIOUS, "Remote Previous", WearCompanionBridge.REMOTE_PREVIOUS)

    fun remoteVolumeUp() = remoteCommand(ACTION_ID_REMOTE_VOLUME_UP, "Remote Volume +", WearCompanionBridge.REMOTE_VOLUME_UP)

    fun remoteVolumeDown() = remoteCommand(ACTION_ID_REMOTE_VOLUME_DOWN, "Remote Volume -", WearCompanionBridge.REMOTE_VOLUME_DOWN)

    fun handoffQueueToWatch() {
        viewModelScope.launch {
            runTrackedAction(ACTION_ID_HANDOFF, "Queue handoff to watch") {
                val queue = mediaPlayerHandler.queueData.value?.data
                val tracks =
                    when {
                        queue?.listTracks?.isNotEmpty() == true -> queue.listTracks.take(MAX_HANDOFF_TRACKS)
                        mediaPlayerHandler.nowPlayingState.value.track != null -> listOf(mediaPlayerHandler.nowPlayingState.value.track!!)
                        else -> emptyList()
                    }
                if (tracks.isEmpty()) {
                    ActionOutcome(
                        success = false,
                        detail = "Queue handoff skipped: queue is empty.",
                        toast = "No queue to hand off.",
                    )
                } else {
                    val index = mediaPlayerHandler.currentSongIndex.value.coerceIn(0, tracks.lastIndex)
                    val payload =
                        JSONObject()
                            .put("command", WearCompanionBridge.REMOTE_HANDOFF_QUEUE)
                            .put("tracks", Json.encodeToString(tracks))
                            .put("index", index)
                            .put("playlistId", queue?.playlistId ?: "")
                            .put("playlistName", queue?.playlistName ?: "")
                            .put("playlistType", queue?.playlistType?.name ?: "")

                    val sent = sendSimpleAction(WearCompanionBridge.ACTION_REMOTE, payload)
                    if (sent) {
                        ActionOutcome(
                            success = true,
                            detail = "Queue handoff sent (${tracks.size} tracks).",
                            toast = "Queue handoff sent to watch.",
                        )
                    } else {
                        ActionOutcome(success = false, detail = "Watch is unreachable for queue handoff.")
                    }
                }
            }
        }
    }

    fun retryLastFailedAction() {
        if (actionState.value.inProgress) return
        when (lastFailedActionId) {
            ACTION_ID_DIAGNOSTICS -> requestDiagnostics()
            ACTION_ID_SESSION_SYNC -> syncSessionAndSpotify()
            ACTION_ID_SELECTIVE_SYNC -> syncSelectedData()
            ACTION_ID_REMOTE_PLAY_PAUSE -> remotePlayPause()
            ACTION_ID_REMOTE_NEXT -> remoteNext()
            ACTION_ID_REMOTE_PREVIOUS -> remotePrevious()
            ACTION_ID_REMOTE_VOLUME_UP -> remoteVolumeUp()
            ACTION_ID_REMOTE_VOLUME_DOWN -> remoteVolumeDown()
            ACTION_ID_HANDOFF -> handoffQueueToWatch()
            null -> makeToast("No failed action to retry.")
            else -> makeToast("Retry is unavailable for the last action.")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            dataStoreManager.putString(WearCompanionBridge.KEY_LOG, "")
        }
    }

    private fun remoteCommand(
        actionId: String,
        label: String,
        command: String,
    ) {
        viewModelScope.launch {
            runTrackedAction(actionId, label) {
                val payload = JSONObject().put("command", command)
                val sent = sendSimpleAction(WearCompanionBridge.ACTION_REMOTE, payload)
                if (sent) {
                    ActionOutcome(success = true, detail = "Remote command sent: $command")
                } else {
                    ActionOutcome(success = false, detail = "Watch is unreachable for remote command: $command.")
                }
            }
        }
    }

    private suspend fun runTrackedAction(
        actionId: String,
        label: String,
        block: suspend () -> ActionOutcome,
    ) {
        _actionState.value =
            _actionState.value.copy(
                inProgress = true,
                activeAction = label,
                retryAvailable = false,
            )

        val outcome =
            runCatching { block() }.getOrElse { error ->
                ActionOutcome(
                    success = false,
                    detail = error.message ?: "Unexpected error.",
                    toast = "Action failed unexpectedly.",
                )
            }

        if (outcome.success) {
            lastFailedActionId = null
            _actionState.value =
                _actionState.value.copy(
                    inProgress = false,
                    activeAction = "",
                    lastSuccess = "${timestamp()} • ${outcome.detail}",
                    lastFailure = "",
                    retryAvailable = false,
                )
        } else {
            lastFailedActionId = actionId
            _actionState.value =
                _actionState.value.copy(
                    inProgress = false,
                    activeAction = "",
                    lastFailure = "${timestamp()} • ${outcome.detail}",
                    retryAvailable = true,
                )
        }

        appendLog("$label: ${if (outcome.success) "success" else "failure"} (${outcome.detail})")
        if (outcome.toast.isNotBlank()) {
            makeToast(outcome.toast)
        }
    }

    private suspend fun sendSimpleAction(
        action: String,
        payload: JSONObject,
    ): Boolean {
        val node = getPrimaryNode() ?: run {
            makeToast("No watch connected.")
            refreshConnection()
            return false
        }
        return sendActionToNode(node.id, action, UUID.randomUUID().toString(), payload)
    }

    private suspend fun sendActionToNode(
        nodeId: String,
        action: String,
        requestId: String,
        payload: JSONObject,
    ): Boolean {
        val body =
            JSONObject()
                .put("requestId", requestId)
                .put("action", action)
                .put("payload", payload)
                .toString()
                .toByteArray(Charsets.UTF_8)
        return sendMessage(nodeId, body)
    }

    private fun timestamp(): String = LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()

    private fun songToJson(song: SongEntity): JSONObject =
        JSONObject()
            .put("videoId", song.videoId)
            .put("title", song.title)
            .put("albumId", song.albumId ?: "")
            .put("albumName", song.albumName ?: "")
            .put("artistId", JSONArray(song.artistId ?: emptyList<String>()))
            .put("artistName", JSONArray(song.artistName ?: emptyList<String>()))
            .put("duration", song.duration)
            .put("durationSeconds", song.durationSeconds)
            .put("isAvailable", song.isAvailable)
            .put("isExplicit", song.isExplicit)
            .put("likeStatus", song.likeStatus)
            .put("thumbnails", song.thumbnails ?: "")
            .put("videoType", song.videoType)
            .put("category", song.category ?: "")
            .put("resultType", song.resultType ?: "")
            .put("liked", song.liked)
            .put("downloadState", song.downloadState)

    private fun playlistToJson(playlist: PlaylistEntity): JSONObject =
        JSONObject()
            .put("id", playlist.id)
            .put("author", playlist.author ?: "")
            .put("description", playlist.description)
            .put("duration", playlist.duration)
            .put("durationSeconds", playlist.durationSeconds)
            .put("privacy", playlist.privacy)
            .put("thumbnails", playlist.thumbnails)
            .put("title", playlist.title)
            .put("trackCount", playlist.trackCount)
            .put("tracks", JSONArray(playlist.tracks ?: emptyList<String>()))
            .put("year", playlist.year ?: "")
            .put("liked", playlist.liked)
            .put("downloadState", playlist.downloadState)

    private fun albumToJson(album: AlbumEntity): JSONObject =
        JSONObject()
            .put("browseId", album.browseId)
            .put("artistId", JSONArray(album.artistId ?: emptyList<String?>()))
            .put("artistName", JSONArray(album.artistName ?: emptyList<String>()))
            .put("audioPlaylistId", album.audioPlaylistId)
            .put("description", album.description)
            .put("duration", album.duration ?: "")
            .put("durationSeconds", album.durationSeconds)
            .put("thumbnails", album.thumbnails ?: "")
            .put("title", album.title)
            .put("trackCount", album.trackCount)
            .put("tracks", JSONArray(album.tracks ?: emptyList<String>()))
            .put("type", album.type)
            .put("year", album.year ?: "")
            .put("liked", album.liked)
            .put("downloadState", album.downloadState)

    private fun artistToJson(
        channelId: String,
        name: String,
        thumbnails: String?,
        followed: Boolean,
    ): JSONObject =
        JSONObject()
            .put("channelId", channelId)
            .put("name", name)
            .put("thumbnails", thumbnails ?: "")
            .put("followed", followed)

    private fun podcastToJson(podcast: PodcastsEntity): JSONObject =
        JSONObject()
            .put("podcastId", podcast.podcastId)
            .put("title", podcast.title)
            .put("authorId", podcast.authorId)
            .put("authorName", podcast.authorName)
            .put("authorThumbnail", podcast.authorThumbnail ?: "")
            .put("description", podcast.description ?: "")
            .put("thumbnail", podcast.thumbnail ?: "")
            .put("isFavorite", podcast.isFavorite)
            .put("listEpisodes", JSONArray(podcast.listEpisodes))

    private suspend fun buildDownloadPayload(): JSONObject {
        val downloadedSongs =
            (songRepository.getDownloadedSongs().first().orEmpty() + songRepository.getDownloadingSongs().first().orEmpty())
                .distinctBy { it.videoId }

        val downloadedCollections =
            (playlistRepository.getAllDownloadedPlaylist().first() + playlistRepository.getAllDownloadingPlaylist().first())
                .distinctBy { it.toString() }

        val playlistStates = JSONArray()
        val albumStates = JSONArray()
        downloadedCollections.forEach { item ->
            when (item) {
                is PlaylistEntity -> playlistStates.put(JSONObject().put("id", item.id).put("downloadState", item.downloadState))
                is AlbumEntity -> albumStates.put(JSONObject().put("browseId", item.browseId).put("downloadState", item.downloadState))
                else -> Unit
            }
        }

        val songStates = JSONArray()
        downloadedSongs.forEach { song ->
            songStates.put(JSONObject().put("videoId", song.videoId).put("downloadState", song.downloadState))
        }

        return JSONObject()
            .put("songs", songStates)
            .put("playlists", playlistStates)
            .put("albums", albumStates)
    }

    private fun observeBridgeState() {
        viewModelScope.launch {
            dataStoreManager.getString(WearCompanionBridge.KEY_LAST_RESPONSE).collect {
                _lastResponse.value = it.orEmpty()
            }
        }
        viewModelScope.launch {
            dataStoreManager.getString(WearCompanionBridge.KEY_LAST_DIAGNOSTICS).collect {
                _diagnostics.value = it.orEmpty()
            }
        }
        viewModelScope.launch {
            dataStoreManager.getString(WearCompanionBridge.KEY_LOG).collect {
                _logText.value = it.orEmpty()
            }
        }
        viewModelScope.launch {
            dataStoreManager.getString(WearCompanionBridge.KEY_AUTO_SYNC_BATTERY_SAVER).collect {
                _autoSyncPolicy.value =
                    _autoSyncPolicy.value.copy(
                        batterySaver = it.toBooleanFlag(),
                    )
            }
        }
        viewModelScope.launch {
            dataStoreManager.getString(WearCompanionBridge.KEY_AUTO_SYNC_UNMETERED_ONLY).collect {
                _autoSyncPolicy.value =
                    _autoSyncPolicy.value.copy(
                        unmeteredOnly = it.toBooleanFlag(),
                    )
            }
        }
        viewModelScope.launch {
            dataStoreManager.getString(WearCompanionBridge.KEY_AUTO_SYNC_LAST_STATS).collect {
                _autoSyncStatsSummary.value = formatAutoSyncStats(it)
            }
        }
    }

    private fun appendLog(message: String) {
        viewModelScope.launch {
            val current = dataStoreManager.getString(WearCompanionBridge.KEY_LOG).first().orEmpty()
            val timestamp = LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
            val appended =
                if (current.isBlank()) {
                    "[$timestamp] $message"
                } else {
                    "$current\n[$timestamp] $message"
                }
            dataStoreManager.putString(WearCompanionBridge.KEY_LOG, appended.takeLast(MAX_LOG_CHARS))
        }
    }

    private suspend fun getPrimaryNode(): Node? = getConnectedNodes().firstOrNull()

    private suspend fun getConnectedNodes(): List<Node> =
        suspendCancellableCoroutine { cont ->
            nodeClient.connectedNodes
                .addOnSuccessListener { nodes ->
                    if (cont.isActive) cont.resume(nodes)
                }.addOnFailureListener {
                    if (cont.isActive) cont.resume(emptyList())
                }
        }

    private suspend fun sendMessage(
        nodeId: String,
        payload: ByteArray,
    ): Boolean =
        suspendCancellableCoroutine { cont ->
            messageClient.sendMessage(nodeId, WearCompanionBridge.PATH_REQUEST, payload)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(true)
                }.addOnFailureListener {
                    if (cont.isActive) cont.resume(false)
                }
        }

    companion object {
        private const val ACTION_ID_DIAGNOSTICS = "diagnostics"
        private const val ACTION_ID_SESSION_SYNC = "session_sync"
        private const val ACTION_ID_SELECTIVE_SYNC = "selective_sync"
        private const val ACTION_ID_REMOTE_PLAY_PAUSE = "remote_play_pause"
        private const val ACTION_ID_REMOTE_NEXT = "remote_next"
        private const val ACTION_ID_REMOTE_PREVIOUS = "remote_previous"
        private const val ACTION_ID_REMOTE_VOLUME_UP = "remote_volume_up"
        private const val ACTION_ID_REMOTE_VOLUME_DOWN = "remote_volume_down"
        private const val ACTION_ID_HANDOFF = "handoff_queue"
        private const val MAX_SYNC_ITEMS = 120
        private const val MAX_HANDOFF_TRACKS = 80
        private const val MAX_LOG_CHARS = 16000
    }
}

private fun String?.toBooleanFlag(): Boolean = this == DataStoreManager.TRUE

private fun formatAutoSyncStats(raw: String?): String {
    if (raw.isNullOrBlank()) return "No auto-sync run yet."
    val stats = runCatching { JSONObject(raw) }.getOrNull() ?: return "Auto-sync stats unavailable."
    val status = stats.optString("status").ifBlank { "unknown" }
    val attempted = stats.optInt("attempted", 0)
    val sent = stats.optInt("sent", 0)
    val changed = stats.optInt("changedCategories", 0)
    val reason = stats.optString("reason").ifBlank { "n/a" }
    return "Status: $status • Delivered $sent/$attempted • Changed categories: $changed • Reason: $reason"
}

private data class ActionOutcome(
    val success: Boolean,
    val detail: String,
    val toast: String = "",
)
