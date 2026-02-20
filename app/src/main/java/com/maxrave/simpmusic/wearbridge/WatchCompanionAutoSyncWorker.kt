package com.maxrave.simpmusic.wearbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.coroutines.resume
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WatchCompanionAutoSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val dataStoreManager: DataStoreManager by inject()
    private val songRepository: SongRepository by inject()
    private val playlistRepository: PlaylistRepository by inject()
    private val albumRepository: AlbumRepository by inject()
    private val artistRepository: ArtistRepository by inject()
    private val podcastRepository: PodcastRepository by inject()

    override suspend fun doWork(): Result {
        val batterySaverPolicyEnabled =
            dataStoreManager.getString(WearCompanionBridge.KEY_AUTO_SYNC_BATTERY_SAVER).first() ==
                DataStoreManager.TRUE
        val unmeteredOnlyPolicyEnabled =
            dataStoreManager.getString(WearCompanionBridge.KEY_AUTO_SYNC_UNMETERED_ONLY).first() ==
                DataStoreManager.TRUE
        if (batterySaverPolicyEnabled && !isDeviceCharging()) {
            val reason = "Auto-sync skipped: battery-saver policy requires charging."
            appendLog(reason)
            persistAutoSyncStats(status = "skipped", reason = reason)
            return Result.success()
        }
        if (unmeteredOnlyPolicyEnabled && !isActiveNetworkUnmetered()) {
            val reason = "Auto-sync skipped: unmetered-only policy is active."
            appendLog(reason)
            persistAutoSyncStats(status = "skipped", reason = reason)
            return Result.success()
        }

        val node = getConnectedNodes().firstOrNull()
        if (node == null) {
            val reason = "Auto-sync skipped: no connected watch."
            appendLog(reason)
            persistAutoSyncStats(status = "skipped", reason = reason)
            return Result.success()
        }

        var attempted = 0
        var sent = 0
        fun counted(result: Boolean) {
            attempted++
            if (result) sent++
        }
        var changedCategories = 0
        val categoryStats = linkedMapOf<String, CategorySyncResult>()

        val cookie = dataStoreManager.cookie.first()
        val loggedIn = dataStoreManager.loggedIn.first() == DataStoreManager.TRUE
        if (loggedIn && cookie.isNotBlank()) {
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
            val sessionSyncResult =
                syncCategoryIfChanged(
                    signatureKey = WearCompanionBridge.KEY_AUTO_SYNC_SIG_SESSION,
                    signature = hashText(payload.toString()),
                ) {
                    1 to if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_SESSION, payload)) 1 else 0
                }
            categoryStats["session"] = sessionSyncResult
            changedCategories += if (sessionSyncResult.changed) 1 else 0
            repeat(sessionSyncResult.attempted) { counted(it < sessionSyncResult.sent) }
        }

        val likedSongs = songRepository.getLikedSongs().first().take(MAX_SYNC_ITEMS)
        val songsSyncResult =
            syncCategoryIfChanged(
                signatureKey = WearCompanionBridge.KEY_AUTO_SYNC_SIG_SONGS,
                signature = hashText(likedSongs.joinToString("|") { "${it.videoId}:${it.likeStatus}:${it.downloadState}" }),
            ) {
                syncCollection(node.id, WearCompanionBridge.ACTION_SYNC_SONG, likedSongs, ::songToJson)
            }
        categoryStats["songs"] = songsSyncResult
        changedCategories += if (songsSyncResult.changed) 1 else 0
        repeat(songsSyncResult.attempted) { counted(it < songsSyncResult.sent) }

        val likedPlaylists = playlistRepository.getLikedPlaylists().first().take(MAX_SYNC_ITEMS)
        val playlistsSyncResult =
            syncCategoryIfChanged(
                signatureKey = WearCompanionBridge.KEY_AUTO_SYNC_SIG_PLAYLISTS,
                signature = hashText(likedPlaylists.joinToString("|") { "${it.id}:${it.liked}:${it.downloadState}" }),
            ) {
                syncCollection(node.id, WearCompanionBridge.ACTION_SYNC_PLAYLIST, likedPlaylists, ::playlistToJson)
            }
        categoryStats["playlists"] = playlistsSyncResult
        changedCategories += if (playlistsSyncResult.changed) 1 else 0
        repeat(playlistsSyncResult.attempted) { counted(it < playlistsSyncResult.sent) }

        val likedAlbums = albumRepository.getLikedAlbums().first().take(MAX_SYNC_ITEMS)
        val albumsSyncResult =
            syncCategoryIfChanged(
                signatureKey = WearCompanionBridge.KEY_AUTO_SYNC_SIG_ALBUMS,
                signature = hashText(likedAlbums.joinToString("|") { "${it.browseId}:${it.liked}:${it.downloadState}" }),
            ) {
                syncCollection(node.id, WearCompanionBridge.ACTION_SYNC_ALBUM, likedAlbums, ::albumToJson)
            }
        categoryStats["albums"] = albumsSyncResult
        changedCategories += if (albumsSyncResult.changed) 1 else 0
        repeat(albumsSyncResult.attempted) { counted(it < albumsSyncResult.sent) }

        val followedArtists = artistRepository.getFollowedArtists().first().take(MAX_SYNC_ITEMS)
        val artistsSyncResult =
            syncCategoryIfChanged(
                signatureKey = WearCompanionBridge.KEY_AUTO_SYNC_SIG_ARTISTS,
                signature = hashText(followedArtists.joinToString("|") { "${it.channelId}:${it.followed}" }),
            ) {
                val payloads =
                    followedArtists.map { artist ->
                        JSONObject()
                            .put("channelId", artist.channelId)
                            .put("name", artist.name)
                            .put("thumbnails", artist.thumbnails ?: "")
                            .put("followed", artist.followed)
                    }
                syncJsonPayloadCollection(node.id, WearCompanionBridge.ACTION_SYNC_ARTIST, payloads)
            }
        categoryStats["artists"] = artistsSyncResult
        changedCategories += if (artistsSyncResult.changed) 1 else 0
        repeat(artistsSyncResult.attempted) { counted(it < artistsSyncResult.sent) }

        val favoritePodcasts = podcastRepository.getFavoritePodcasts().first().take(MAX_SYNC_ITEMS)
        val podcastsSyncResult =
            syncCategoryIfChanged(
                signatureKey = WearCompanionBridge.KEY_AUTO_SYNC_SIG_PODCASTS,
                signature = hashText(favoritePodcasts.joinToString("|") { "${it.podcastId}:${it.isFavorite}" }),
            ) {
                syncCollection(node.id, WearCompanionBridge.ACTION_SYNC_PODCAST, favoritePodcasts, ::podcastToJson)
            }
        categoryStats["podcasts"] = podcastsSyncResult
        changedCategories += if (podcastsSyncResult.changed) 1 else 0
        repeat(podcastsSyncResult.attempted) { counted(it < podcastsSyncResult.sent) }

        val downloadPayload = buildDownloadPayload()
        val downloadSyncResult =
            syncCategoryIfChanged(
                signatureKey = WearCompanionBridge.KEY_AUTO_SYNC_SIG_DOWNLOADS,
                signature = hashText(downloadPayload.toString()),
            ) {
                1 to if (sendActionToNode(node.id, WearCompanionBridge.ACTION_SYNC_DOWNLOAD, downloadPayload)) 1 else 0
            }
        categoryStats["downloads"] = downloadSyncResult
        changedCategories += if (downloadSyncResult.changed) 1 else 0
        repeat(downloadSyncResult.attempted) { counted(it < downloadSyncResult.sent) }

        appendLog("Auto-sync delta changed $changedCategories categories, delivered $sent/$attempted payloads.")
        persistAutoSyncStats(
            status =
                when {
                    attempted == 0 -> "noop"
                    sent == attempted -> "success"
                    sent == 0 -> "retry"
                    else -> "partial"
                },
            reason = "",
            attempted = attempted,
            sent = sent,
            changedCategories = changedCategories,
            categoryStats = categoryStats,
        )
        return when {
            attempted == 0 -> Result.success()
            sent == attempted -> Result.success()
            sent == 0 -> Result.retry()
            else -> Result.retry()
        }
    }

    private suspend fun <T> syncCollection(
        nodeId: String,
        action: String,
        items: List<T>,
        mapper: (T) -> JSONObject,
    ): Pair<Int, Int> {
        val payloads = items.map(mapper)
        return syncJsonPayloadCollection(nodeId, action, payloads)
    }

    private suspend fun syncJsonPayloadCollection(
        nodeId: String,
        action: String,
        payloads: List<JSONObject>,
    ): Pair<Int, Int> {
        var attempted = 0
        var sent = 0
        payloads.forEach { payload ->
            attempted++
            if (sendActionToNode(nodeId, action, payload)) {
                sent++
            }
        }
        return attempted to sent
    }

    private suspend fun syncCategoryIfChanged(
        signatureKey: String,
        signature: String,
        sync: suspend () -> Pair<Int, Int>,
    ): CategorySyncResult {
        val previous = dataStoreManager.getString(signatureKey).first().orEmpty()
        if (previous == signature) return CategorySyncResult()
        val (attempted, sent) = sync()
        if (attempted == sent) {
            dataStoreManager.putString(signatureKey, signature)
        }
        return CategorySyncResult(
            attempted = attempted,
            sent = sent,
            changed = true,
        )
    }

    private fun hashText(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun isDeviceCharging(): Boolean {
        val batteryManager = applicationContext.getSystemService(BatteryManager::class.java) ?: return false
        return batteryManager.isCharging
    }

    private fun isActiveNetworkUnmetered(): Boolean {
        val connectivityManager = applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private suspend fun getConnectedNodes(): List<Node> {
        val nodeClient = Wearable.getNodeClient(applicationContext)
        return suspendCancellableCoroutine { continuation ->
            nodeClient.connectedNodes
                .addOnSuccessListener { nodes ->
                    if (continuation.isActive) continuation.resume(nodes)
                }.addOnFailureListener {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        }
    }

    private suspend fun sendActionToNode(
        nodeId: String,
        action: String,
        payload: JSONObject,
    ): Boolean {
        val body =
            JSONObject()
                .put("requestId", UUID.randomUUID().toString())
                .put("action", action)
                .put("payload", payload)
                .toString()
                .toByteArray(Charsets.UTF_8)
        return suspendCancellableCoroutine { continuation ->
            Wearable
                .getMessageClient(applicationContext)
                .sendMessage(nodeId, WearCompanionBridge.PATH_REQUEST, body)
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(true)
                }.addOnFailureListener {
                    if (continuation.isActive) continuation.resume(false)
                }
        }
    }

    private suspend fun appendLog(message: String) {
        val current = dataStoreManager.getString(WearCompanionBridge.KEY_LOG).first().orEmpty()
        val timestamp = LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
        val updated =
            if (current.isBlank()) {
                "[$timestamp] $message"
            } else {
                "$current\n[$timestamp] $message"
            }
        dataStoreManager.putString(WearCompanionBridge.KEY_LOG, updated.takeLast(MAX_LOG_CHARS))
    }

    private suspend fun persistAutoSyncStats(
        status: String,
        reason: String,
        attempted: Int = 0,
        sent: Int = 0,
        changedCategories: Int = 0,
        categoryStats: Map<String, CategorySyncResult> = emptyMap(),
    ) {
        val categories =
            JSONObject().apply {
                categoryStats.forEach { (name, result) ->
                    put(
                        name,
                        JSONObject()
                            .put("attempted", result.attempted)
                            .put("sent", result.sent)
                            .put("changed", result.changed),
                    )
                }
            }
        val stats =
            JSONObject()
                .put("timestamp", System.currentTimeMillis())
                .put("status", status)
                .put("reason", reason)
                .put("attempted", attempted)
                .put("sent", sent)
                .put("changedCategories", changedCategories)
                .put("categories", categories)
                .toString()
        dataStoreManager.putString(WearCompanionBridge.KEY_AUTO_SYNC_LAST_STATS, stats)
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

    companion object {
        private const val MAX_SYNC_ITEMS = 60
        private const val MAX_LOG_CHARS = 16000
        const val UNIQUE_WORK_NAME = "watch_companion_auto_sync"
    }
}

private data class CategorySyncResult(
    val attempted: Int = 0,
    val sent: Int = 0,
    val changed: Boolean = false,
)
