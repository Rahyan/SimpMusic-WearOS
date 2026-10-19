package com.maxrave.media3.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Owns remote playback independently of a short-lived Data Layer listener or Activity. */
@UnstableApi
class PlaybackServiceConnection private constructor(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connecting = Mutex()
    private var controller: MediaController? = null
    private var idleRelease: Job? = null

    suspend fun ensureConnected() = withContext(Dispatchers.Main.immediate) {
        connecting.withLock {
            if (controller?.isConnected == true) {
                scheduleIdleRelease()
                return@withLock
            }
            val future = MediaController.Builder(context, SessionToken(context,
                ComponentName(context, SimpleMediaService::class.java)))
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(disconnected: MediaController) {
                        if (controller === disconnected) {
                            idleRelease?.cancel()
                            controller = null
                        }
                    }
                }).buildAsync()
            val connected = try {
                withTimeout(8_000) { future.await() }.also {
                    check(it.isConnected) { "Could not connect to the watch player." }
                }
            } catch (failure: Throwable) {
                MediaController.releaseFuture(future)
                throw failure
            }
            controller = connected
            connected.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = scheduleIdleRelease()
            })
            scheduleIdleRelease()
        }
    }

    private fun scheduleIdleRelease() {
        idleRelease?.cancel()
        val current = controller ?: return
        val ongoing = current.playbackState == Player.STATE_BUFFERING ||
            (current.playbackState == Player.STATE_READY && current.playWhenReady)
        if (ongoing) return
        idleRelease = scope.launch {
            delay(30_000)
            if (controller === current) {
                controller = null
                current.release()
            }
        }
    }

    companion object {
        @Volatile private var instance: PlaybackServiceConnection? = null
        fun get(context: Context): PlaybackServiceConnection = instance ?: synchronized(this) {
            instance ?: PlaybackServiceConnection(context).also { instance = it }
        }
    }
}
