package com.maxrave.simpmusic.wear

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.simpmusic.wear.offline.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.browse.playlist.Author
import com.maxrave.domain.utils.Resource
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/** Account-free persistence/ownership checks. Run only with offlinePlaylists=true on an isolated install. */
@RunWith(AndroidJUnit4::class)
class OfflinePlaylistsTest {
    private lateinit var context: Context
    private lateinit var store: OfflinePlaylists

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.maxrave.simpmusic.test.dev")
        assumeTrue(InstrumentationRegistry.getArguments().getString("offlinePlaylists") == "true")
        val factory = object : WorkerFactory() {
            override fun createWorker(context: Context, workerClassName: String, parameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == OfflinePlaylistsWorker::class.java.name) object : Worker(context, parameters) {
                    override fun doWork(): Result = Result.success()
                } else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        context.getSharedPreferences("offline_playlists", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("watch_download_policy", Context.MODE_PRIVATE).edit().clear().commit()
        store = OfflinePlaylists(context)
    }

    private fun track() = Track(album = null, artists = null, duration = "1:00", durationSeconds = 60,
        isAvailable = true, isExplicit = false, likeStatus = null, thumbnails = null, title = "Sample track",
        videoId = "sample-track", videoType = null, category = null, feedbackTokens = null, resultType = null, year = null)

    @Test fun pinAndSettingsSurviveNewStoreInstance() = runBlocking {
        store.pin("playlist-one", "Saved playlist", listOf(track()))
        store.settings(256, false)
        val restored = OfflinePlaylists(context).observe().first()
        assertEquals("Saved playlist", restored.playlists.single().title)
        assertEquals("sample-track", restored.playlists.single().tracks.single().videoId)
        assertEquals(256, restored.budgetMb)
        assertFalse(restored.removeStale)
        context.getSharedPreferences("offline_queue", Context.MODE_PRIVATE).edit().putBoolean("charging", true).commit()
        context.getSharedPreferences("watch_download_policy", Context.MODE_PRIVATE).edit().putBoolean("charging", false).commit()
        assertFalse("Saved queues must reflect a newer global policy", OfflineQueue(context).chargingOnly())
        assertFalse(store.chargingOnly())
    }

    @Test fun manualDownloadRelinquishesAutomaticOwnership() = runBlocking {
        val prefs = context.getSharedPreferences("offline_playlists", Context.MODE_PRIVATE)
        prefs.edit().putString("state", Json.encodeToString(OfflinePlaylistState(owned = mapOf("sample-track" to 100L)))).commit()
        store.manualDownload("sample-track")
        assertTrue(store.observe().first().owned.isEmpty())
    }

    @Test fun unpinKeepsFilesAndRelinquishesOwnership() = runBlocking {
        store.pin("playlist-one", "Saved playlist", listOf(track()))
        val prefs = context.getSharedPreferences("offline_playlists", Context.MODE_PRIVATE)
        val state = store.observe().first().copy(owned = mapOf("sample-track" to 100L))
        prefs.edit().putString("state", Json.encodeToString(state)).commit()
        val file = java.io.File(context.filesDir, "manual-offline-fixture").apply { writeText("keep") }
        try {
            store.unpin("playlist-one")
            val restored = store.observe().first()
            assertTrue(restored.playlists.isEmpty())
            assertTrue(restored.owned.isEmpty())
            assertEquals("keep", file.readText())
        } finally { file.delete() }
    }

    @Test fun unreadableRegistryPausesCleanupWithoutBlockingManualDownload() = runBlocking {
        val prefs = context.getSharedPreferences("offline_playlists", Context.MODE_PRIVATE)
        prefs.edit().putString("state", "{invalid").commit()
        store.manualDownload("sample-track")
        assertTrue(prefs.getBoolean("cleanup_paused", false))
        prefs.edit().putString("state", Json.encodeToString(OfflinePlaylistState(owned = mapOf("sample-track" to 100L), removeStale = true))).commit()
        store.settings(512, false)
        assertTrue(store.observe().first().owned.isEmpty())
        assertFalse(prefs.getBoolean("cleanup_paused", false))
    }

    @Test fun incompletePlaylistCannotReplaceSavedMusicOrTriggerCleanup() = runBlocking {
        val app = context.applicationContext as WearSimpMusicApplication
        app.preparePlaybackCaches()
        val koin = GlobalContext.get()
        val originalPlaylists = koin.get<PlaylistRepository>()
        val originalDownloads = withContext(Dispatchers.Main) { koin.get<DownloadHandler>() }
        val removed = mutableListOf<String>()
        val fakeDownloads = object : DownloadHandler {
            override val downloads = MutableStateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>(emptyMap())
            override val downloadTask = MutableStateFlow<Map<String, Int>>(emptyMap())
            override suspend fun completedAudioIds() = setOf("sample-track")
            override suspend fun audioDownloadInventory() = mapOf("sample-track" to DownloadHandler.AudioDownload(DownloadHandler.STATE_COMPLETED, 100, 100))
            override suspend fun setWatchDownloadPolicy(chargingOnly: Boolean) = Unit
            override suspend fun downloadTrack(videoId: String, title: String, thumbnail: String, audioOnly: Boolean, automatic: Boolean) {
                fail("Incomplete response must not schedule new tracks")
            }
            override fun removeDownload(videoId: String) { removed += videoId }
            override fun removeAllDownloads() { fail("Must not clear downloads") }
        }
        var completeResponse = false
        val fakePlaylists = object : PlaylistRepository by originalPlaylists {
            override fun getFullPlaylistData(playlistId: String) = flowOf(Resource.Success(
                PlaylistBrowse(Author("", ""), null, "", 0, playlistId, "PUBLIC", emptyList(), "Saved playlist",
                    0, emptyList(), "2026", isComplete = completeResponse)))
        }
        loadKoinModules(module {
            single<PlaylistRepository> { fakePlaylists }
            single<DownloadHandler> {
                assertEquals("Downloader creation must stay on Main", android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                fakeDownloads
            }
        })
        try {
            val playlist = OfflinePlaylist("playlist-one", "Saved playlist", listOf(track()), "revision")
            val state = OfflinePlaylistState(listOf(playlist), owned = mapOf("sample-track" to 100L), removeStale = true)
            context.getSharedPreferences("offline_playlists", Context.MODE_PRIVATE).edit()
                .putString("state", Json.encodeToString(state)).commit()
            assertFalse(store.sync())
            val restored = store.observe().first()
            assertEquals("sample-track", restored.playlists.single().tracks.single().videoId)
            assertTrue(removed.isEmpty())
            assertTrue("sample-track" in restored.owned)
            completeResponse = true
            assertTrue(store.sync())
            assertTrue(store.observe().first().playlists.single().tracks.isEmpty())
            assertEquals(listOf("sample-track"), removed)
        } finally {
            loadKoinModules(module {
                single<PlaylistRepository> { originalPlaylists }
                single<DownloadHandler> { originalDownloads }
            })
        }
    }
}
