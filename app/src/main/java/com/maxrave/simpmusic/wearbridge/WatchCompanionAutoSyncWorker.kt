package com.maxrave.simpmusic.wearbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.maxrave.common.BridgeRequests
import com.maxrave.common.collectionFingerprint
import com.maxrave.common.nodeSyncKey
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

        val nodes = getConnectedNodes()
        if (nodes.isEmpty()) {
            persistAutoSyncStats("skipped", "No connected watch.")
            return Result.success()
        }
        val collections = listOf(
            SyncCollection("songs", WearCompanionBridge.ACTION_SYNC_SONG, "videoId", songRepository.getLikedSongs().first().map(::songToJson)),
            SyncCollection("playlists", WearCompanionBridge.ACTION_SYNC_PLAYLIST, "id", playlistRepository.getLikedPlaylists().first().map(::playlistToJson)),
            SyncCollection("albums", WearCompanionBridge.ACTION_SYNC_ALBUM, "browseId", albumRepository.getLikedAlbums().first().map(::albumToJson)),
            SyncCollection("artists", WearCompanionBridge.ACTION_SYNC_ARTIST, "channelId", artistRepository.getFollowedArtists().first().map {
                JSONObject().put("channelId", it.channelId).put("name", it.name).put("thumbnails", it.thumbnails ?: "").put("followed", it.followed)
            }),
            SyncCollection("podcasts", WearCompanionBridge.ACTION_SYNC_PODCAST, "podcastId", podcastRepository.getFavoritePodcasts().first().map(::podcastToJson)),
        )
        var attempted = 0
        var applied = 0
        var changed = 0
        var failed = false
        var pendingPages = false
        val stats = linkedMapOf<String, CategorySyncResult>()
        for (node in nodes) {
            val status = requestAction(node.id, WearCompanionBridge.ACTION_STATUS, JSONObject())
            val capabilities = status?.data?.let { runCatching { JSONObject(it) }.getOrNull() }
            val generation = capabilities?.optString("libraryGeneration").orEmpty()
            if (status?.ok != true || generation.isBlank()) { failed = true; continue }
            // Session refresh is intentionally explicit. A background library job must not undo watch logout.
            for (collection in collections) {
                val all = collection.payloads.sortedBy { it.optString(collection.idField) }
                val signature = collectionFingerprint(all)
                val key = nodeSyncKey(collection.category, node.id + ":" + generation)
                if (dataStoreManager.getString(key).first() == signature) continue
                val cursor = dataStoreManager.getString("${key}_cursor").first().orEmpty().split(":")
                val offset = if (cursor.firstOrNull() == signature) cursor.getOrNull(1)?.toIntOrNull()?.coerceIn(0, all.size) ?: 0 else 0
                val payloads = all.drop(offset).take(MAX_SYNC_ITEMS)
                val nextOffset = offset + payloads.size
                val complete = nextOffset == all.size
                changed++
                var count = 0
                var success = true
                for (payload in payloads) {
                    attempted++; count++
                    if (sendActionToNode(node.id, collection.action, payload)) applied++ else {
                        success = false; break // Stop a disconnected watch from consuming the entire worker budget.
                    }
                }
                if (success) {
                    attempted++; count++
                    val manifest = JSONObject().put("category", collection.category)
                        .put("complete", complete).put("revision", signature).put("offset", offset)
                        .put("ids", JSONArray(payloads.map { it.getString(collection.idField) }))
                    success = sendActionToNode(node.id, WearCompanionBridge.ACTION_RECONCILE, manifest)
                    if (success) applied++
                }
                if (success) {
                    if (complete) {
                        dataStoreManager.putString(key, signature)
                        dataStoreManager.putString("${key}_cursor", "")
                    } else {
                        dataStoreManager.putString("${key}_cursor", "$signature:$nextOffset")
                        pendingPages = true
                    }
                } else failed = true
                stats[nodeSyncKey(collection.category, node.id)] = CategorySyncResult(count, count - if (success) 0 else 1, true)
                if (!success) break
            }
        }
        appendLog("Auto-sync: watch confirmed $applied/$attempted requests across ${nodes.size} watch(es).")
        persistAutoSyncStats(if (failed || pendingPages) "retry" else if (attempted == 0) "noop" else "success", "",
            attempted, applied, changed, stats)
        return if (failed || pendingPages) Result.retry() else Result.success()
    }

    private data class SyncCollection(val category: String, val action: String, val idField: String, val payloads: List<JSONObject>)

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
    ): Boolean = requestAction(nodeId, action, payload)?.ok == true

    private suspend fun requestAction(nodeId: String, action: String, payload: JSONObject): com.maxrave.common.BridgeAcknowledgement? {
        val requestId = UUID.randomUUID().toString()
        val body = JSONObject().put("protocolVersion", WearCompanionBridge.PROTOCOL_VERSION)
            .put("requestId", requestId).put("action", action).put("payload", payload).toString().toByteArray(Charsets.UTF_8)
        if (body.size > 100_000) return null
        return BridgeRequests.sendAndAwait(nodeId, requestId, action) {
            suspendCancellableCoroutine { continuation ->
                Wearable.getMessageClient(applicationContext).sendMessage(nodeId, WearCompanionBridge.PATH_REQUEST, body)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(true) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(false) }
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
