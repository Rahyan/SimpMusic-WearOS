package com.maxrave.simpmusic.wear.wearable

import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import kotlinx.coroutines.flow.first

/** Phone metadata conveys library membership, never ownership of an on-watch audio file. */
internal class WatchLibrarySync {
    suspend fun song(repository: SongRepository, incoming: SongEntity) {
        val existing = repository.getSongById(incoming.videoId).first()
        repository.insertSong(incoming.copy(downloadState = DownloadState.STATE_NOT_DOWNLOADED)).first()
        repository.updateSongMetadata(incoming)
        if (existing?.liked != incoming.liked) repository.updateLikeStatus(incoming.videoId, if (incoming.liked) 1 else 0)
    }

    suspend fun album(repository: AlbumRepository, incoming: AlbumEntity) {
        val existing = repository.getAlbum(incoming.browseId).first()
        repository.insertAlbum(incoming.copy(downloadState = DownloadState.STATE_NOT_DOWNLOADED)).first()
        repository.updateAlbumMetadata(incoming)
        if (existing?.liked != incoming.liked) repository.updateAlbumLiked(incoming.browseId, if (incoming.liked) 1 else 0)
    }

    suspend fun playlist(repository: PlaylistRepository, incoming: PlaylistEntity) {
        val existing = repository.getPlaylist(incoming.id).first()
        repository.insertAndReplacePlaylist(incoming.copy(
            downloadState = DownloadState.STATE_NOT_DOWNLOADED,
            inLibrary = existing?.inLibrary ?: incoming.inLibrary,
            favoriteAt = if (existing != null) existing.favoriteAt else incoming.favoriteAt,
            downloadedAt = if (existing != null) existing.downloadedAt else incoming.downloadedAt,
        ))
        if (existing?.liked != incoming.liked) repository.updatePlaylistLiked(incoming.id, if (incoming.liked) 1 else 0)
    }
}
