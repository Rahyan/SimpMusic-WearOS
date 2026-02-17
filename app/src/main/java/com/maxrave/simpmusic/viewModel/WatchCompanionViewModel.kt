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
            val sent =
                sendSimpleAction(
                    action = WearCompanionBridge.ACTION_STATUS,
                    payload = JSONObject(),
                )
            if (sent) {
                appendLog("Diagnostics requested.")
            }
        }
    }

    fun syncSessionAndSpotify() {
        viewModelScope.launch {
            val cookie = dataStoreManager.cookie.first()
            if (cookie.isBlank()) {
                makeToast("No phone session found. Sign in on phone first.")
                appendLog("Session sync skipped: no cookie.")
                return@launch
            }

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
                makeToast("Session sync sent to watch.")
                appendLog("Sent session + Spotify token sync.")
            }
        }
    }

    fun syncSelectedData() {
        viewModelScope.launch {
            val node = getPrimaryNode() ?: run {
                makeToast("No watch connected.")
                appendLog("Selective sync failed: no connected watch.")
                refreshConnection()
                return@launch
            }

            _syncing.value = true
            try {
                val selected = syncSelection.value
                val requestId = UUID.randomUUID().toString()
                var sentCount = 0

                if (selected.songs) {
                    songRepository.getLikedSongs().first().take(MAX_SYNC_ITEMS).forEach { song ->
                        if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_SONG, requestId, songToJson(song))) {
                            sentCount++
                        }
                    }
                }
                if (selected.playlists) {
                    playlistRepository.getLikedPlaylists().first().take(MAX_SYNC_ITEMS).forEach { playlist ->
                        if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_PLAYLIST, requestId, playlistToJson(playlist))) {
                            sentCount++
                        }
                    }
                }
                if (selected.albums) {
                    albumRepository.getLikedAlbums().first().take(MAX_SYNC_ITEMS).forEach { album ->
                        if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_ALBUM, requestId, albumToJson(album))) {
                            sentCount++
                        }
                    }
                }
                if (selected.artists) {
                    artistRepository.getFollowedArtists().first().take(MAX_SYNC_ITEMS).forEach { artist ->
                        if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_ARTIST, requestId, artistToJson(artist.channelId, artist.name, artist.thumbnails, artist.followed))) {
                            sentCount++
                        }
                    }
                }
                if (selected.podcasts) {
                    podcastRepository.getFavoritePodcasts().first().take(MAX_SYNC_ITEMS).forEach { podcast ->
                        if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_PODCAST, requestId, podcastToJson(podcast))) {
                            sentCount++
                        }
                    }
                }
                if (selected.downloads) {
                    val downloadPayload = buildDownloadPayload()
                    if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_DOWNLOAD, requestId, downloadPayload)) {
                        sentCount++
                    }
                }

                appendLog("Selective sync sent ($sentCount payloads).")
                makeToast("Selective sync sent.")
            } finally {
                _syncing.value = false
            }
        }
    }

    fun remotePlayPause() = remoteCommand(WearCompanionBridge.REMOTE_PLAY_PAUSE)

    fun remoteNext() = remoteCommand(WearCompanionBridge.REMOTE_NEXT)

    fun remotePrevious() = remoteCommand(WearCompanionBridge.REMOTE_PREVIOUS)

    fun remoteVolumeUp() = remoteCommand(WearCompanionBridge.REMOTE_VOLUME_UP)

    fun remoteVolumeDown() = remoteCommand(WearCompanionBridge.REMOTE_VOLUME_DOWN)

    fun handoffQueueToWatch() {
        viewModelScope.launch {
            val queue = mediaPlayerHandler.queueData.value?.data
            val tracks =
                when {
                    queue?.listTracks?.isNotEmpty() == true -> queue.listTracks.take(MAX_HANDOFF_TRACKS)
                    mediaPlayerHandler.nowPlayingState.value.track != null -> listOf(mediaPlayerHandler.nowPlayingState.value.track!!)
                    else -> emptyList()
                }
            if (tracks.isEmpty()) {
                makeToast("No queue to hand off.")
                appendLog("Queue handoff skipped: queue is empty.")
                return@launch
            }

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
                appendLog("Queue handoff sent (${tracks.size} tracks).")
                makeToast("Queue handoff sent to watch.")
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            dataStoreManager.putString(WearCompanionBridge.KEY_LOG, "")
        }
    }

    private fun remoteCommand(command: String) {
        viewModelScope.launch {
            val payload = JSONObject().put("command", command)
            val sent = sendSimpleAction(WearCompanionBridge.ACTION_REMOTE, payload)
            if (sent) {
                appendLog("Remote command sent: $command")
            }
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
        private const val MAX_SYNC_ITEMS = 120
        private const val MAX_HANDOFF_TRACKS = 80
        private const val MAX_LOG_CHARS = 16000
    }
}
