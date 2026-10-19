package com.maxrave.simpmusic.wear.wearable

import android.content.Context
import com.maxrave.common.nodeSyncKey
import com.maxrave.simpmusic.wear.offline.toJson
import com.maxrave.common.removedRemoteMembership
import android.media.AudioManager
import android.os.Build
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.maxrave.common.Config
import com.maxrave.common.WearCompanionBridge
import com.maxrave.common.BridgeReplyCache
import com.maxrave.common.bridgeDigest
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
import com.maxrave.simpmusic.wear.BuildConfig
import com.maxrave.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private const val KEY_WEAR_LOGIN_STATUS = "wear_login_status"
private const val KEY_WEAR_LOGIN_MESSAGE = "wear_login_message"
private const val PATH_LOGIN_COOKIE = "/simpmusic/login/cookie"
private const val PATH_LOGIN_STATUS = "/simpmusic/login/status"

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class WearDataLayerListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestMutex = Mutex()
    private val replies = BridgeReplyCache(capacity = 32)

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

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
        if (messageEvent.data.size > 100_000) return
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
        if (requestId.isBlank() || requestId.length > 128 || action.length > 64 || raw.length > 100_000) return

        scope.launch {
            if (action == WearCompanionBridge.ACTION_PLAYBACK_STATE) {
                sendCompanionResponse(sourceNodeId, requestId, action, true, "", playbackData())
                return@launch
            }
            requestMutex.withLock {
            val replayKey = "$sourceNodeId:$requestId:$action"
            replies.get(replayKey, android.os.SystemClock.elapsedRealtime())?.let { cached ->
                Wearable.getMessageClient(applicationContext).sendMessage(sourceNodeId,
                    WearCompanionBridge.PATH_RESPONSE, cached.toByteArray(Charsets.UTF_8))
                return@withLock
            }
            val handled =
                runCatching { withContext(com.maxrave.common.ImportedLibraryMutation) {
                    when (action) {
                        WearCompanionBridge.ACTION_STATUS -> handleStatus(sourceNodeId, requestId)
                        WearCompanionBridge.ACTION_LOGOUT -> handleLogout(sourceNodeId, requestId)
                        WearCompanionBridge.ACTION_RECONCILE -> handleReconcile(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_SESSION -> handleSyncSession(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_SONG -> handleSyncSong(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_PLAYLIST -> handleSyncPlaylist(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_ALBUM -> handleSyncAlbum(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_ARTIST -> handleSyncArtist(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_PODCAST -> handleSyncPodcast(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_SYNC_DOWNLOAD -> handleSyncDownload(sourceNodeId, requestId, payload)
                        WearCompanionBridge.ACTION_REMOTE -> {
                            (application as com.maxrave.simpmusic.wear.WearSimpMusicApplication).preparePlaybackCaches()
                            withContext(Dispatchers.Main.immediate) { handleRemote(sourceNodeId, requestId, payload) }
                        }
                        else ->
                            sendCompanionResponse(
                                sourceNodeId = sourceNodeId,
                                requestId = requestId,
                                action = action,
                                ok = false,
                                message = "Unsupported action: $action",
                            )
                    }
                } }
            handled.onFailure { error ->
                if (error is CancellationException) throw error
                Logger.w("WearCompanion", "Action failed action=$action error=${error.javaClass.simpleName}")
                sendCompanionResponse(
                    sourceNodeId = sourceNodeId,
                    requestId = requestId,
                    action = action,
                    ok = false,
                    message = "Watch couldn't complete this action. Check the connection and try again.",
                )
            }
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
        (application as com.maxrave.simpmusic.wear.WearSimpMusicApplication).preparePlaybackCaches()
        withContext(Dispatchers.Main.immediate) {
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
                    .put("appVersion", BuildConfig.VERSION_NAME)
                    .put("loggedIn", dataStoreManager.loggedIn.first() == DataStoreManager.TRUE)
                    .put("hasCookie", dataStoreManager.cookie.first().isNotBlank())
                    .put("hasSpotifySpdc", dataStoreManager.spdc.first().isNotBlank())
                    .put("accountCount", accountCount)
                    .put("queueSize", queueSize)
                    .put("nowPlayingVideoId", nowPlaying)
            val state = playbackData()
            diagnostics.put("libraryGeneration", libraryGeneration())
                .put("protocolVersion", WearCompanionBridge.PROTOCOL_VERSION)
                .put("capabilities", JSONArray(listOf("playback", "queue", "logout", "reconcile", "offline_queue")))
                .put("playback", state.getJSONObject("playback"))
            sendCompanionResponse(
                sourceNodeId = sourceNodeId,
                requestId = requestId,
                action = WearCompanionBridge.ACTION_STATUS,
                ok = true,
                message = "Watch diagnostics ready.",
                data = diagnostics,
            )
                }
        }

    private suspend fun playbackData(): JSONObject {
        (application as com.maxrave.simpmusic.wear.WearSimpMusicApplication).preparePlaybackCaches()
        return withContext(Dispatchers.Main.immediate) {
            val handler: MediaPlayerHandler = GlobalContext.get().get()
            val current = handler.nowPlayingState.value
            val item = handler.player.currentMediaItem
            val controls = handler.controlState.value
            val tracks = handler.queueData.value?.data?.listTracks.orEmpty()
            val currentIndex = handler.player.currentMediaItemIndex
            val start = (currentIndex - 10).coerceAtLeast(0)
            val queue = JSONArray()
            tracks.drop(start).take(40).forEachIndexed { offset, track ->
                queue.put(JSONObject().put("index", start + offset).put("videoId", track.videoId)
                    .put("title", track.title.take(160)).put("artist", track.artists?.joinToString { it.name }.orEmpty().take(512)))
            }
            val audio = getSystemService(AudioManager::class.java)
            val state = JSONObject().put("generatedAt", System.currentTimeMillis())
                .put("title", (item?.metadata?.title ?: current.songEntity?.title ?: current.track?.title ?: current.mediaItem.metadata.title).orEmpty().take(512))
                .put("artist", (item?.metadata?.artist ?: current.songEntity?.artistName?.joinToString() ?: current.track?.artists?.joinToString { it.name }
                    ?: current.mediaItem.metadata.artist).orEmpty().take(512))
                .put("artwork", (item?.metadata?.artworkUri ?: current.songEntity?.thumbnails ?: current.track?.thumbnails?.lastOrNull()?.url).orEmpty().take(2048))
                .put("videoId", item?.mediaId ?: current.songEntity?.videoId ?: current.track?.videoId ?: current.mediaItem.mediaId)
                .put("isPlaying", handler.player.isPlaying)
                // Loading also describes buffer fill during healthy playback; only the player state means a stall.
                .put("isBuffering", handler.player.playbackState == com.maxrave.domain.data.player.PlayerConstants.STATE_BUFFERING)
                .put("positionMs", handler.getProgress().coerceAtLeast(0))
                .put("durationMs", handler.getPlayerDuration().coerceAtLeast(0))
                .put("canNext", controls.isNextAvailable).put("canPrevious", controls.isPreviousAvailable)
                .put("queueRevision", bridgeDigest(tracks.joinToString("\n") { it.videoId }))
                .put("queueSize", tracks.size).put("currentIndex", currentIndex).put("queue", queue)
                .put("volume", audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
                .put("maxVolume", audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0)
            val offline = withContext(Dispatchers.IO) {
                com.maxrave.simpmusic.wear.offline.OfflineQueue(applicationContext).status(GlobalContext.get().get()).toJson()
            }
            JSONObject().put("protocolVersion", WearCompanionBridge.PROTOCOL_VERSION).put("playback", state).put("offlinePack", offline)
        }
    }

    private suspend fun handleLogout(sourceNodeId: String, requestId: String) {
        val store: DataStoreManager = GlobalContext.get().get()
        WearAccountManager(applicationContext, store, GlobalContext.get().get(), GlobalContext.get().get()).logOutAll()
        store.setSpdc("")
        store.setSpotifyClientToken("")
        store.setSpotifyPersonalToken("")
        store.setSpotifyClientTokenExpires(0)
        store.setSpotifyPersonalTokenExpires(0)
        store.putString(KEY_WEAR_LOGIN_STATUS, "signed_out")
        store.putString(KEY_WEAR_LOGIN_MESSAGE, "Signed out from your phone.")
        sendCompanionResponse(sourceNodeId, requestId, WearCompanionBridge.ACTION_LOGOUT, true, "Watch signed out.", playbackData())
    }

    private val ownershipPrefs by lazy { getSharedPreferences("watch_bridge_ownership", Context.MODE_PRIVATE) }

    private fun libraryGeneration(): String {
        ownershipPrefs.getString("generation", null)?.let { return it }
        return java.util.UUID.randomUUID().toString().also { ownershipPrefs.edit().putString("generation", it).apply() }
    }

    private val localMembership by lazy { WatchLocalMembership(this) }
    private fun effectiveMembership(node: String, category: String, id: String, incoming: Boolean): Boolean {
        val key = nodeSyncKey(category, node)
        val otherOwns = ownershipPrefs.all.any { (k, v) -> k.startsWith("${category}_v2_") &&
            k != key && !k.endsWith("_manifest") && (v as? Set<*>)?.contains(id) == true }
        return com.maxrave.common.effectiveImportedMembership(incoming, localMembership.intent(category, id), otherOwns)
    }

    private fun rememberMembership(node: String, category: String, id: String, liked: Boolean, previouslyLiked: Boolean) {
        val key = nodeSyncKey(category, node)
        val owned = ownershipPrefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        // Existing watch-local favorites are never claimed by a phone.
        val otherPhoneOwns = ownershipPrefs.all.any { (k, v) ->
            k.startsWith("${category}_v2_") && !k.endsWith("_manifest") && k != key && (v as? Set<*>)?.contains(id) == true
        }
        if (liked && (!previouslyLiked || id in owned || otherPhoneOwns)) owned.add(id)
        if (!liked) owned.remove(id)
        ownershipPrefs.edit().putStringSet(key, owned).apply()
    }

    private suspend fun handleReconcile(node: String, requestId: String, payload: JSONObject) {
        val category = payload.optString("category")
        require(category in setOf("songs", "playlists", "albums", "artists", "podcasts")) { "Unknown library collection." }
        val ids = payload.optJSONArray("ids") ?: error("Missing collection manifest.")
        require(ids.length() <= 60) { "Collection manifest too large." }
        val key = nodeSyncKey(category, node)
        val revision = payload.optString("revision")
        val progress = if (revision.isNotBlank()) {
            val previousRevision = ownershipPrefs.getString("${key}_revision", null)
            val previous = previousRevision?.let { com.maxrave.common.ManifestProgress(it,
                ownershipPrefs.getInt("${key}_offset", 0), ownershipPrefs.getStringSet("${key}_manifest", emptySet()).orEmpty()) }
            com.maxrave.common.mergeManifestPage(previous, revision, payload.optInt("offset"),
                (0 until ids.length()).map { ids.getString(it) })
        } else null // Older manual senders can supply one complete bounded manifest.
        val incoming = progress?.ids ?: (0 until ids.length()).map { ids.getString(it) }.toSet()
        require(incoming.all { it.isNotBlank() && it.length <= 512 }) { "Invalid collection identifier." }
        val owned = ownershipPrefs.getStringSet(key, emptySet()).orEmpty()
        val protected = ownershipPrefs.all.filterKeys { it.startsWith("${category}_v2_") && !it.endsWith("_manifest") && it != key }
            .values.flatMap { (it as? Set<*>)?.filterIsInstance<String>().orEmpty() }.toSet()
        val complete = payload.optBoolean("complete", false)
        val removed = removedRemoteMembership(owned, incoming, complete, protected + localMembership.liked(category))
        for (id in removed) {
            when (category) {
                "songs" -> GlobalContext.get().get<SongRepository>().updateLikeStatus(id, 0)
                "playlists" -> GlobalContext.get().get<PlaylistRepository>().updatePlaylistLiked(id, 0)
                "albums" -> GlobalContext.get().get<AlbumRepository>().updateAlbumLiked(id, 0)
                "artists" -> GlobalContext.get().get<ArtistRepository>().updateFollowedStatus(id, 0)
                "podcasts" -> GlobalContext.get().get<PodcastRepository>().favoritePodcast(id, false).first()
            }
        }
        val edit = ownershipPrefs.edit()
        if (complete) edit.putStringSet(key, owned.intersect(incoming))
        if (progress != null) edit.putString("${key}_revision", progress.revision)
            .putInt("${key}_offset", progress.offset).putStringSet("${key}_manifest", progress.ids)
        edit.apply()
        sendCompanionResponse(node, requestId, WearCompanionBridge.ACTION_RECONCILE, true,
            "Library updated.", JSONObject().put("removed", removed.size).put("complete", complete))
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
        if (videoId.isBlank() || title.isBlank()) {
            rejectInvalidPayload(sourceNodeId, requestId, WearCompanionBridge.ACTION_SYNC_SONG)
            return
        }

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
        val previouslyLiked = songRepository.getSongById(videoId).first()?.liked == true
        WatchLibrarySync().song(songRepository, song.copy(liked = effectiveMembership(sourceNodeId, "songs", videoId, song.liked)))
        rememberMembership(sourceNodeId, "songs", videoId, song.liked, previouslyLiked)
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
        if (id.isBlank() || title.isBlank()) {
            rejectInvalidPayload(sourceNodeId, requestId, WearCompanionBridge.ACTION_SYNC_PLAYLIST)
            return
        }
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
        val previouslyLiked = playlistRepository.getPlaylist(id).first()?.liked == true
        WatchLibrarySync().playlist(playlistRepository, playlist.copy(liked = effectiveMembership(sourceNodeId, "playlists", id, playlist.liked)))
        rememberMembership(sourceNodeId, "playlists", id, playlist.liked, previouslyLiked)
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
        if (browseId.isBlank() || title.isBlank()) {
            rejectInvalidPayload(sourceNodeId, requestId, WearCompanionBridge.ACTION_SYNC_ALBUM)
            return
        }
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
        val previouslyLiked = albumRepository.getAlbum(browseId).first()?.liked == true
        WatchLibrarySync().album(albumRepository, album.copy(liked = effectiveMembership(sourceNodeId, "albums", browseId, album.liked)))
        rememberMembership(sourceNodeId, "albums", browseId, album.liked, previouslyLiked)
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
        if (channelId.isBlank() || name.isBlank()) {
            rejectInvalidPayload(sourceNodeId, requestId, WearCompanionBridge.ACTION_SYNC_ARTIST)
            return
        }
        val artistRepository: ArtistRepository = GlobalContext.get().get()
        val followed = payload.optBoolean("followed", false)
        val previouslyFollowed = artistRepository.getFollowedArtists().first().any { it.channelId == channelId }
        artistRepository.insertArtist(
            ArtistEntity(
                channelId = channelId,
                name = name,
                thumbnails = payload.optStringOrNull("thumbnails"),
                followed = followed,
            ),
        )
        artistRepository.updateArtistMetadata(ArtistEntity(channelId, name, payload.optStringOrNull("thumbnails"), followed))
        val effectiveFollowed = effectiveMembership(sourceNodeId, "artists", channelId, followed)
        if (previouslyFollowed != effectiveFollowed) artistRepository.updateFollowedStatus(channelId, if (effectiveFollowed) 1 else 0)
        rememberMembership(sourceNodeId, "artists", channelId, followed, previouslyFollowed)
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
        if (podcastId.isBlank() || title.isBlank()) {
            rejectInvalidPayload(sourceNodeId, requestId, WearCompanionBridge.ACTION_SYNC_PODCAST)
            return
        }
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
        val existing = podcastRepository.getPodcast(podcastId).first()
        podcastRepository.updatePodcastMetadata(podcast)
        val effectiveFavorite = effectiveMembership(sourceNodeId, "podcasts", podcastId, podcast.isFavorite)
        if (existing?.isFavorite != effectiveFavorite) {
            check(podcastRepository.favoritePodcast(podcastId, effectiveFavorite).first()) { "Podcast membership could not be updated." }
        }
        rememberMembership(sourceNodeId, "podcasts", podcastId, podcast.isFavorite, existing?.isFavorite == true)
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
        // No audio bytes are transferred by this action. Download state is device-local.
        sendCompanionResponse(
            sourceNodeId = sourceNodeId,
            requestId = requestId,
            action = WearCompanionBridge.ACTION_SYNC_DOWNLOAD,
            ok = false,
            message = "Phone download state cannot be applied without transferring audio to the watch.",
        )
    }

    private suspend fun handleRemote(
        sourceNodeId: String,
        requestId: String,
        payload: JSONObject,
    ) {
        val command = payload.optString("command")
        val mediaPlayerHandler: MediaPlayerHandler = GlobalContext.get().get()
        val queue = mediaPlayerHandler.queueData.value?.data?.listTracks.orEmpty()
        if (command in setOf(WearCompanionBridge.REMOTE_QUEUE_PLAY, WearCompanionBridge.REMOTE_QUEUE_REMOVE,
                WearCompanionBridge.REMOTE_QUEUE_MOVE)) {
            val index = payload.optInt("index", -1)
            val revision = bridgeDigest(queue.joinToString("\n") { it.videoId })
            if (payload.optString("queueRevision") != revision || index !in queue.indices ||
                payload.optString("videoId") != queue[index].videoId) {
                sendCompanionResponse(sourceNodeId, requestId, WearCompanionBridge.ACTION_REMOTE, false,
                    "The queue changed. Refresh it and try again.", playbackData())
                return
            }
        }
        if (command in setOf(WearCompanionBridge.REMOTE_PLAY_PAUSE, WearCompanionBridge.REMOTE_NEXT,
                WearCompanionBridge.REMOTE_PREVIOUS, WearCompanionBridge.REMOTE_QUEUE_PLAY,
                WearCompanionBridge.REMOTE_HANDOFF_QUEUE, WearCompanionBridge.REMOTE_PLAY_OFFLINE)) {
            require(command != WearCompanionBridge.REMOTE_PLAY_PAUSE || mediaPlayerHandler.player.mediaItemCount > 0) {
                "Choose a track on the watch first."
            }
            com.maxrave.media3.service.PlaybackServiceConnection.get(applicationContext).ensureConnected()
        }
        when (command) {
            WearCompanionBridge.REMOTE_PREPARE_OFFLINE -> {
                if (payload.optString("queueRevision") != bridgeDigest(queue.joinToString("\n") { it.videoId })) {
                    sendCompanionResponse(sourceNodeId, requestId, WearCompanionBridge.ACTION_REMOTE, false, "The queue changed. Refresh and try again.", playbackData())
                    return
                }
                try {
                    com.maxrave.simpmusic.wear.offline.OfflineQueue(applicationContext).prepare(queue, payload.optBoolean("chargingOnly", true))
                } catch (invalid: IllegalArgumentException) {
                    sendCompanionResponse(sourceNodeId, requestId, WearCompanionBridge.ACTION_REMOTE, false, invalid.message.orEmpty(), playbackData())
                    return
                }
            }
            WearCompanionBridge.REMOTE_RETRY_OFFLINE -> {
                val store = com.maxrave.simpmusic.wear.offline.OfflineQueue(applicationContext)
                require(payload.optString("packRevision") == store.revision()) { "The offline queue changed." }
                store.retry(payload.optBoolean("chargingOnly", true))
            }
            WearCompanionBridge.REMOTE_PLAY_OFFLINE -> {
                val store = com.maxrave.simpmusic.wear.offline.OfflineQueue(applicationContext)
                require(payload.optString("packRevision") == store.revision()) { "The offline queue changed." }
                store.play(mediaPlayerHandler)
            }

            WearCompanionBridge.REMOTE_SEEK -> {
                require(payload.optString("videoId").isNotBlank() &&
                    payload.optString("videoId") == mediaPlayerHandler.player.currentMediaItem?.mediaId) {
                    "The track changed. Refresh the player and try again."
                }
                val duration = mediaPlayerHandler.getPlayerDuration()
                require(duration > 0) { "Duration unavailable" }
                mediaPlayerHandler.player.seekTo(payload.optLong("positionMs").coerceIn(0, duration))
            }
            WearCompanionBridge.REMOTE_QUEUE_PLAY -> {
                mediaPlayerHandler.playMediaItemInMediaSource(payload.getInt("index"))
                mediaPlayerHandler.player.play()
            }
            WearCompanionBridge.REMOTE_QUEUE_REMOVE -> mediaPlayerHandler.removeMediaItem(payload.getInt("index"))
            WearCompanionBridge.REMOTE_QUEUE_MOVE -> {
                val destination = payload.optInt("to", -1)
                require(destination in queue.indices) { "Invalid queue destination" }
                mediaPlayerHandler.swap(payload.getInt("index"), destination)
            }
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
                        type = Config.PLAYLIST_CLICK,
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
            message = "Watch control applied.",
            data = playbackData(),
        )
    }

    private fun rejectInvalidPayload(sourceNodeId: String, requestId: String, action: String) {
        sendCompanionResponse(sourceNodeId, requestId, action, false, "Missing required identifier or title.")
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
        if (action != WearCompanionBridge.ACTION_PLAYBACK_STATE) {
            replies.put("$sourceNodeId:$requestId:$action", payload.toString(), android.os.SystemClock.elapsedRealtime())
        }
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
