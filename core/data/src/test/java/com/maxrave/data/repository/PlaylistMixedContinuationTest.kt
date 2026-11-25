package com.maxrave.data.repository

import com.maxrave.data.repository.PlaylistBrowseFixture.CAROUSEL_CURSOR
import com.maxrave.data.repository.PlaylistBrowseFixture.Page
import com.maxrave.data.repository.PlaylistBrowseFixture.append
import com.maxrave.data.repository.PlaylistBrowseFixture.appendWithCarousel
import com.maxrave.data.repository.PlaylistBrowseFixture.assertMembers
import com.maxrave.data.repository.PlaylistBrowseFixture.carouselSection
import com.maxrave.data.repository.PlaylistBrowseFixture.initial
import com.maxrave.data.repository.PlaylistBrowseFixture.load
import com.maxrave.data.repository.PlaylistBrowseFixture.marker
import com.maxrave.data.repository.PlaylistBrowseFixture.member
import com.maxrave.data.repository.PlaylistBrowseFixture.memberSection
import com.maxrave.data.repository.PlaylistBrowseFixture.members
import com.maxrave.data.repository.PlaylistBrowseFixture.sectionContinuation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistMixedContinuationTest {
    @Test
    fun orderedAppendActionsForTheSameTargetKeepAllMembers() {
        val result = load(
            initial("4 tracks", members(1), memberCursor = "members"),
            Page("members", """
                {"responseContext":{},"onResponseReceivedActions":[
                  {"appendContinuationItemsAction":{"targetId":"playlist-members","continuationItems":[${member(2)}]}},
                  {"appendContinuationItemsAction":{"targetId":"playlist-members","continuationItems":[${member(3)},${marker("last")}]}}
                ]}
            """.trimIndent()),
            append("last", listOf(member(4))),
        )
        assertTrue(assertMembers(result, 4).isComplete)
    }

    @Test
    fun memberMarkerBeforeLaterRowsLeavesPaginationOrderUnresolved() {
        val result = load(
            initial("4 tracks", members(1), memberCursor = "members"),
            append("members", listOf(member(2), marker("last"), member(3))),
            append("last", listOf(member(4))),
        )
        assertFalse(assertMembers(result, 4).isComplete)
    }

    @Test
    fun memberAppendAndCarouselInSameResponseKeepOnlyMembers() {
        val result = load(
            initial("3 tracks", members(1), memberCursor = "members"),
            appendWithCarousel("members", listOf(member(2), member(3))),
        )
        assertTrue(assertMembers(result, 3).isComplete)
    }

    @Test
    fun memberMarkerInMixedResponseTakesPrecedenceOverCarouselSectionCursor() {
        val result = load(
            initial("3 tracks", members(1), memberCursor = "members"),
            appendWithCarousel(
                "members",
                listOf(member(2)),
                nextMember = "remaining-members",
                nextSection = CAROUSEL_CURSOR,
            ),
            append("remaining-members", listOf(member(3))),
        )
        assertTrue(assertMembers(result, 3).isComplete)
    }

    @Test
    fun initialMixedSectionCursorStaysUnresolvedAlongsideMemberMarker() {
        val result = load(
            initial(
                "2 tracks",
                members(1),
                sectionCursor = "mixed-section",
                memberCursor = "members",
                siblingSections = listOf(carouselSection()),
            ),
            append("members", listOf(member(2))),
        )
        assertFalse(assertMembers(result, 2).isComplete)
    }

    @Test
    fun playableCarouselRowCannotFillMemberCountGap() {
        val result = load(
            initial("3 tracks", members(1), memberCursor = "members"),
            appendWithCarousel("members", listOf(member(2))),
        )
        assertFalse(assertMembers(result, 2).isComplete)
    }

    @Test
    fun malformedMemberRemainsIncompleteAlongsideValidCarouselCards() {
        val result = load(
            initial("2 tracks", members(1), memberCursor = "members"),
            appendWithCarousel("members", listOf(member(2), member(3, title = null))),
        )
        assertFalse(assertMembers(result, 2).isComplete)
    }

    @Test
    fun genericShelfFromSectionCursorCannotEstablishMembershipEvenWithMatchingCount() {
        val result = load(
            initial("1 track", members(1), sectionCursor = "unknown-section"),
            sectionContinuation("unknown-section", listOf(memberSection(listOf(member(2))))),
        )
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun sectionWithCarouselAndUnknownSiblingRemainsIncomplete() {
        val result = load(
            initial("1 track", members(1), sectionCursor = CAROUSEL_CURSOR),
            sectionContinuation(CAROUSEL_CURSOR, listOf(carouselSection(), "{}")),
        )
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun appendTargetChangeCannotSupplyPlaylistMembers() {
        val result = load(
            initial("2 tracks", members(1), memberCursor = "first"),
            append("first", listOf(member(2)), next = "second"),
            append("second", listOf(member(3)), target = "another-shelf"),
        )
        assertFalse(assertMembers(result, 2).isComplete)
    }

    @Test
    fun replayedPlaylistEntryRemainsIncompleteEvenWhenTrackCountMatches() {
        val result = load(
            initial("3 tracks", listOf(member(1, playlistSetVideoId = "entry-1")), memberCursor = "members"),
            append(
                "members",
                listOf(member(2, playlistSetVideoId = "entry-2"), member(3, playlistSetVideoId = "entry-1")),
            ),
        )
        assertFalse(assertMembers(result, 3).isComplete)
    }

    @Test
    fun distinctPlaylistEntriesMayReferenceTheSameVideo() {
        val result = load(
            initial("2 tracks", listOf(member(1, playlistSetVideoId = "entry-1")), memberCursor = "members"),
            append("members", listOf(member(1, playlistSetVideoId = "entry-2"))),
        )
        val playlist = checkNotNull(result.data)
        assertEquals(2, playlist.trackCount)
        assertEquals(listOf("member-1", "member-1"), playlist.tracks.map { it.videoId })
        assertTrue(playlist.isComplete)
    }
}
