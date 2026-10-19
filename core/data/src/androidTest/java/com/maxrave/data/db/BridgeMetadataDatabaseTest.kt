package com.maxrave.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maxrave.domain.data.entities.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** Real SQLite/Room regression for metadata updates and podcast foreign-key preservation. */
@RunWith(AndroidJUnit4::class)
class BridgeMetadataDatabaseTest {
    private lateinit var database: MusicDatabase
    private lateinit var dao: DatabaseDao
    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            MusicDatabase::class.java).addTypeConverter(Converters()).build()
        dao = database.getDatabaseDao()
    }
    @After fun close() { if (::database.isInitialized) database.close() }

    @Test fun metadataDoesNotReplaceWatchDownloadsOrHistory() = runBlocking {
        val date = LocalDateTime.of(2026, 2, 1, 12, 0)
        val song = SongEntity(videoId = "song", duration = "3:00", durationSeconds = 180, isAvailable = true,
            isExplicit = false, likeStatus = "LIKE", title = "Before", videoType = "ATV", category = null,
            resultType = null, liked = true, totalPlayTime = 12_345, downloadState = DownloadState.STATE_DOWNLOADED,
            favoriteAt = date, downloadedAt = date, inLibrary = date)
        dao.insertSong(song)
        dao.updateSongMetadata(song.copy(title = "After", thumbnails = "new-art", totalPlayTime = 0,
            liked = false, downloadState = 0, inLibrary = LocalDateTime.now()).bridgeMetadata())
        val after = dao.getSong("song")!!
        assertEquals("After", after.title); assertEquals("new-art", after.thumbnails)
        assertEquals(song.downloadState, after.downloadState); assertEquals(song.totalPlayTime, after.totalPlayTime)
        assertTrue(after.liked); assertEquals(date, after.favoriteAt); assertEquals(date, after.downloadedAt)
        assertEquals(date, after.inLibrary)
    }

    @Test fun podcastRefreshAndFavoriteChangesPreserveEpisodes() = runBlocking {
        val podcast = PodcastsEntity("podcast", "Before", "author", "Artist", null, null, null, false, listEpisodes = listOf("episode"))
        dao.upsertPodcastMetadata(podcast)
        val episode = EpisodeEntity("episode", "podcast", "Cached episode", "Artist", "author", null, null, "3:00")
        dao.insertEpisodes(listOf(episode))
        dao.upsertPodcastMetadata(podcast.copy(title = "After"))
        assertEquals("After", dao.getPodcast("podcast")!!.title)
        assertEquals(episode, dao.getEpisode("episode"))
        val date = LocalDateTime.of(2026, 2, 1, 12, 0)
        assertEquals(1, dao.updatePodcastFavorite("podcast", true, date))
        assertEquals(0, dao.updatePodcastFavorite("podcast", true, LocalDateTime.now()))
        assertEquals(date, dao.getPodcast("podcast")!!.favoriteTime)
        assertEquals(episode, dao.getEpisode("episode"))
        assertEquals(1, dao.updatePodcastFavorite("podcast", false, null))
        assertNotNull(dao.getEpisode("episode"))
    }

    @Test fun playlistEntryIdentityIsScopedAndRemovalKeepsOtherPlaylists() = runBlocking {
        dao.insertSetVideoId(SetVideoIdEntity("song", "first-entry", "first"))
        dao.insertSetVideoId(SetVideoIdEntity("song", "second-entry", "VLsecond"))
        dao.insertSetVideoId(SetVideoIdEntity("song", "legacy-entry", ""))
        assertEquals("first-entry", dao.getSetVideoId("song", listOf("first", "VLfirst"))!!.setVideoId)
        assertEquals("second-entry", dao.getSetVideoId("song", listOf("second", "VLsecond"))!!.setVideoId)
        assertNull(dao.getSetVideoId("song", listOf("third")))
        dao.deleteSetVideoId("song", "first")
        assertNull(dao.getSetVideoId("song", listOf("first")))
        assertEquals("second-entry", dao.getSetVideoId("song", listOf("VLsecond"))!!.setVideoId)
        assertEquals("legacy-entry", dao.getSetVideoId("song", listOf(""))!!.setVideoId)
    }
}
