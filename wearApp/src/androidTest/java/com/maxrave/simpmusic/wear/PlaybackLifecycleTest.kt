package com.maxrave.simpmusic.wear

import android.app.ActivityManager
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maxrave.media3.service.PlaybackServiceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No music, account or Activity. Requires a freshly cleared isolated package and lifecyclePreview=true. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackLifecycleTest {
    @Test(timeout = 90_000) fun coldSurfaceReadsStayLightAndServiceReconnectsAfterIdle() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.maxrave.simpmusic.test.dev")
        assumeTrue(InstrumentationRegistry.getArguments().getString("lifecyclePreview") == "true")
        val app = context.applicationContext as WearSimpMusicApplication
        assertNull("Fresh surface reads must not initialize playback", app.playbackHandler)
        repeat(10) { app.playbackSurfaces.snapshot() }
        assertNull(app.playbackHandler)
        app.preparePlaybackCaches()
        PlaybackServiceConnection.get(app).ensureConnected()
        assertNotNull(app.playbackHandler)
        fun serviceRunning(): Boolean {
            @Suppress("DEPRECATION")
            return context.getSystemService(ActivityManager::class.java).getRunningServices(100)
                .any { it.service.className == "com.maxrave.media3.service.SimpleMediaService" }
        }
        assertTrue(serviceRunning())
        withTimeout(40_000) { while (serviceRunning()) delay(250) }
        assertFalse("Idle controller must release its service binding", serviceRunning())
        PlaybackServiceConnection.get(app).ensureConnected()
        assertTrue(serviceRunning())
        withContext(Dispatchers.Main) {
            // The singleton remains usable after service destruction and session recreation.
            assertEquals(0, app.playbackHandler!!.player.mediaItemCount)
        }
    }
}
