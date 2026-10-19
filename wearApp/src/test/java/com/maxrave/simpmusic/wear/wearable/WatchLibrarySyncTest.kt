package com.maxrave.simpmusic.wear.wearable

import com.maxrave.domain.data.entities.*
import com.maxrave.domain.repository.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class WatchLibrarySyncTest {
    // Models the actual insert-ignore contract for song/album and preserving replacement for playlist.
    private class Library(var song: SongEntity? = null, var album: AlbumEntity? = null, var playlist: PlaylistEntity? = null) {
        var membershipWrites = 0
        inline fun <reified T> repository(): T = Proxy.newProxyInstance(T::class.java.classLoader,
            arrayOf(T::class.java)) { _, method, args ->
            when (method.name) {
                "getSongById" -> flowOf(song)
                "getAlbum" -> flowOf(album)
                "getPlaylist" -> flowOf(playlist)
                "insertSong" -> { if (song == null) song = args[0] as SongEntity; flowOf(1L) }
                "insertAlbum" -> { if (album == null) album = args[0] as AlbumEntity; flowOf(1L) }
                "insertAndReplacePlaylist" -> {
                    val incoming = args[0] as PlaylistEntity
                    playlist = incoming.copy(downloadState = playlist?.downloadState ?: incoming.downloadState)
                    Unit
                }
                "updateSongMetadata" -> {
                    val remote = args[0] as SongEntity
                    song = song?.let { remote.copy(downloadState = it.downloadState, totalPlayTime = it.totalPlayTime, inLibrary = it.inLibrary, liked = it.liked, favoriteAt = it.favoriteAt) }; Unit
                }
                "updateAlbumMetadata" -> {
                    val remote = args[0] as AlbumEntity
                    album = album?.let { remote.copy(downloadState = it.downloadState, inLibrary = it.inLibrary, liked = it.liked, favoriteAt = it.favoriteAt) }; Unit
                }
                "updateLikeStatus" -> { membershipWrites++; song = song?.copy(liked = args[1] == 1); Unit }
                "updateAlbumLiked" -> { membershipWrites++; album = album?.copy(liked = args[1] == 1); Unit }
                "updatePlaylistLiked" -> { membershipWrites++; playlist = playlist?.copy(liked = args[1] == 1); Unit }
                else -> error("Unexpected library write: ${method.name}")
            }
        } as T
    }

    private val sync = WatchLibrarySync()
    private fun song(state: Int) = SongEntity(videoId = "track", title = "Song", duration = "3:00",
        durationSeconds = 180, isAvailable = true, isExplicit = false, likeStatus = "LIKE",
        videoType = "MUSIC_VIDEO_TYPE_ATV", category = null, resultType = null, liked = true, downloadState = state)
    private fun album(state: Int) = AlbumEntity(browseId = "album", audioPlaylistId = "list", description = "",
        duration = "3:00", durationSeconds = 180, thumbnails = null, title = "Album", trackCount = 1,
        type = "Album", year = null, liked = true, downloadState = state)
    private fun playlist(state: Int) = PlaylistEntity(id = "list", title = "Playlist", liked = true, downloadState = state)

    private suspend fun import(library: Library, remoteState: Int) {
        sync.song(library.repository<SongRepository>(), song(remoteState))
        sync.album(library.repository<AlbumRepository>(), album(remoteState))
        sync.playlist(library.repository<PlaylistRepository>(), playlist(remoteState))
    }

    @Test fun metadataRefreshPreservesWatchHistoryAndDownloads() = runBlocking {
        val original = song(DownloadState.STATE_DOWNLOADED).copy(totalPlayTime = 12345)
        val library = Library(original, album(DownloadState.STATE_DOWNLOADED).copy(title = "Old album"))
        sync.song(library.repository<SongRepository>(), song(0).copy(title = "Renamed", thumbnails = "new-art"))
        sync.album(library.repository<AlbumRepository>(), album(0).copy(title = "New album"))
        assertEquals(0, library.membershipWrites)
        assertEquals(original.favoriteAt, library.song!!.favoriteAt)
        assertEquals("Renamed", library.song!!.title); assertEquals("new-art", library.song!!.thumbnails)
        assertEquals(12345L, library.song!!.totalPlayTime); assertEquals(original.inLibrary, library.song!!.inLibrary)
        assertEquals(DownloadState.STATE_DOWNLOADED, library.song!!.downloadState)
        assertEquals("New album", library.album!!.title); assertEquals(DownloadState.STATE_DOWNLOADED, library.album!!.downloadState)
    }

    @Test fun phoneDownloadsNeverCreateFalseWatchOfflineEntries() = runBlocking {
        val library = Library()
        import(library, DownloadState.STATE_DOWNLOADED)
        assertEquals(0, library.song!!.downloadState)
        assertEquals(0, library.album!!.downloadState)
        assertEquals(0, library.playlist!!.downloadState)
        assertEquals(true, library.song!!.liked)
        assertEquals(true, library.album!!.liked)
        assertEquals(true, library.playlist!!.liked)
    }

    @Test fun phoneSyncCannotEraseActiveOrFinishedWatchDownloads() = runBlocking {
        for (state in listOf(DownloadState.STATE_PREPARING, DownloadState.STATE_DOWNLOADING, DownloadState.STATE_DOWNLOADED)) {
            val library = Library(song(state), album(state), playlist(state))
            import(library, DownloadState.STATE_NOT_DOWNLOADED)
            assertEquals(state, library.song!!.downloadState)
            assertEquals(state, library.album!!.downloadState)
            assertEquals(state, library.playlist!!.downloadState)
        }
    }
}
