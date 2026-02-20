package com.maxrave.simpmusic.wear

import android.os.Bundle
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.simpmusic.wear.tile.SimpMusicTileService
import com.maxrave.simpmusic.wear.ui.WearAppRoot
import com.maxrave.simpmusic.wear.ui.theme.WearTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class WearMainActivity : ComponentActivity() {
    private val mediaPlayerHandler: MediaPlayerHandler by inject()

    private val debugVideoId: String?
        get() = intent?.getStringExtra(EXTRA_DEBUG_VIDEO_ID)

    private var didDebugAutoplay = false
    private var pendingTileAction: String? = null

    private var shouldUnbind = false
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                mediaPlayerHandler.setActivitySession(this@WearMainActivity, WearMainActivity::class.java, service)
                Log.w("WearMainActivity", "onServiceConnected")
                maybeDebugAutoplay()
                dispatchPendingTileAction()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w("WearMainActivity", "onServiceDisconnected")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeTileActionFromIntent(intent)
        setContent {
            WearTheme {
                WearAppRoot(mediaPlayerHandler = mediaPlayerHandler)
            }
        }
    }

    private fun maybeDebugAutoplay() {
        if (!BuildConfig.DEBUG) return
        val videoId = debugVideoId?.trim().orEmpty()
        if (videoId.isBlank()) return
        if (didDebugAutoplay) return
        didDebugAutoplay = true

        // Useful for reproducing playback issues via ADB without UI interactions:
        // adb shell am start -n com.maxrave.simpmusic.dev/com.maxrave.simpmusic.wear.WearMainActivity --es debug_video_id <id>
        lifecycleScope.launch {
            // Give DI/service binding a moment; loadMediaItem is still safe if the service is not
            // fully connected yet (we mainly want logs and extraction behavior).
            kotlinx.coroutines.delay(1500)
            mediaPlayerHandler.loadMediaItem(
                anyTrack =
                    Track(
                        album = null,
                        artists = null,
                        duration = null,
                        durationSeconds = null,
                        isAvailable = true,
                        isExplicit = false,
                        likeStatus = null,
                        thumbnails = null,
                        title = intent?.getStringExtra(EXTRA_DEBUG_TITLE) ?: "Debug Track",
                        videoId = videoId,
                        videoType = null,
                        category = null,
                        feedbackTokens = null,
                        resultType = null,
                        year = null,
                    ),
                type = Config.SONG_CLICK,
                index = null,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        mediaPlayerHandler.startMediaService(this, serviceConnection)
        shouldUnbind = true
        maybeDebugAutoplay()
        dispatchPendingTileAction()
    }

    override fun onStop() {
        super.onStop()
        // WearOS can stop activities aggressively (screen off, ambient, etc).
        // If we unbind here and `startService` was blocked, we can inadvertently kill playback.
    }

    override fun onDestroy() {
        if (shouldUnbind) {
            runCatching { unbindService(serviceConnection) }
            shouldUnbind = false
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeTileActionFromIntent(intent)
        // Allow repeated ADB launches with different extras to trigger again.
        didDebugAutoplay = false
        maybeDebugAutoplay()
        dispatchPendingTileAction()
    }

    private fun consumeTileActionFromIntent(sourceIntent: Intent?) {
        val action = sourceIntent?.getStringExtra(SimpMusicTileService.EXTRA_TILE_ACTION).orEmpty()
        if (action.isBlank()) return
        pendingTileAction = action
        sourceIntent?.removeExtra(SimpMusicTileService.EXTRA_TILE_ACTION)
    }

    private fun dispatchPendingTileAction() {
        val tileAction = pendingTileAction ?: return
        pendingTileAction = null
        lifecycleScope.launch {
            when (tileAction) {
                SimpMusicTileService.ACTION_PLAY_PAUSE -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                SimpMusicTileService.ACTION_NEXT -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                SimpMusicTileService.ACTION_PREVIOUS -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
                else -> Unit
            }
        }
    }

    private companion object {
        private const val EXTRA_DEBUG_VIDEO_ID = "debug_video_id"
        private const val EXTRA_DEBUG_TITLE = "debug_title"
    }
}
