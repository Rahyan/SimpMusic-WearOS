package com.maxrave.data.db

import com.maxrave.domain.data.entities.*

/** Partial Room updates cannot overwrite local downloads, play history, or membership timestamps. */
internal data class SongBridgeMetadata(
    val videoId: String,
    val albumId: String?,
    val albumName: String?,
    val artistId: List<String>?,
    val artistName: List<String>?,
    val duration: String,
    val durationSeconds: Int,
    val isAvailable: Boolean,
    val isExplicit: Boolean,
    val likeStatus: String,
    val thumbnails: String?,
    val title: String,
    val videoType: String,
    val category: String?,
    val resultType: String?,
)

internal fun SongEntity.bridgeMetadata() = SongBridgeMetadata(
    videoId = videoId,
    albumId = albumId,
    albumName = albumName,
    artistId = artistId,
    artistName = artistName,
    duration = duration,
    durationSeconds = durationSeconds,
    isAvailable = isAvailable,
    isExplicit = isExplicit,
    likeStatus = likeStatus,
    thumbnails = thumbnails,
    title = title,
    videoType = videoType,
    category = category,
    resultType = resultType,
)

internal data class AlbumBridgeMetadata(
    val browseId: String,
    val artistId: List<String?>?,
    val artistName: List<String>?,
    val audioPlaylistId: String,
    val description: String,
    val duration: String?,
    val durationSeconds: Int,
    val thumbnails: String?,
    val title: String,
    val trackCount: Int,
    val tracks: List<String>?,
    val type: String,
    val year: String?,
)

internal fun AlbumEntity.bridgeMetadata() = AlbumBridgeMetadata(
    browseId = browseId,
    artistId = artistId,
    artistName = artistName,
    audioPlaylistId = audioPlaylistId,
    description = description,
    duration = duration,
    durationSeconds = durationSeconds,
    thumbnails = thumbnails,
    title = title,
    trackCount = trackCount,
    tracks = tracks,
    type = type,
    year = year,
)

internal data class ArtistBridgeMetadata(
    val channelId: String,
    val name: String,
    val thumbnails: String?,
)

internal fun ArtistEntity.bridgeMetadata() = ArtistBridgeMetadata(
    channelId = channelId,
    name = name,
    thumbnails = thumbnails,
)

internal data class PodcastBridgeMetadata(
    val podcastId: String,
    val title: String,
    val authorId: String,
    val authorName: String,
    val authorThumbnail: String?,
    val description: String?,
    val thumbnail: String?,
    val listEpisodes: List<String>,
)

internal fun PodcastsEntity.bridgeMetadata() = PodcastBridgeMetadata(
    podcastId = podcastId,
    title = title,
    authorId = authorId,
    authorName = authorName,
    authorThumbnail = authorThumbnail,
    description = description,
    thumbnail = thumbnail,
    listEpisodes = listEpisodes,
)
