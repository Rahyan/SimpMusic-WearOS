package com.maxrave.data.parser

import com.maxrave.common.collectPlaylistPages
import com.maxrave.data.mapping.toTrack
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.kotlinytmusicscraper.models.Continuation
import com.maxrave.kotlinytmusicscraper.models.MusicShelfRenderer
import com.maxrave.kotlinytmusicscraper.models.SectionListRenderer
import com.maxrave.kotlinytmusicscraper.models.response.BrowseResponse
import com.maxrave.kotlinytmusicscraper.pages.NextPage
import com.maxrave.kotlinytmusicscraper.parser.toSongItem

internal data class OfflinePlaylistPages(
    val initialRows: List<MusicShelfRenderer.Content>,
    val appendedTracks: List<Track>,
    val isComplete: Boolean,
)

internal suspend fun BrowseResponse.loadOfflinePlaylistPages(
    playlistId: String,
    fetch: suspend (String) -> BrowseResponse,
): OfflinePlaylistPages {
    val twoColumn = contents?.twoColumnBrowseResultsRenderer
    val sections = listOfNotNull(
        contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer,
        twoColumn?.secondaryContents?.sectionListRenderer,
        twoColumn?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer,
    )
    val shelves = sections.flatMap { section ->
        section.contents.orEmpty().mapNotNull { item ->
            item.musicPlaylistShelfRenderer?.let { section to it }
        }
    }
    val (section, shelf) = shelves.firstOrNull { (_, shelf) ->
        shelf.playlistId?.removePrefix("VL") == playlistId.removePrefix("VL")
    } ?: shelves.firstOrNull { (_, shelf) -> shelf.playlistId == null }
        ?: return OfflinePlaylistPages(emptyList(), emptyList(), false)

    val rows = shelf.contents.orEmpty()
    val initialRows = rows.memberRows()
    val memberTokens = rows.markerTokens() + shelf.continuations.tokens()
    val sectionTokens = section.continuations.tokens()
    val first = chooseCursor(memberTokens, sectionTokens)
    val hasMixedSectionCursor = section.contents.orEmpty().any { it.musicCarouselShelfRenderer != null } &&
        hasUnresolvedSectionCursor(memberTokens, sectionTokens)
    val seenEntries = mutableSetOf<String>()
    val initialEntriesAreUnique = initialRows.recordEntries(seenEntries)
    var complete = shelf.contents != null && shelves.size == 1 && rows.haveValidMemberLayout() &&
        first.resolved && !hasMixedSectionCursor && initialEntriesAreUnique && continuationContents == null &&
        onResponseReceivedActions.isNullOrEmpty() && contents?.sectionListRenderer == null &&
        sections.all { container ->
            (container === section || container.continuations.isNullOrEmpty()) &&
                container.contents.orEmpty().all { it.isKnownInitialSection() }
        }
    var cursor = first.cursor
    val tracks = collectPlaylistPages(emptyList(), cursor?.token) { token ->
        val page = fetch(token).offlinePage(checkNotNull(cursor))
        val uniqueEntries = page.rows.recordEntries(seenEntries)
        complete = complete && page.complete && uniqueEntries
        cursor = page.next
        page.tracks to cursor?.token
    }
    return OfflinePlaylistPages(initialRows, tracks, complete)
}

private enum class CursorOrigin {
    MEMBER,
    SECTION,
    // Follow a section fallback from a broken member page for display. The chain is already incomplete.
    UNRESOLVED_MEMBER,
}

private data class Cursor(val token: String, val origin: CursorOrigin, val appendTarget: String?)

private data class CursorChoice(val cursor: Cursor?, val resolved: Boolean)

private fun chooseCursor(
    memberTokens: List<String?>,
    sectionTokens: List<String?>,
    sectionOrigin: CursorOrigin = CursorOrigin.SECTION,
    appendTarget: String? = null,
): CursorChoice {
    // A loose section cursor yields to the shelf. A section known to paginate members
    // contributes its own member candidate, which must agree with any shelf token.
    val candidates = if (sectionOrigin == CursorOrigin.MEMBER) {
        (memberTokens + sectionTokens).distinct()
    } else {
        memberTokens.ifEmpty { sectionTokens }.distinct()
    }
    if (candidates.isEmpty()) return CursorChoice(null, true)
    val token = candidates.singleOrNull()?.takeIf { it.isNotBlank() }
        ?: return CursorChoice(null, false)
    val origin = if (memberTokens.isEmpty()) sectionOrigin else CursorOrigin.MEMBER
    return CursorChoice(Cursor(token, origin, appendTarget), true)
}

// A cursor on a section containing both a member shelf and a carousel can belong to either.
private fun hasUnresolvedSectionCursor(memberTokens: List<String?>, sectionTokens: List<String?>): Boolean =
    memberTokens.isNotEmpty() && sectionTokens.any { it !in memberTokens }

private data class OfflinePage(
    val rows: List<MusicShelfRenderer.Content>,
    val tracks: List<Track>,
    val next: Cursor?,
    val complete: Boolean,
)

private fun BrowseResponse.offlinePage(cursor: Cursor): OfflinePage {
    val continuation = continuationContents
    val section = continuation?.sectionListContinuation
    val items = section?.contents.orEmpty()
    val carouselCount = items.count { it.musicCarouselShelfRenderer != null && it.rendererCount() == 1 }
    val carouselOnly = items.isNotEmpty() && carouselCount == items.size
    val legacyShelf = continuation?.musicPlaylistShelfContinuation
    val hasMemberOrigin = cursor.origin != CursorOrigin.SECTION
    val genericShelf = items.singleOrNull { it.musicShelfRenderer != null && it.rendererCount() == 1 }
        ?.musicShelfRenderer
        ?.takeIf { cursor.origin == CursorOrigin.MEMBER }
    val actions = onResponseReceivedActions.orEmpty()
    val appends = actions.mapNotNull { it.appendContinuationItemsAction }
    val target = cursor.appendTarget ?: appends.firstOrNull()?.targetId?.takeIf { candidate ->
        appends.all { it.targetId == candidate }
    }
    val memberAppends = if (hasMemberOrigin && !target.isNullOrBlank()) {
        appends.filter { it.targetId == target }
    } else {
        emptyList()
    }
    val usesAppend = memberAppends.isNotEmpty()

    val usesGenericShelf = !usesAppend && legacyShelf == null && genericShelf != null
    val rows = when {
        usesAppend -> memberAppends.flatMap { it.continuationItems }.map {
            MusicShelfRenderer.Content(it.musicResponsiveListItemRenderer, null, it.continuationItemRenderer)
        }
        !hasMemberOrigin -> emptyList()
        legacyShelf != null -> legacyShelf.contents
        else -> genericShelf?.contents.orEmpty()
    }
    val memberRows = rows.memberRows()
    val tracks = memberRows.mapNotNull { row ->
        val renderer = row.musicResponsiveListItemRenderer ?: return@mapNotNull null
        val entry = renderer.playlistItemData
        if (usesGenericShelf && entry?.playlistSetVideoId.isNullOrBlank()) return@mapNotNull null
        val song = if (usesAppend) NextPage.fromMusicResponsiveListItemRenderer(renderer) else row.toSongItem()
        song?.takeIf { entry?.videoId == null || entry.videoId == it.id }?.toTrack()
    }
    val hasMemberPayload = hasMemberOrigin && (usesAppend || legacyShelf != null || usesGenericShelf)
    val responseIsUnambiguous = contents == null &&
        continuation?.gridContinuation == null && continuation?.musicShelfContinuation == null &&
        actions.size == memberAppends.size
    val sectionIsUnderstood = section == null || carouselOnly ||
        (usesGenericShelf && items.size == carouselCount + 1)
    val memberPageIsComplete = cursor.origin == CursorOrigin.MEMBER && hasMemberPayload &&
        (!usesAppend || legacyShelf == null) && sectionIsUnderstood &&
        memberRows.isNotEmpty() && tracks.size == memberRows.size && rows.haveValidMemberLayout()
    val carouselPageIsComplete = cursor.origin == CursorOrigin.SECTION && carouselOnly && legacyShelf == null

    val sectionOrigin = when {
        usesGenericShelf && items.size == 1 -> CursorOrigin.MEMBER
        hasMemberOrigin && !hasMemberPayload -> CursorOrigin.UNRESOLVED_MEMBER
        else -> CursorOrigin.SECTION
    }
    val shelfContinuations = when {
        usesGenericShelf -> genericShelf?.continuations
        !usesAppend && hasMemberOrigin -> legacyShelf?.continuations
        else -> null
    }
    val memberTokens = rows.markerTokens() + shelfContinuations.tokens()
    val sectionTokens = section?.continuations.tokens()
    val hasMixedSectionCursor = usesGenericShelf && carouselCount != 0 &&
        hasUnresolvedSectionCursor(memberTokens, sectionTokens)
    val next = chooseCursor(
        memberTokens = memberTokens,
        sectionTokens = sectionTokens,
        sectionOrigin = sectionOrigin,
        appendTarget = if (usesAppend) target else cursor.appendTarget,
    )
    return OfflinePage(
        memberRows,
        tracks,
        next.cursor,
        responseIsUnambiguous && (memberPageIsComplete || carouselPageIsComplete) &&
            next.resolved && !hasMixedSectionCursor,
    )
}

private fun List<Continuation>?.tokens(): List<String?> =
    orEmpty().map { if (it.reloadContinuationData == null) it.nextContinuationData?.continuation else null }

private fun List<MusicShelfRenderer.Content>.markerTokens(): List<String?> =
    mapNotNull { it.continuationItemRenderer }.map { it.continuationEndpoint?.continuationCommand?.token }

private fun List<MusicShelfRenderer.Content>.memberRows(): List<MusicShelfRenderer.Content> =
    filter { it.continuationItemRenderer == null }

private fun List<MusicShelfRenderer.Content>.haveValidMemberLayout(): Boolean {
    val firstMarker = indexOfFirst { it.continuationItemRenderer != null }
    val markersAreTrailing = firstMarker < 0 || subList(firstMarker, size).all { it.continuationItemRenderer != null }
    return markersAreTrailing && all { row ->
        listOfNotNull(row.musicResponsiveListItemRenderer, row.musicMultiRowListItemRenderer, row.continuationItemRenderer).size == 1
    }
}

private fun List<MusicShelfRenderer.Content>.recordEntries(seen: MutableSet<String>): Boolean {
    val ids = mapNotNull { it.musicResponsiveListItemRenderer?.playlistItemData?.playlistSetVideoId }
    return ids.filter { it.isNotBlank() }.all { seen.add(it) }
}

private fun SectionListRenderer.Content.isKnownInitialSection(): Boolean =
    rendererCount() == 1 && (
        musicPlaylistShelfRenderer != null || musicCarouselShelfRenderer != null ||
            musicDescriptionShelfRenderer != null || musicResponsiveHeaderRenderer != null ||
            musicEditablePlaylistDetailHeaderRenderer != null
        )

private fun SectionListRenderer.Content.rendererCount(): Int =
    listOfNotNull(
        musicCarouselShelfRenderer,
        musicShelfRenderer,
        musicCardShelfRenderer,
        musicPlaylistShelfRenderer,
        musicDescriptionShelfRenderer,
        musicResponsiveHeaderRenderer,
        musicEditablePlaylistDetailHeaderRenderer,
        gridRenderer,
    ).size
