package com.maxrave.simpmusic.wear

import androidx.test.platform.app.InstrumentationRegistry
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.SimpleMediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

/** A fresh handler must start actual jobs; an active placeholder Job is not an updater. */
class ProgressUpdaterTest {
    @Test fun startsAndRestartsOnFreshHandler(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("progressUpdater") == "true")
        check(isIsolatedTestPackage(InstrumentationRegistry.getInstrumentation().targetContext.packageName))
        val handler = withContext(Dispatchers.Main) { GlobalContext.get().get<MediaPlayerHandler>() }
        assumeTrue(withContext(Dispatchers.Main) { !handler.player.isPlaying })
        try {
            withContext(Dispatchers.Main) {
                handler.startBufferedUpdate()
                handler.startProgressUpdate()
                handler.startProgressUpdate()
            }
            withTimeout(4_000) { handler.simpleMediaState.first { it is SimpleMediaState.Progress } }
            withContext(Dispatchers.Main) {
                handler.stopProgressUpdate()
                handler.stopBufferedUpdate() // Publishes a different state before restart.
                handler.startProgressUpdate()
                handler.startProgressUpdate()
            }
            withTimeout(4_000) { handler.simpleMediaState.first { it is SimpleMediaState.Progress } }
        } finally {
            withContext(Dispatchers.Main) { handler.stopProgressUpdate(); handler.stopBufferedUpdate() }
        }
    }
}
