package com.maxrave.data.di.loader

import com.maxrave.data.di.databaseModule
import com.maxrave.data.di.mediaHandlerModule
import com.maxrave.data.di.repositoryModule
import com.maxrave.media3.di.loadMediaService
import org.koin.core.context.loadKoinModules

fun loadAllModules(
    onMediaItemChanged: (com.maxrave.domain.data.player.GenericMediaItem?) -> Unit = {},
    onPlayingChanged: (android.content.Context, Boolean) -> Unit = { _, _ -> },
    onPlaybackCreated: (com.maxrave.domain.mediaservice.handler.MediaPlayerHandler) -> Unit = {},
) {
    loadKoinModules(
        listOf(
            databaseModule,
            repositoryModule,
        ),
    )
    loadMediaService()
    loadKoinModules(mediaHandlerModule(onMediaItemChanged, onPlayingChanged, onPlaybackCreated))
}
