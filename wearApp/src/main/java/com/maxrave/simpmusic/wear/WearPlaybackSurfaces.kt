package com.maxrave.simpmusic.wear

import android.content.ComponentName
import android.content.Context
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.wear.complication.PlaybackStatusComplicationService
import com.maxrave.simpmusic.wear.tile.SimpMusicTileService

/** Tile/watch-face reads must not construct ExoPlayer, its caches or extraction dependencies. */
class WearPlaybackSurfaces(private val context: Context) {
    data class Snapshot(val title: String = "", val isPlaying: Boolean = false)
    @Volatile private var current: Snapshot? = null
    private val preferences by lazy { context.getSharedPreferences("playback_surface", Context.MODE_PRIVATE) }

    fun snapshot(): Snapshot = current ?: Snapshot(preferences.getString("title", "").orEmpty())

    fun onMediaItemChanged(item: GenericMediaItem?) {
        val title = item?.metadata?.title?.toString().orEmpty()
        val next = snapshot().copy(title = title, isPlaying = item != null && snapshot().isPlaying)
        preferences.edit().putString("title", title).apply()
        publish(next)
    }

    fun onPlayingChanged(playing: Boolean) = publish(snapshot().copy(isPlaying = playing))

    private fun publish(next: Snapshot) {
        if (current == next) return
        current = next
        try {
            androidx.wear.tiles.TileService.getUpdater(context).requestUpdate(SimpMusicTileService::class.java)
            androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester.create(
                context, ComponentName(context, PlaybackStatusComplicationService::class.java)).requestUpdateAll()
        } catch (_: SecurityException) {
            Logger.w("WearSurface", "System denied playback surface refresh")
        }
    }
}
