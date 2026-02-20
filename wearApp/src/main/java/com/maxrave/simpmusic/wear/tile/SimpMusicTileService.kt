package com.maxrave.simpmusic.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.simpmusic.wear.WearMainActivity
import org.koin.core.context.GlobalContext

class SimpMusicTileService : TileService() {
    private val mediaPlayerHandler: MediaPlayerHandler by lazy { GlobalContext.get().get() }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val uiState = readTileUiState()
        val controlsRow =
            LayoutElementBuilders.Row.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    buildControlButton(
                        label = "◁",
                        clickable = buildLaunchClickable(id = "prev", action = ACTION_PREVIOUS),
                    ),
                ).addContent(
                    LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(6f)).build(),
                ).addContent(
                    buildControlButton(
                        label = if (uiState.isPlaying) "II" else "▷",
                        clickable = buildLaunchClickable(id = "play_pause", action = ACTION_PLAY_PAUSE),
                    ),
                ).addContent(
                    LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(6f)).build(),
                ).addContent(
                    buildControlButton(
                        label = "▷▷",
                        clickable = buildLaunchClickable(id = "next", action = ACTION_NEXT),
                    ),
                ).build()

        val content =
            LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(uiState.status)
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(13f))
                                .setColor(ColorBuilders.argb(if (uiState.isPlaying) 0xFF66D9FF.toInt() else 0xFFB7C7CF.toInt()))
                                .build(),
                        ).setMaxLines(1)
                        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                        .build(),
                ).addContent(
                    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build(),
                ).addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.wrap())
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setClickable(buildLaunchClickable(id = "open_app", action = ACTION_OPEN))
                                .setPadding(
                                    ModifiersBuilders.Padding.Builder()
                                        .setAll(DimensionBuilders.dp(2f))
                                        .build(),
                                ).build(),
                        ).addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(uiState.title)
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder()
                                        .setSize(DimensionBuilders.sp(16f))
                                        .setColor(ColorBuilders.argb(0xFFEAF6FB.toInt()))
                                        .build(),
                                ).setMaxLines(2)
                                .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                                .build(),
                        ).build(),
                ).addContent(
                    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build(),
                ).addContent(controlsRow)
                .addContent(
                    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build(),
                ).addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("Tap title to open app")
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(11f))
                                .setColor(ColorBuilders.argb(0xFFA2B6C0.toInt()))
                                .build(),
                        ).setMaxLines(1)
                        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                        .build(),
                ).build()

        val root =
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(ColorBuilders.argb(0xFF06202A.toInt()))
                                .build(),
                        ).build(),
                ).addContent(content)
                .build()

        val tile =
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(10_000L)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    Layout.Builder()
                                        .setRoot(root)
                                        .build(),
                                ).build(),
                        ).build(),
                ).build()
        return Futures.immediateFuture(tile)
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )

    private fun buildLaunchClickable(
        id: String,
        action: String,
    ): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearMainActivity::class.java.name)
                            .addKeyToExtraMapping(
                                EXTRA_TILE_ACTION,
                                ActionBuilders.AndroidStringExtra.Builder().setValue(action).build(),
                            ).build(),
                    ).build(),
            ).build()

    private fun buildControlButton(
        label: String,
        clickable: ModifiersBuilders.Clickable,
    ): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(44f))
            .setHeight(DimensionBuilders.dp(30f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0x1FFFFFFF))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(999f)).build())
                            .build(),
                    ).setClickable(clickable)
                    .build(),
            ).addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(12f))
                            .setColor(ColorBuilders.argb(0xFFEAF6FB.toInt()))
                            .build(),
                    ).build(),
            ).build()

    private data class TileUiState(
        val isPlaying: Boolean,
        val status: String,
        val title: String,
    )

    private fun readTileUiState(): TileUiState =
        runCatching {
            val nowPlaying = mediaPlayerHandler.nowPlayingState.value
            val trackTitle = nowPlaying.track?.title ?: nowPlaying.songEntity?.title.orEmpty()
            val isPlaying = mediaPlayerHandler.controlState.value.isPlaying
            TileUiState(
                isPlaying = isPlaying,
                status =
                    when {
                        trackTitle.isBlank() -> "SimpMusic"
                        isPlaying -> "Playing"
                        else -> "Paused"
                    },
                title = trackTitle.ifBlank { "Open on watch" },
            )
        }.getOrElse {
            TileUiState(
                isPlaying = false,
                status = "SimpMusic",
                title = "Open on watch",
            )
        }

    companion object {
        const val RESOURCES_VERSION = "1"
        const val EXTRA_TILE_ACTION = "tile_action"
        const val ACTION_OPEN = "open"
        const val ACTION_PLAY_PAUSE = "play_pause"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"
    }
}
