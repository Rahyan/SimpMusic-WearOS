package com.maxrave.simpmusic.wear

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.simpmusic.wear.offline.OfflineQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

/** Opt-in hardware checks; no tokens or URLs leave the test process. Uses only isolated test apps. */
class PhysicalPlaybackTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun permitted() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("physicalPlayback") == "true")
        check(isIsolatedTestPackage(context.packageName))
    }
    private fun track(id: String, title: String, seconds: Int) = Track(
        album = null, artists = listOf(Artist(null, "Hardware check")), duration = null, durationSeconds = seconds,
        isAvailable = true, isExplicit = false, likeStatus = null, thumbnails = null, title = title, videoId = id,
        videoType = null, category = null, feedbackTokens = null, resultType = null, year = null)
    private suspend fun start(handler: MediaPlayerHandler, track: Track) = withContext(Dispatchers.Main) {
        handler.resetSongAndQueue()
        handler.loadMediaItem(track, Config.SONG_CLICK, null)
        assertEquals(track.videoId, handler.queueData.value!!.data.listTracks.first().videoId)
        assertEquals(track.videoId, handler.player.currentMediaItem!!.mediaId)
    }
    private suspend fun waitPlaying(handler: MediaPlayerHandler) = withTimeout(120_000) {
        while (!withContext(Dispatchers.Main) { handler.player.isPlaying && handler.player.duration > 0 }) delay(500)
    }
    private suspend fun completeTrack(handler: MediaPlayerHandler, label: String) {
        withContext(Dispatchers.Main) { handler.player.play() }
        waitPlaying(handler)
        val duration = withContext(Dispatchers.Main) { handler.player.duration }
        assertTrue("Unexpectedly short stream", duration > 150_000)
        withContext(Dispatchers.Main) { handler.player.seekTo(0); handler.player.play() }
        withTimeout(duration + 180_000) {
            var reached = 0L
            var lastReport = -1L
            while (reached < duration - 2_000) {
                delay(1_000)
                reached = withContext(Dispatchers.Main) { handler.player.currentPosition }
                val minute = reached / 60_000
                if (minute != lastReport) { lastReport = minute; Log.i("WearDeviceCheck", "$label minute=$minute durationMs=$duration") }
            }
        }
        Log.i("WearDeviceCheck", "$label full track reached")
        withContext(Dispatchers.Main) { handler.player.pause() }
    }
    @Test fun sustainedSignedAndGuestPlaybackSeekAndOfflineQueue(): Unit = runBlocking {
        permitted()
        val handler: MediaPlayerHandler = GlobalContext.get().get()
        val data: DataStoreManager = GlobalContext.get().get()
        val cookie = data.cookie.first()
        val pageId = data.pageId.first()
        assertTrue("Sign in to isolated companion and sync first", cookie.isNotBlank())
        val originalEndless = data.endlessQueue.first() == DataStoreManager.TRUE
        val bunny = track("aqz-KE-bpKQ", "Big Buck Bunny", 634)
        try {
            data.setEndlessQueue(false)
            start(handler, bunny)
            waitPlaying(handler)
            withContext(Dispatchers.Main) { handler.player.pause(); handler.player.seekTo(90_000) }
            delay(1_000)
            assertTrue(withContext(Dispatchers.Main) { kotlin.math.abs(handler.player.currentPosition - 90_000) < 1_500 })
            Log.i("WearDeviceCheck", "paused seek passed")
            completeTrack(handler, "signed-session")
            data.setCookie("", null)
            delay(2_000)
            start(handler, track("dQw4w9WgXcQ", "Never Gonna Give You Up", 213))
            completeTrack(handler, "guest")
            data.setCookie(cookie, pageId)
            verifyOfflineQueue(handler, bunny)
        } finally {
            data.setCookie(cookie, pageId)
            data.setEndlessQueue(originalEndless)
            withContext(Dispatchers.Main) { handler.player.pause() }
        }
    }

    @Test fun downloadedQueueSeekAndProgress(): Unit = runBlocking {
        permitted()
        val handler: MediaPlayerHandler = GlobalContext.get().get()
        try {
            verifyOfflineQueue(handler, track("aqz-KE-bpKQ", "Big Buck Bunny", 634))
        } finally {
            withContext(Dispatchers.Main) { handler.player.pause() }
        }
    }

    private suspend fun verifyOfflineQueue(handler: MediaPlayerHandler, bunny: Track) {
        val downloads: DownloadHandler = GlobalContext.get().get()
        val offline = OfflineQueue(context)
        offline.prepare(listOf(bunny), chargingOnly = false)
        withTimeout(300_000) {
            while (offline.status(downloads).ready != 1) delay(2_000)
        }
        offline.play(handler)
        waitPlaying(handler)
        assertEquals("wear_offline_queue", handler.queueData.value?.data?.playlistId)
        withContext(Dispatchers.Main) { handler.player.pause(); handler.player.seekTo(180_000); handler.player.play() }
        // Seeking can rebuffer. Observe actual player progress instead of assuming ten wall seconds of playback.
        var reached = 0L
        withTimeout(45_000) {
            while (reached < 185_000) {
                delay(1_000)
                reached = withContext(Dispatchers.Main) { handler.player.currentPosition }
                Log.i("WearDeviceCheck", "cached seek positionMs=$reached playing=${withContext(Dispatchers.Main) { handler.player.isPlaying }}")
            }
        }
        assertTrue("Cached seek landed at $reached", reached < 205_000)
        Log.i("WearDeviceCheck", "verified download queue and cached seek/play passed; network not disabled")
    }
}
