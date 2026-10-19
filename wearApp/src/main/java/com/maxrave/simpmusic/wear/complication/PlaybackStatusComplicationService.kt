package com.maxrave.simpmusic.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.maxrave.simpmusic.wear.WearMainActivity
import com.maxrave.simpmusic.wear.tile.SimpMusicTileService

class PlaybackStatusComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val snapshot = (application as com.maxrave.simpmusic.wear.WearSimpMusicApplication).playbackSurfaces.snapshot()
        val title = snapshot.title
        val isPlaying = snapshot.isPlaying
        return createData(
            text = if (title.isBlank()) "App" else if (isPlaying) "II" else "▷",
            title = title.ifBlank { "SimpMusic" },
            action = if (title.isBlank()) SimpMusicTileService.ACTION_OPEN else SimpMusicTileService.ACTION_PLAY_PAUSE,
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return createData(
            text = "▷",
            title = "SimpMusic",
            action = SimpMusicTileService.ACTION_OPEN,
        )
    }

    private fun createData(
        text: String,
        title: String,
        action: String,
    ): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("SimpMusic playback control").build(),
        ).setTitle(
            PlainComplicationText.Builder(title.take(24)).build(),
        ).setTapAction(buildTapIntent(action))
            .build()

    private fun buildTapIntent(action: String): PendingIntent =
        PendingIntent.getActivity(
            this,
            action.hashCode(),
            Intent(this, WearMainActivity::class.java)
                .putExtra(SimpMusicTileService.EXTRA_TILE_ACTION, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
