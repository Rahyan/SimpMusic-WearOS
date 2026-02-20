package com.maxrave.simpmusic.wear.wearable

import android.media.AudioManager
import android.os.Build
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.maxrave.common.Config
import com.maxrave.common.WearCompanionBridge
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.PodcastsEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Album
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.AccountRepository
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.PodcastRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.simpmusic.wear.auth.WearAccountManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private const val KEY_WEAR_LOGIN_STATUS = "wear_login_status"
private const val KEY_WEAR_LOGIN_MESSAGE = "wear_login_message"
private const val PATH_LOGIN_COOKIE = "/simpmusic/login/cookie"
private const val PATH_LOGIN_STATUS = "/simpmusic/login/status"

class WearDataLayerListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_LOGIN_COOKIE -> onCookie(messageEvent)
            PATH_LOGIN_STATUS -> onStatus(messageEvent)
            WearCompanionBridge.PATH_REQUEST -> onCompanionRequest(messageEvent)
            WearCompanionBridge.PATH_RESPONSE -> onCompanionResponse(messageEvent)
            else -> return
        }
    }

    private fun onStatus(messageEvent: MessageEvent) {
        val payload = runCatching { messageEvent.data?.decodeToString() }.getOrNull().orEmpty()
        val parts = payload.split("|", limit = 2)
        val status = parts.getOrNull(0).orEmpty()
        val msg = parts.getOrNull(1).orEmpty()
        if (status.isBlank() && msg.isBlank()) return

        scope.launch {
            val dataStoreManager: DataStoreManager = GlobalContext.get().get()
            if (status.isNotBlank()) {
                dataStoreManager.putString(KEY_WEAR_LOGIN_STATUS, status)
            }
            // Always update the message, even if empty, to avoid stale status text.
            dataStoreManager.putString(KEY_WEAR_LOGIN_MESSAGE, msg)
        }
    }

    private fun onCookie(messageEvent: MessageEvent) {
        val cookie = runCatching { messageEvent.data?.decodeToString() }.getOrNull()
        if (cookie.isNullOrBlank()) return

        scope.launch {
            val dataStoreManager: DataStoreManager = GlobalContext.get().get()
            val accountRepository: AccountRepository = GlobalContext.get().get()
            val commonRepository: CommonRepository = GlobalContext.get().get()

            dataStoreManager.putString(KEY_WEAR_LOGIN_STATUS, "processing")
            dataStoreManager.putString(KEY_WEAR_LOGIN_MESSAGE, "Syncing session to watch...")

            val ok =
                WearAccountManager(
                    context = applicationContext,
                    dataStoreManager = dataStoreManager,
                    accountRepository = accountRepository,
                    commonRepository = commonRepository,
                ).addAccountFromCookie(cookie)

            if (ok) {
                dataStoreManager.putString(KEY_WEAR_LOGIN_STATUS, "success")
                dataStoreManager.putString(KEY_WEAR_LOGIN_MESSAGE, "Signed in.")
            } else {
                dataStoreManager.putString(KEY_WEAR_LOGIN_STATUS, "failed")
                dataStoreManager.putString(KEY_WEAR_LOGIN_MESSAGE, "Failed to sign in. Try again.")
            }
        }
    }

    private fun onCompanionRequest(messageEvent: MessageEvent) {
        val sourceNodeId = messageEvent.sourceNodeId
        val raw = runCatching { messageEvent.data.decodeToString() }.getOrNull().orEmpty()
        if (raw.isBlank()) return
        val envelope = runCatching { JSONObject(raw) }.getOrNull()
        if (envelope == null) {
            sendCompanionResponse(
                sourceNodeId = sourceNodeId,
                requestId = "",
                action = "unknown",
                ok = false,
                message = "Invalid companion payload.",
            )
            return
        }

        val requestId = envelope.optString("requestId")
        val action = envelope.optString("action")
        val payload = envelope.optJSONObject("payload") ?: JSONObject()

        scope.launch {
            val handled =
                runCatching {
                    when (action) {
                        WearCompanionBridge.ACTION_STATUS -> handleStatus(sourceNodeId, requestId)
                        WearCompanionBridge.ACTION_SYNC_SESSION -> handleSyncSession(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_SONG -> handleSyncSong(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_PLAYLIST -> handleSyncPlaylist(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_ALBUM -> handleSyncAlbum(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_ARTIST -> handleSyncArtist(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_PODCAST -> handleSyncPodcast(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_DOWNLOAD -> handleSyncDownload(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_REMOTE -> handleRemote(sourceNodeId, requestId, payload)
                        else ->
                            sendCompanionResponse(
                                sourceNodeId = sourceNodeId,
                                requestId = requestId,
                                action = action,
                                ok = false,
                                message = "Unsupported action: $action",
                            )
                    }
                }
            handled.onFailure { error ->
                sendCompanionResponse(
                    sourceNodeId = sourceNodeId,
                    requestId = requestId,
                    action = action,
                    ok = false,
                    message = error.message ?: "Watch action failed.",
                )
            }
        }
    }

    private fun onCompanionResponse(messageEvent: MessageEvent) {
        val payload = runCatching { messageEvent.data.decodeToString() }.getOrNull().orEmpty()
        if (payload.isBlank()) return
        scope.launch {
            val dataStoreManager: DataStoreManager = GlobalContext.get().get()
            dataStoreManager.putString(WearCompanionBridge.KEY_LAST_RESPONSE, payload)
            val response = runCatching { JSONObject(payload) }.getOrNull()
            if (response?.optString("action") == WearCompanionBridge.ACTION_STATUS) {
                dataStoreManager.putString(WearCompanionBridge.KEY_LAST_DIAGNOSTICS, payload)
            }
            appendCompanionLog(response?.optString("message").orEmpty().ifBlank { payload })
        }
    }

    private suspend fun appendCompanionLog(message: String) {
        val dataStoreManager: DataStoreManager = GlobalContext.get().get()
        val current = dataStoreManager.getString(WearCompanionBridge.KEY_LOG).first().orEmpty()
        val timestamp = LocalTime.now().truncatedTo(ChronoUnit.SECONDS)
        val updated =
            if (current.isBlank()) {
                "[$timestamp] $message"
            } else {
                "$current\n[$timestamp] $message"
            }
        dataStoreManager.putString(WearCompanionBridge.KEY_LOG, updated.takeLast(16000))
    }

    private suspend fun handleStatus(
        sourceNodeId: String,
        requestId: String,
    ) {
        val dataStoreManager: DataStoreManager = GlobalContext.get().get()
        val accountRepository: AccountRepository = GlobalContext.get().get()
        val mediaPlayerHandler: MediaPlayerHandler = GlobalContext.get().get()
        val accountCount = accountRepository.getGoogleAccounts().first()?.size ?: 0
        val queueSize = mediaPlayerHandler.queueData.value?.data?.listTracks?.size ?: 0
        val nowPlaying = mediaPlayerHandler.nowPlayingState.value.songEntity?.videoId.orEmpty()
        val diagnostics =
            JSONObject()
                .put("model", Build.MODEL)
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("appVersion", dataStoreManager.appVersion.first())
                .put("loggedIn", dataStoreManager.loggedIn.first() == DataStoreManager.TRUE)
                .put("hasCookie", dataStoreManager.cookie.first().isNotBlank())
                .put("hasSpotifySpdc", dataStoreManager.spdc.first().isNotBlank())
                .put("accountCount", accountCount)
                .put("queueSize", queueSize)
                .put("nowPlayingVideoId", nowPlaying)
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_STATUS,
            ok = true,
            message = "Watch diagnostics ready.",
            data = diagnostics,
        )
    }

    private suspend fun handleSyncSession(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val cookie = payload.optString("cookie")
        if (cookie.isBlank()) {
            sendCompanionResponse(
                sourceNodeId = sourceNodeId,
                requestId = requestId,
                action = WearCompanionBridge.ACTION_SYNC_SESSION,
                ok = false,
                message = "Missing cookie in session sync payload.",
            )
            return
        }
        val dataStoreManager: DataStoreManager = GlobalContext.get().get()
        val accountRepository: AccountRepository = GlobalContext.get().get()
        val commonRepository: CommonRepository = GlobalContext.get().get()

        val synced =
            WearAccountManager(
                context = applicationContext,
                dataStoreManager = dataStoreManager,
                accountRepository = accountRepository,
                commonRepository = commonRepository,
            ).addAccountFromCookie(cookie)

        if (!synced) {
            sendCompanionResponse(
                sourceNodeId = sourceNodeId,
                requestId = requestId,
                action = WearCompanionBridge.ACTION_SYNC_SESSION,
                ok = false,
                message = "Watch could not apply account session.",
            )
            return
        }

        payload.optString("spdc").takeIf { it.isNotBlank() }?.let { dataStoreManager.setSpdc(it) }
        payload.optString("spotifyClientToken").takeIf { it.isNotBlank() }?.let { dataStoreManager.setSpotifyClientToken(it) }
        payload.optLong("spotifyClientTokenExpires").takeIf { it > 0L }?.let { dataStoreManager.setSpotifyClientTokenExpires(it) }
        payload.optString("spotifyPersonalToken").takeIf { it.isNotBlank() }?.let { dataStoreManager.setSpotifyPersonalToken(it) }
        payload.optLong("spotifyPersonalTokenExpires").takeIf { it > 0L }?.let { dataStoreManager.setSpotifyPersonalTokenExpires(it) }
        dataStoreManager.setSpotifyLyrics(payload.optBoolean("spotifyLyrics", false))
        dataStoreManager.setSpotifyCanvas(payload.optBoolean("spotifyCanvas", false))

        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_SYNC_SESSION,
            ok = true,
            message = "Session and Spotify tokens synced to watch.",
        )
    }

    private suspend fun handleSyncSong(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val videoId = payload.optString("videoId")
        val title = payload.optString("title")
        if (videoId.isBlank() || title.isBlank()) return

        val songRepository: SongRepository = GlobalContext.get().get()
        val song =
            SongEntity(
                videoId = videoId,
                albumId = payload.optStringOrNull("albumId"),
                albumName = payload.optStringOrNull("albumName"),
                artistId = jsonArrayToStringList(payload.optJSONArray("artistId")).ifEmpty { null },
                artistName = jsonArrayToStringList(payload.optJSONArray("artistName")).ifEmpty { null },
                duration = payload.optString("duration"),
                durationSeconds = payload.optInt("durationSeconds"),
                isAvailable = payload.optBoolean("isAvailable", true),
                isExplicit = payload.optBoolean("isExplicit", false),
                likeStatus = payload.optString("likeStatus"),
                thumbnails = payload.optStringOrNull("thumbnails"),
                title = title,
                videoType = payload.optString("videoType"),
                category = payload.optStringOrNull("category"),
                resultType = payload.optStringOrNull("resultType"),
                liked = payload.optBoolean("liked", false),
                downloadState = payload.optInt("downloadState"),
            )
        songRepository.insertSong(song).first()
        songRepository.updateLikeStatus(videoId, if (song.liked) 1 else 0)
        songRepository.updateDownloadState(videoId, song.downloadState)
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_SYNC_SONG,
            ok = true,
            message = "Synced song ${song.title}.",
        )
    }

    private suspend fun handleSyncPlaylist(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val id = payload.optString("id")
        val title = payload.optString("title")
        if (id.isBlank() || title.isBlank()) return
        val playlistRepository: PlaylistRepository = GlobalContext.get().get()
        val playlist =
            PlaylistEntity(
                id = id,
                author = payload.optStringOrNull("author"),
                description = payload.optString("description"),
                duration = payload.optString("duration"),
                durationSeconds = payload.optInt("durationSeconds"),
                privacy = payload.optString("privacy"),
                thumbnails = payload.optString("thumbnails"),
                title = title,
                trackCount = payload.optInt("trackCount"),
                tracks = jsonArrayToStringList(payload.optJSONArray("tracks")).ifEmpty { null },
                year = payload.optStringOrNull("year"),
                liked = payload.optBoolean("liked", false),
                downloadState = payload.optInt("downloadState"),
            )
        playlistRepository.insertAndReplacePlaylist(playlist)
        playlistRepository.updatePlaylistLiked(id, if (playlist.liked) 1 else 0)
        playlistRepository.updatePlaylistDownloadState(id, playlist.downloadState)
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_SYNC_PLAYLIST,
            ok = true,
            message = "Synced playlist ${playlist.title}.",
        )
    }

    private suspend fun handleSyncAlbum(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val browseId = payload.optString("browseId")
        val title = payload.optString("title")
        if (browseId.isBlank() || title.isBlank()) return
        val albumRepository: AlbumRepository = GlobalContext.get().get()
        val album =
            AlbumEntity(
                browseId = browseId,
                artistId = jsonArrayToNullableStringList(payload.optJSONArray("artistId")).ifEmpty { null },
                artistName = jsonArrayToStringList(payload.optJSONArray("artistName")).ifEmpty { null },
                audioPlaylistId = payload.optString("audioPlaylistId"),
                description = payload.optString("description"),
                duration = payload.optStringOrNull("duration"),
                durationSeconds = payload.optInt("durationSeconds"),
                thumbnails = payload.optStringOrNull("thumbnails"),
                title = title,
                trackCount = payload.optInt("trackCount"),
                tracks = jsonArrayToStringList(payload.optJSONArray("tracks")).ifEmpty { null },
                type = payload.optString("type"),
                year = payload.optStringOrNull("year"),
                liked = payload.optBoolean("liked", false),
                downloadState = payload.optInt("downloadState"),
            )
        albumRepository.insertAlbum(album).first()
        albumRepository.updateAlbumLiked(browseId, if (album.liked) 1 else 0)
        albumRepository.updateAlbumDownloadState(browseId, album.downloadState)
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_SYNC_ALBUM,
            ok = true,
            message = "Synced album ${album.title}.",
        )
    }

    private suspend fun handleSyncArtist(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val channelId = payload.optString("channelId")
        val name = payload.optString("name")
        if (channelId.isBlank() || name.isBlank()) return
        val artistRepository: ArtistRepository = GlobalContext.get().get()
        val followed = payload.optBoolean("followed", false)
        artistRepository.insertArtist(
            ArtistEntity(
                channelId = channelId,
                name = name,
                thumbnails = payload.optStringOrNull("thumbnails"),
                followed = followed,
            ),
        )
        artistRepository.updateFollowedStatus(channelId, if (followed) 1 else 0)
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_SYNC_ARTIST,
            ok = true,
            message = "Synced artist $name.",
        )
    }

    private suspend fun handleSyncPodcast(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val podcastId = payload.optString("podcastId")
        val title = payload.optString("title")
        if (podcastId.isBlank() || title.isBlank()) return
        val podcastRepository: PodcastRepository = GlobalContext.get().get()
        val podcast =
            PodcastsEntity(
                podcastId = podcastId,
                title = title,
                authorId = payload.optString("authorId"),
                authorName = payload.optString("authorName"),
                authorThumbnail = payload.optStringOrNull("authorThumbnail"),
                description = payload.optStringOrNull("description"),
                thumbnail = payload.optStringOrNull("thumbnail"),
                isFavorite = payload.optBoolean("isFavorite", false),
                listEpisodes = jsonArrayToStringList(payload.optJSONArray("listEpisodes")),
            )
        podcastRepository.insertPodcast(podcast).first()
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_SYNC_PODCAST,
            ok = true,
            message = "Synced podcast ${podcast.title}.",
        )
    }

    private suspend fun handleSyncDownload(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val songRepository: SongRepository = GlobalContext.get().get()
        val playlistRepository: PlaylistRepository = GlobalContext.get().get()
        val albumRepository: AlbumRepository = GlobalContext.get().get()

        payload.optJSONArray("songs")?.forEachObject { item ->
            val videoId = item.optString("videoId")
            if (videoId.isNotBlank()) {
                songRepository.updateDownloadState(videoId, item.optInt("downloadState"))
            }
        }
        payload.optJSONArray("playlists")?.forEachObject { item ->
            val playlistId = item.optString("id")
            if (playlistId.isNotBlank()) {
                playlistRepository.updatePlaylistDownloadState(playlistId, item.optInt("downloadState"))
            }
        }
        payload.optJSONArray("albums")?.forEachObject { item ->
            val browseId = item.optString("browseId")
            if (browseId.isNotBlank()) {
                albumRepository.updateAlbumDownloadState(browseId, item.optInt("downloadState"))
            }
        }
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_SYNC_DOWNLOAD,
            ok = true,
            message = "Synced download metadata.",
        )
    }

    private suspend fun handleRemote(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val command = payload.optString("command")
        val mediaPlayerHandler: MediaPlayerHandler = GlobalContext.get().get()
        when (command) {
            WearCompanionBridge.REMOTE_PLAY_PAUSE -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
            WearCompanionBridge.REMOTE_NEXT -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
            WearCompanionBridge.REMOTE_PREVIOUS -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
            WearCompanionBridge.REMOTE_VOLUME_UP -> {
                val audioManager = getSystemService(AudioManager::class.java)
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            WearCompanionBridge.REMOTE_VOLUME_DOWN -> {
                val audioManager = getSystemService(AudioManager::class.java)
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            WearCompanionBridge.REMOTE_HANDOFF_QUEUE -> {
                val tracksJson = payload.optString("tracks")
                val tracks = if (tracksJson.isBlank()) emptyList() else parseTracks(tracksJson)
                if (tracks.isNotEmpty()) {
                    val index = payload.optInt("index", 0).coerceIn(0, tracks.lastIndex)
                    val playlistType =
                        payload.optString("playlistType").takeIf { it.isNotBlank() }?.let {
                            runCatching { PlaylistType.valueOf(it) }.getOrNull()
                        }
                    mediaPlayerHandler.setQueueData(
                        QueueData.Data(
                            listTracks = tracks,
                            firstPlayedTrack = tracks[index],
                            playlistId = payload.optStringOrNull("playlistId"),
                            playlistName = payload.optStringOrNull("playlistName"),
                            playlistType = playlistType,
                        ),
                    )
                    mediaPlayerHandler.loadMediaItem<Track>(
                        anyTrack = tracks[index],
                        type = Config.SONG_CLICK,
                        index = index,
                    )
                }
            }
            else -> {
                sendCompanionResponse(
                    sourceNodeId = sourceNodeId,
                    requestId = requestId,
                    action = WearCompanionBridge.ACTION_REMOTE,
                    ok = false,
                    message = "Unsupported remote command: $command",
                )
                return
            }
        }
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_REMOTE,
            ok = true,
            message = "Executed remote command: $command",
        )
    }

    private fun sendCompanionResponse(
        sourceNodeId: String,
        requestId: String,
        action: String,
        ok: Boolean,
        message: String,
        data: JSONObject? = null,
    ) {
        val payload =
            JSONObject()
                .put("requestId", requestId)
                .put("action", action)
                .put("ok", ok)
                .put("message", message)
                .put("timestamp", System.currentTimeMillis())
        if (data != null) payload.put("data", data)
        Wearable
            .getMessageClient(applicationContext)
            .sendMessage(sourceNodeId, WearCompanionBridge.PATH_RESPONSE, payload.toString().toByteArray(Charsets.UTF_8))
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val value = optString(key)
        return value.ifBlank { null }
    }

    private fun jsonArrayToStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun jsonArrayToNullableStringList(array: JSONArray?): List<String?> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.opt(i)
                when (value) {
                    null, JSONObject.NULL -> add(null)
                    else -> add(value.toString())
                }
            }
        }
    }

    private fun parseTracks(json: String): List<Track> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val videoId = item.optString("videoId")
                if (title.isBlank() || videoId.isBlank()) continue

                val album =
                    item.optJSONObject("album")?.let { albumObj ->
                        val id = albumObj.optString("id")
                        val name = albumObj.optString("name")
                        if (id.isBlank() || name.isBlank()) null else Album(id, name)
                    }

                val artists =
                    item.optJSONArray("artists")
                        ?.let { artistsArray ->
                            buildList {
                                for (artistIndex in 0 until artistsArray.length()) {
                                    val artistObj = artistsArray.optJSONObject(artistIndex) ?: continue
                                    val name = artistObj.optString("name")
                                    if (name.isBlank()) continue
                                    add(Artist(id = artistObj.optStringOrNull("id"), name = name))
                                }
                            }
                        }?.ifEmpty { null }

                val thumbnails =
                    item.optJSONArray("thumbnails")
                        ?.let { thumbnailArray ->
                            buildList {
                                for (thumbnailIndex in 0 until thumbnailArray.length()) {
                                    val thumbnailObj = thumbnailArray.optJSONObject(thumbnailIndex) ?: continue
                                    val url = thumbnailObj.optString("url")
                                    if (url.isBlank()) continue
                                    add(
                                        Thumbnail(
                                            width = thumbnailObj.optInt("width", 0),
                                            url = url,
                                            height = thumbnailObj.optInt("height", 0),
                                        ),
                                    )
                                }
                            }
                        }?.ifEmpty { null }

                add(
                    Track(
                        album = album,
                        artists = artists,
                        duration = item.optStringOrNull("duration"),
                        durationSeconds = item.optIntOrNull("durationSeconds"),
                        isAvailable = item.optBoolean("isAvailable", true),
                        isExplicit = item.optBoolean("isExplicit", false),
                        likeStatus = item.optStringOrNull("likeStatus"),
                        thumbnails = thumbnails,
                        title = title,
                        videoId = videoId,
                        videoType = item.optStringOrNull("videoType"),
                        category = item.optStringOrNull("category"),
                        feedbackTokens = null,
                        resultType = item.optStringOrNull("resultType"),
                        year = item.optStringOrNull("year"),
                    ),
                )
            }
        }
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            block(item)
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) {
            optInt(key)
        } else {
            null
        }
}
