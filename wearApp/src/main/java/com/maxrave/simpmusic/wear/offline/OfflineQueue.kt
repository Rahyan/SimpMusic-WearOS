package com.maxrave.simpmusic.wear.offline

import android.content.Context
import android.os.StatFs
import androidx.work.*
import com.maxrave.common.Config
import com.maxrave.common.WatchOfflinePack
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.toSongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One pinned queue with durable download requests; ready means audio verified in the watch cache. */
class OfflineQueue(private val context: Context) {
    // Shared by UI, Data Layer and WorkManager instances in this process.
    private companion object { val mutation = Mutex() }
    private val prefs = context.getSharedPreferences("offline_queue", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    fun tracks(): List<Track> = runCatching { json.decodeFromString<List<Track>>(prefs.getString("tracks", "[]")!!) }.getOrDefault(emptyList())
    fun revision(): String = prefs.getString("revision", "").orEmpty()
    fun chargingOnly(): Boolean = context.getSharedPreferences("watch_download_policy", Context.MODE_PRIVATE)
        .getBoolean("charging", prefs.getBoolean("charging", true))
    fun message(value: String) { prefs.edit().putString("message", value).apply() }
    suspend fun <T> withRevision(expected: String, block: suspend () -> T): T? = mutation.withLock {
        if (revision() != expected) null else block()
    }

    suspend fun prepare(tracks: List<Track>, chargingOnly: Boolean) = withContext(Dispatchers.IO) {
        mutation.withLock {
        com.maxrave.common.validateOfflineQueue(tracks.map { com.maxrave.common.OfflineTrackEstimate(it.videoId, it.title, it.durationSeconds) })
        val revision = UUID.randomUUID().toString()
        check(prefs.edit().putString("tracks", json.encodeToString(tracks)).putString("revision", revision)
            .putBoolean("charging", chargingOnly).putString("message", "Preparing download requests…").commit())
        updateDownloadPolicy(chargingOnly)
        enqueue(revision)
        }
    }

    suspend fun retry(chargingOnly: Boolean) = withContext(Dispatchers.IO) {
        mutation.withLock {
        require(tracks().isNotEmpty()) { "Prepare an offline queue first." }
        check(prefs.edit().putBoolean("charging", chargingOnly).putString("message", "Preparing download requests…").commit())
        updateDownloadPolicy(chargingOnly)
        enqueue(revision())
        }
    }
    private suspend fun updateDownloadPolicy(chargingOnly: Boolean) {
        (context.applicationContext as com.maxrave.simpmusic.wear.WearSimpMusicApplication).preparePlaybackCaches()
        val downloads: DownloadHandler = withContext(Dispatchers.Main.immediate) { GlobalContext.get().get() }
        downloads.setWatchDownloadPolicy(chargingOnly)
    }

    private fun enqueue(revision: String) {
        val request = OneTimeWorkRequestBuilder<OfflineQueueWorker>()
            .setInputData(workDataOf("revision" to revision))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork("watch-offline-queue", ExistingWorkPolicy.REPLACE, request)
    }
    suspend fun readyTracks(handler: DownloadHandler): List<Track> {
        val ready = handler.completedAudioIds()
        return tracks().filter { it.videoId in ready }
    }
    suspend fun status(handler: DownloadHandler): WatchOfflinePack {
        return mutation.withLock {
        val tracks = tracks()
        val ready = readyTracks(handler)
        val failed = tracks.count { handler.downloads.value[it.videoId]?.first?.state == DownloadHandler.STATE_FAILED }
        WatchOfflinePack(revision(), tracks.size, ready.size, ready.sumOf { (it.durationSeconds ?: 0).coerceAtLeast(0) },
            chargingOnly(), when {
                tracks.isEmpty() -> "Prepare a queue to listen without your phone."
                ready.size == tracks.size -> "Ready to go"
                failed > 0 -> "$failed downloads need retrying"
                else -> prefs.getString("message", "Waiting for downloads").orEmpty()
            })
        }
    }
    suspend fun play(handler: MediaPlayerHandler) {
        mutation.withLock {
        val downloads: DownloadHandler = withContext(Dispatchers.Main.immediate) { GlobalContext.get().get() }
        val ready = withContext(Dispatchers.IO) { readyTracks(downloads) }
        require(ready.isNotEmpty()) { "No verified offline tracks are ready yet." }
        withContext(Dispatchers.Main.immediate) {
            handler.resetSongAndQueue()
            handler.setQueueData(QueueData.Data(ready, ready.first(), "wear_offline_queue", "Offline queue", PlaylistType.PLAYLIST, null))
            handler.loadMediaItem(ready.first(), Config.PLAYLIST_CLICK, 0)
        }
        }
    }
}

class OfflineQueueWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val store = OfflineQueue(applicationContext)
        val expected = inputData.getString("revision") ?: return Result.failure()
        if (store.revision() != expected) return Result.success()
        return try {
            (applicationContext as com.maxrave.simpmusic.wear.WearSimpMusicApplication).preparePlaybackCaches()
            val downloads: DownloadHandler = withContext(Dispatchers.Main.immediate) { GlobalContext.get().get() }
            val songs: SongRepository = GlobalContext.get().get()
            // Policy is applied when requested and restored by the downloader on cold creation.
            // An old background retry must not overwrite a newer global choice.
            val ready = downloads.completedAudioIds()
            for (track in store.tracks().distinctBy { it.videoId }) {
                if (track.videoId in ready) continue
                val scheduled = store.withRevision(expected) {
                if (StatFs(applicationContext.filesDir.absolutePath).availableBytes < 128L * 1024 * 1024) {
                    store.message("Low storage · Free space and retry")
                    return@withRevision false
                }
                songs.insertSong(track.toSongEntity().copy(downloadState = DownloadState.STATE_NOT_DOWNLOADED)).first()
                downloads.downloadTrack(track.videoId, track.title, track.thumbnails?.lastOrNull()?.url.orEmpty(), audioOnly = true)
                true
                }
                if (scheduled == null) return Result.success()
                if (!scheduled) return Result.failure()
            }
            store.withRevision(expected) { store.message(if (store.chargingOnly()) "Downloads wait for Wi-Fi and charging" else "Downloading when connected") }
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            store.withRevision(expected) { store.message("Couldn’t schedule all downloads · Retry") }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

fun WatchOfflinePack.toJson(): JSONObject = JSONObject().put("revision", revision).put("total", total).put("ready", ready)
    .put("readySeconds", readySeconds).put("chargingOnly", chargingOnly).put("message", message)
