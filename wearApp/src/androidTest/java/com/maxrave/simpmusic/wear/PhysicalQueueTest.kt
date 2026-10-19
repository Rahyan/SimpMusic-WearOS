package com.maxrave.simpmusic.wear

import androidx.test.platform.app.InstrumentationRegistry
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.extension.toGenericMediaItem
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

/** Exercises the actual handler and ExoPlayer mirror without resolving synthetic tracks. */
class PhysicalQueueTest {
    private fun track(id: String) = Track(null, listOf(Artist(null, "Fixture")), null, 180,
        true, false, null, null, id, id, null, null, null, null, null)

    @Test fun playNextRadioPolicyAndBatchTrimPreserveMirror(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("physicalQueue") == "true")
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName == "com.maxrave.simpmusic.test.dev")
        val handler: MediaPlayerHandler = GlobalContext.get().get()
        val data: DataStoreManager = GlobalContext.get().get()
        val endless = data.endlessQueue.first() == DataStoreManager.TRUE
        val saveRecent = data.saveRecentSongAndQueue.first() == DataStoreManager.TRUE
        // The test never prepares its synthetic media; preserve the existing real queue.
        data.setEndlessQueue(false)
        data.setSaveRecentSongAndQueue(false)
        try {
        delay(200)
        withContext(Dispatchers.Main) {
            val oldQueue = handler.queueData.value!!.data
            val player = handler.player
            val oldItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it)!! }
            val oldIndex = player.currentMediaItemIndex
            val oldPosition = player.currentPosition
            val oldPlaying = player.isPlaying
            val oldShuffle = player.shuffleModeEnabled
            try {
                player.pause()
                player.shuffleModeEnabled = false
                suspend fun seed(tracks: List<Track>, radio: Boolean = false) {
                    handler.resetSongAndQueue()
                    handler.setQueueData(QueueData.Data(tracks, tracks.first(), if (radio) "RDAMVMfixture" else "wear_offline_queue",
                        "Fixture", if (radio) PlaylistType.RADIO else PlaylistType.PLAYLIST, null))
                    tracks.forEach { player.addMediaItem(it.toGenericMediaItem()) }
                }
                fun ids() = handler.queueData.value!!.data.listTracks.map { it.videoId }
                fun playerIds() = (0 until player.mediaItemCount).map { player.getMediaItemAt(it)!!.mediaId }
                seed(listOf(track("a"), track("b"), track("c")))
                handler.playNext(track("next"))
                assertEquals(listOf("a", "next", "b", "c"), ids())
                assertEquals(ids(), playerIds())
                val radio = (0 until 130).map { track("radio-$it") }
                seed(radio, radio = true)
                assertFalse(handler.loadMoreCatalog(arrayListOf(track("blocked")), isAddToQueue = true))
                assertEquals(130, player.mediaItemCount)
                player.seekTo(125, 0)
                assertTrue(handler.loadMoreCatalog(arrayListOf(track("extra"))))
                assertEquals(106, player.mediaItemCount)
                assertEquals(100, player.currentMediaItemIndex)
                assertEquals("radio-125", player.currentMediaItem!!.mediaId)
                assertEquals("radio-25", ids().first())
                assertEquals(ids(), playerIds())
            } finally {
                handler.resetSongAndQueue()
                handler.setQueueData(oldQueue)
                oldItems.forEach(player::addMediaItem)
                if (oldItems.isNotEmpty()) {
                    player.seekTo(oldIndex.coerceIn(oldItems.indices), oldPosition)
                    player.prepare()
                    player.playWhenReady = oldPlaying
                }
                player.shuffleModeEnabled = oldShuffle
            }
        }
        } finally {
            data.setEndlessQueue(endless)
            data.setSaveRecentSongAndQueue(saveRecent)
        }
    }
}
