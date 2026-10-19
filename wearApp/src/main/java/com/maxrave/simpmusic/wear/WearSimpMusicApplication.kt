package com.maxrave.simpmusic.wear

import android.app.Application
import com.maxrave.data.di.loader.loadAllModules
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.AccountRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.simpmusic.wear.auth.WearAccountManager
import com.maxrave.simpmusic.wear.net.ProcessNetworkBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import com.maxrave.logger.Logger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Wear entrypoint.
 *
 * We keep this separate from the phone app Application so we don’t inherit
 * phone-specific crash/restart wiring or UI assumptions.
 */
class WearSimpMusicApplication : Application() {
    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady
    private var networkBinder: ProcessNetworkBinder? = null
    private var lastPublishedPlayback: Triple<String, String, Boolean>? = null
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Logger.configure(java.io.File(filesDir, "diagnostics"), BuildConfig.DEBUG)

        val localMembership = com.maxrave.simpmusic.wear.wearable.WatchLocalMembership(this)
        com.maxrave.common.LocalLibraryEdits.listener = localMembership::record

        // Prefer validated Wi-Fi when available to avoid YouTube signed URL 403s caused by
        // network path/IP mismatch (Wi-Fi vs phone-proxied Bluetooth).
        networkBinder = ProcessNetworkBinder(this).also { it.start() }

        startKoin {
            androidContext(this@WearSimpMusicApplication)
            loadAllModules()
        }

        startupScope.launch(Dispatchers.Main.immediate) {
            val handler: com.maxrave.domain.mediaservice.handler.MediaPlayerHandler = GlobalContext.get().get()
            kotlinx.coroutines.flow.combine(handler.nowPlayingState, handler.controlState) { now, control ->
                Triple(now.mediaItem.mediaId, now.track?.title ?: now.songEntity?.title.orEmpty(), control.isPlaying)
            }.collect { state ->
                if (state != lastPublishedPlayback) {
                    lastPublishedPlayback = state
                    try {
                        androidx.wear.tiles.TileService.getUpdater(this@WearSimpMusicApplication)
                            .requestUpdate(com.maxrave.simpmusic.wear.tile.SimpMusicTileService::class.java)
                    } catch (_: SecurityException) { Logger.w("WearSurface", "System denied tile refresh") }
                    try {
                        androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester.create(
                        this@WearSimpMusicApplication, android.content.ComponentName(this@WearSimpMusicApplication,
                            com.maxrave.simpmusic.wear.complication.PlaybackStatusComplicationService::class.java)).requestUpdateAll()
                    } catch (_: SecurityException) { Logger.w("WearSurface", "System denied complication refresh") }
                }
            }
        }
        startupScope.launch {
            runCatching {
                val koin = GlobalContext.get()
                val dataStoreManager: DataStoreManager = koin.get()
                val accountRepository: AccountRepository = koin.get()
                val commonRepository: CommonRepository = koin.get()

                withTimeout(8_000) {
                    WearAccountManager(context = applicationContext, dataStoreManager = dataStoreManager,
                        accountRepository = accountRepository, commonRepository = commonRepository).bootstrapSessionAtStartup()
                }
            }.onFailure {
                Logger.w("WearStartup", "Account restore unavailable; opening guest library.")
            }
            _sessionReady.value = true
        }
    }

    override fun onTerminate() {
        networkBinder?.stop()
        networkBinder = null
        super.onTerminate()
    }
}
