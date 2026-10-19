package com.maxrave.data.di

import com.maxrave.common.Config
import com.maxrave.data.mediaservice.MediaServiceHandlerImpl
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.data.player.GenericMediaItem
import android.content.Context
import com.maxrave.media3.exoplayer.ExoPlayerAdapter
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun mediaHandlerModule(
    onMediaItemChanged: (GenericMediaItem?) -> Unit = {},
    onPlayingChanged: (Context, Boolean) -> Unit = { _, _ -> },
    onCreated: (MediaPlayerHandler) -> Unit = {},
) =
    module {
        single<MediaPlayerHandler> {
            MediaServiceHandlerImpl(
                inputPlayer = get<ExoPlayerAdapter>(),
                context = androidContext(),
                dataStoreManager = get(),
                songRepository = get(),
                streamRepository = get(),
                localPlaylistRepository = get(),
                coroutineScope = get(named(Config.SERVICE_SCOPE)),
                updateWidget = onMediaItemChanged,
                updatePlayStatusForWidget = onPlayingChanged,
                setNotificationLayout = { _, _, _ -> },
                pushPlayerError = { _ -> },
            ).also(onCreated)
        }
    }
