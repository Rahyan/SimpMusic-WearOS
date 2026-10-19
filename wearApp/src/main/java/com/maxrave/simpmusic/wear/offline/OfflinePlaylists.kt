package com.maxrave.simpmusic.wear.offline

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import androidx.work.*
import com.maxrave.common.OfflineTrackEstimate
import com.maxrave.common.estimatedOfflineBytes
import com.maxrave.common.planOfflinePlaylists
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.simpmusic.wear.WearSimpMusicApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class OfflinePlaylist(
    val id: String,
    val title: String,
    val tracks: List<Track>,
    val revision: String,
    val updatedAt: Long = 0,
    val message: String = "Waiting to sync",
)

@Serializable
data class OfflinePlaylistState(
    val playlists: List<OfflinePlaylist> = emptyList(),
    val owned: Map<String, Long> = emptyMap(),
    val budgetMb: Int = 512,
    val removeStale: Boolean = false,
    val message: String = "",
)

/** Saved selections and ownership are independent of Room and the manually pinned offline queue. */
class OfflinePlaylists(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("offline_playlists", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun read(): OfflinePlaylistState {
        val raw = prefs.getString("state", null) ?: "{}"
        decoded?.takeIf { it.first == raw }?.let { return it.second }
        return json.decodeFromString<OfflinePlaylistState>(raw).also { decoded = raw to it }
    }

    private fun write(state: OfflinePlaylistState) {
        val raw = json.encodeToString(state)
        check(prefs.edit().putString("state", raw).commit()) { "Could not save offline playlists." }
        decoded = raw to state
    }

    fun observe(): Flow<OfflinePlaylistState> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "state" || key == "cleanup_paused") trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().map { withContext(Dispatchers.IO) {
        val state = read()
        if (prefs.getBoolean("cleanup_paused", false)) state.copy(message = "Auto cleanup paused. Switch it off to reset safely.") else state
    } }
        .catch { emit(OfflinePlaylistState(message = "Couldn’t read saved playlists. Try reopening the app.")) }

    suspend fun pin(id: String, title: String, tracks: List<Track>) = withContext(Dispatchers.IO) {
        require(id.isNotBlank() && id.length <= 512 && title.isNotBlank()) { "Playlist details are unavailable." }
        require(tracks.isNotEmpty() && tracks.size <= 100) { "Choose a playlist with 1–100 tracks." }
        require(tracks.all { it.videoId.isNotBlank() && it.title.isNotBlank() }) { "Playlist details are incomplete." }
        mutation.withLock {
            val state = read()
            require(state.playlists.size < 8 || state.playlists.any { it.id == id }) { "You can keep up to eight playlists offline." }
            write(state.copy(playlists = state.playlists.filterNot { it.id == id } +
                OfflinePlaylist(id, title.take(512), tracks, UUID.randomUUID().toString()), message = "Waiting to sync"))
        }
        requestRefresh()
    }

    suspend fun unpin(id: String) = withContext(Dispatchers.IO) {
        mutation.withLock {
            val state = read()
            val removed = state.playlists.firstOrNull { it.id == id } ?: return@withLock
            val remaining = state.playlists.filterNot { it.id == id }
            val keepOwned = remaining.flatMap { it.tracks }.mapTo(mutableSetOf()) { it.videoId }
            // Stopping sync keeps the files; relinquish ownership rather than deleting them later.
            val promoted = removed.tracks.map { it.videoId }.toSet() - keepOwned
            write(state.copy(playlists = remaining, owned = if (remaining.isEmpty()) emptyMap() else state.owned - promoted))
        }
        requestRefresh()
    }

    suspend fun manualDownload(id: String) = withContext(Dispatchers.IO) {
        mutation.withLock {
            try { val state = read(); if (id in state.owned) write(state.copy(owned = state.owned - id)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Preserve manual downloads even if the registry cannot be read or updated.
                prefs.edit().putBoolean("cleanup_paused", true).commit()
            }
        }
    }

    suspend fun settings(budgetMb: Int, removeStale: Boolean) = withContext(Dispatchers.IO) {
        require(budgetMb in setOf(256, 512, 1024))
        mutation.withLock {
            val state = read()
            val reset = !removeStale && prefs.getBoolean("cleanup_paused", false)
            write(state.copy(budgetMb = budgetMb, removeStale = removeStale, owned = if (reset) emptyMap() else state.owned))
            if (reset) check(prefs.edit().remove("cleanup_paused").commit())
        }
        requestRefresh()
    }

    fun chargingOnly(): Boolean = context.getSharedPreferences("watch_download_policy", Context.MODE_PRIVATE).getBoolean("charging", true)

    fun observePolicy(): Flow<Boolean> = callbackFlow {
        val policy = context.getSharedPreferences("watch_download_policy", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "charging") trySend(chargingOnly()) }
        policy.registerOnSharedPreferenceChangeListener(listener)
        trySend(chargingOnly())
        awaitClose { policy.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    suspend fun requestRefresh() = withContext(Dispatchers.IO) {
        ensureScheduled()
        if (mutation.withLock { read().playlists.isEmpty() }) return@withContext
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OfflinePlaylistsWorker>().setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
    }

    suspend fun ensureScheduled() = withContext(Dispatchers.IO) {
        val state = mutation.withLock { read() }
        if (state.playlists.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        } else {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<OfflinePlaylistsWorker>(6, TimeUnit.HOURS).setConstraints(constraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
        }
    }

    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (chargingOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(chargingOnly()).setRequiresStorageNotLow(true).build()

    suspend fun play(id: String, handler: MediaPlayerHandler) = withContext(Dispatchers.IO) {
        mutation.withLock {
            val playlist = read().playlists.firstOrNull { it.id == id } ?: error("This playlist is no longer kept offline.")
            val downloads: DownloadHandler = withContext(Dispatchers.Main.immediate) { GlobalContext.get().get() }
            val ready = downloads.completedAudioIds()
            val tracks = playlist.tracks.filter { it.videoId in ready }
            require(tracks.isNotEmpty()) { "No verified offline tracks are ready yet." }
            withContext(Dispatchers.Main.immediate) {
                handler.resetSongAndQueue()
                handler.setQueueData(QueueData.Data(tracks, tracks.first(), "wear_offline_playlist:$id", playlist.title, PlaylistType.PLAYLIST, null))
                handler.loadMediaItem(tracks.first(), com.maxrave.common.Config.PLAYLIST_CLICK, 0)
            }
        }
    }

    suspend fun sync(): Boolean = synchronization.withLock syncLock@ {
        val initial = mutation.withLock { read() }
        if (initial.playlists.isEmpty()) return@syncLock true
        val repo: PlaylistRepository = GlobalContext.get().get()
        var refreshFailed = false
        for (playlist in initial.playlists) {
            var failureMessage = "Couldn’t refresh. Keeping saved music."
            try {
                val result = withTimeoutOrNull(60_000) { repo.getFullPlaylistData(playlist.id).first() } ?: error("Refresh timed out")
                val full = (result as? Resource.Success)?.data ?: error("Playlist unavailable")
                check(full.isComplete) { "Incomplete playlist metadata" }
                if (full.tracks.size > 100) failureMessage = "Over 100 tracks. Keeping previously saved music."
                require(full.tracks.size <= 100 && full.tracks.all { it.videoId.isNotBlank() && it.title.isNotBlank() })
                mutation.withLock {
                    val state = read()
                    write(state.copy(playlists = state.playlists.map {
                        if (it.id == playlist.id && it.revision == playlist.revision)
                            it.copy(title = full.title.take(512), tracks = full.tracks, updatedAt = System.currentTimeMillis(), message = "Up to date") else it
                    }))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                refreshFailed = true
                mutation.withLock {
                    val state = read()
                    write(state.copy(playlists = state.playlists.map {
                        if (it.id == playlist.id && it.revision == playlist.revision)
                            it.copy(message = failureMessage) else it
                    }))
                }
            }
        }
        mutation.withLock commitLock@ {
            var state = read()
            if (state.playlists.isEmpty()) return@commitLock
            val app = context as WearSimpMusicApplication
            app.preparePlaybackCaches()
            val downloads: DownloadHandler = withContext(Dispatchers.Main.immediate) { GlobalContext.get().get() }
            val songs: SongRepository = GlobalContext.get().get()
            val inventory = downloads.audioDownloadInventory()
            val ready = downloads.completedAudioIds()
            val tracks = state.playlists.flatMap { it.tracks }.distinctBy { it.videoId }
            val estimates = tracks.associate { it.videoId to estimatedOfflineBytes(OfflineTrackEstimate(it.videoId, it.title, it.durationSeconds)) }
            val activeIds = withContext(Dispatchers.Main.immediate) {
                val handler = app.playbackHandler
                handler?.queueData?.value?.data?.listTracks.orEmpty().mapTo(mutableSetOf()) { it.videoId }.apply {
                    handler?.player?.currentMediaItem?.mediaId?.let(::add)
                }
            }
            val pinned = OfflineQueue(context)
            val pinnedRevision = pinned.revision()
            val protected = pinned.tracks().mapTo(mutableSetOf()) { it.videoId } + activeIds
            val reserved = state.owned.mapValues { (id, estimate) ->
                val file = inventory[id]
                if (id in ready && file != null) file.bytes else maxOf(file?.contentLength ?: 0, estimate)
            }
            val busy = inventory.filterValues { it.state in setOf(DownloadHandler.STATE_QUEUED, DownloadHandler.STATE_DOWNLOADING,
                DownloadHandler.STATE_STOPPED, DownloadHandler.STATE_RESTARTING, DownloadHandler.STATE_REMOVING) }.keys + ready
            var plan = planOfflinePlaylists(tracks.map { OfflineTrackEstimate(it.videoId, it.title, it.durationSeconds) },
                state.owned, inventory.keys, busy, protected, reserved, state.budgetMb.toLong() * 1024 * 1024,
                state.removeStale && !prefs.getBoolean("cleanup_paused", false))
            val removed = mutableSetOf<String>()
            for (id in plan.removeIds) {
                withContext(Dispatchers.Main.immediate) {
                    // Recheck live references on the player's thread immediately before removal.
                    val handler = app.playbackHandler
                    val inQueue = handler?.player?.currentMediaItem?.mediaId == id ||
                        handler?.queueData?.value?.data?.listTracks.orEmpty().any { it.videoId == id }
                    if (!inQueue && pinned.revision() == pinnedRevision) {
                        downloads.removeDownload(id)
                        removed += id
                    }
                }
            }
            state = state.copy(owned = state.owned - removed)
            // Music retained by a newly changed queue must still consume the download budget.
            plan = planOfflinePlaylists(tracks.map { OfflineTrackEstimate(it.videoId, it.title, it.durationSeconds) },
                state.owned, inventory.keys, busy, protected, reserved, state.budgetMb.toLong() * 1024 * 1024, false)
            write(state)
            var available = StatFs(context.filesDir.absolutePath).availableBytes - 128L * 1024 * 1024
            available -= inventory.filterValues { it.state != DownloadHandler.STATE_COMPLETED }.entries.sumOf { (id, file) ->
                (maxOf(file.contentLength, estimates[id] ?: state.owned[id] ?: 0) - file.bytes).coerceAtLeast(0)
            }
            var lowStorage = false
            val requests = mutableListOf<Track>()
            for (id in plan.downloadIds) {
                val track = tracks.first { it.videoId == id }
                val estimate = estimates.getValue(id)
                // Existing failed requests were reserved above too. Do not restart them when
                // those reservations would consume the device's remaining storage reserve.
                if ((id !in inventory && estimate > available) || (id in inventory && available < 0)) {
                    lowStorage = true
                    continue
                }
                requests += track
                if (id !in inventory) available -= estimate
            }
            val requestIds = requests.mapTo(mutableSetOf()) { it.videoId }
            state = state.copy(owned = state.owned + plan.ownedEstimates.filterKeys { it in requestIds },
                message = "Preparing download requests…")
            // One atomic reservation avoids serializing the entire catalog once per track.
            write(state)
            for (track in requests) {
                if (songs.getSongById(track.videoId).first() == null) songs.insertSong(track.toSongEntity()).first()
                downloads.downloadTrack(track.videoId, track.title, track.thumbnails?.lastOrNull()?.url.orEmpty(), audioOnly = true, automatic = true)
            }
            write(state.copy(message = when {
                lowStorage -> "Low storage. Free space to continue."
                plan.budgetBlocked -> "Auto-download budget reached. Increase it or stop syncing a playlist."
                refreshFailed -> "Some playlists couldn’t refresh. Saved music is still available."
                chargingOnly() -> "Syncs on Wi-Fi while charging"
                else -> "Syncs when connected"
            }))
        }
        !refreshFailed
    }

    companion object {
        @Volatile private var decoded: Pair<String, OfflinePlaylistState>? = null
        private val mutation = Mutex()
        private val synchronization = Mutex()
        private const val PERIODIC = "watch-offline-playlists-periodic"
        private const val NOW = "watch-offline-playlists-refresh"
        fun trimMemory() { decoded = null }
    }
}

class OfflinePlaylistsWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if (OfflinePlaylists(applicationContext).sync()) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
}
