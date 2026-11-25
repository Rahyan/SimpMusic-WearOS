package com.maxrave.data.repository

import android.content.Context
import com.maxrave.data.repository.PlaylistBrowseFixture.CAROUSEL_CURSOR
import com.maxrave.data.repository.PlaylistBrowseFixture.append
import com.maxrave.data.repository.PlaylistBrowseFixture.assertLoadError
import com.maxrave.data.repository.PlaylistBrowseFixture.assertMembers
import com.maxrave.data.repository.PlaylistBrowseFixture.carouselSection
import com.maxrave.data.repository.PlaylistBrowseFixture.initial
import com.maxrave.data.repository.PlaylistBrowseFixture.legacyShelf
import com.maxrave.data.repository.PlaylistBrowseFixture.load
import com.maxrave.data.repository.PlaylistBrowseFixture.member
import com.maxrave.data.repository.PlaylistBrowseFixture.memberSection
import com.maxrave.data.repository.PlaylistBrowseFixture.members
import com.maxrave.data.repository.PlaylistBrowseFixture.sectionContinuation
import com.maxrave.kotlinytmusicscraper.Ytmusic
import com.maxrave.kotlinytmusicscraper.models.response.BrowseResponse
import com.maxrave.kotlinytmusicscraper.parser.fromPlaylistContinuationToTrackWithSetVideoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PlaylistLegacyPaginationTest {
    @Test
    fun scraperDecodesTrackAndEntryIdsFromBothLegacyMemberPayloads() {
        val json = Ytmusic(mock(Context::class.java)).normalJson
        val rows = listOf(member(2, playlistSetVideoId = "entry-2"))
        val pages = listOf(
            legacyShelf("legacy", rows),
            sectionContinuation("legacy", listOf(memberSection(rows))),
        )
        for (page in pages) {
            val response = json.decodeFromString<BrowseResponse>(page.body)
            val (track, entryId) = response.fromPlaylistContinuationToTrackWithSetVideoId().single()
            assertEquals("member-2", track.id)
            assertEquals("Track 2", track.title)
            assertEquals("entry-2", entryId)
        }
    }

    @Test
    fun legacyShelfCursorCanLeadToValidMemberAppend() {
        val result = load(
            initial("2 tracks", members(1), shelfCursor = "legacy"),
            append("legacy", listOf(member(2))),
        )
        assertTrue(assertMembers(result, 2).isComplete)
    }

    @Test
    fun explicitLegacyShelfCursorTakesPrecedenceOverEnclosingSectionCursor() {
        val result = load(
            initial("2 tracks", members(1), sectionCursor = CAROUSEL_CURSOR, shelfCursor = "legacy"),
            legacyShelf("legacy", listOf(member(2))),
        )
        assertTrue(assertMembers(result, 2).isComplete)
    }

    @Test
    fun conflictingMemberCursorsKeepMatchingCountIncomplete() {
        val result = load(
            initial("1 track", members(1), memberCursor = "append", shelfCursor = "legacy"),
        )
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun malformedLegacyMemberKeepsMixedSectionIncomplete() {
        val rows = listOf(
            member(2, playlistSetVideoId = "entry-2"),
            member(3, title = null, playlistSetVideoId = "entry-3"),
        )
        val result = load(
            initial("2 tracks", members(1), shelfCursor = "legacy"),
            sectionContinuation("legacy", listOf(carouselSection(), memberSection(rows))),
        )
        assertFalse(assertMembers(result, 2).isComplete)
    }

    @Test
    fun legacySectionCursorFollowsSingleVerifiedMemberShelf() {
        val result = load(
            initial("3 tracks", members(1), shelfCursor = "legacy"),
            sectionContinuation(
                "legacy",
                listOf(memberSection(listOf(member(2, playlistSetVideoId = "entry-2")))),
                next = "remaining-members",
            ),
            legacyShelf("remaining-members", listOf(member(3))),
        )
        assertTrue(assertMembers(result, 3).isComplete)
    }

    @Test
    fun conflictingLegacyCursorsInSameMemberScopeKeepPlaylistIncomplete() {
        val result = load(
            initial("2 tracks", members(1), shelfCursor = "legacy"),
            sectionContinuation(
                "legacy",
                listOf(memberSection(listOf(member(2, playlistSetVideoId = "entry-2")), next = "via-shelf")),
                next = "via-section",
            ),
        )
        assertFalse(assertMembers(result, 2).isComplete)
    }

    @Test
    fun carouselSiblingDoesNotResolveConflictingLegacySectionCursor() {
        val result = load(
            initial("3 tracks", members(1), shelfCursor = "legacy"),
            sectionContinuation(
                "legacy",
                listOf(
                    memberSection(listOf(member(2, playlistSetVideoId = "entry-2")), next = "via-shelf"),
                    carouselSection(),
                ),
                next = "via-section",
            ),
            legacyShelf("via-shelf", listOf(member(3))),
        )
        assertFalse(assertMembers(result, 3).isComplete)
    }

    @Test
    fun genericShelfWithoutEntryIdsCannotResolveExplicitMemberCursor() {
        val result = load(
            initial("1 track", members(1), shelfCursor = "legacy"),
            sectionContinuation("legacy", listOf(memberSection(listOf(member(2))))),
        )
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun memberShelfCursorWithoutMemberDataStaysIncompleteAfterLaterValidAppend() {
        val result = load(
            initial("2 tracks", members(1), shelfCursor = "legacy"),
            sectionContinuation("legacy", listOf(carouselSection()), next = "unresolved"),
            append("unresolved", listOf(member(2))),
        )
        assertFalse(assertMembers(result, 2).isComplete)
    }

    @Test
    fun emptyLegacyShelfRemainsIncompleteEvenWhenDecodedCountMatches() {
        val result = load(
            initial("1 track", members(1), shelfCursor = "empty"),
            legacyShelf("empty", emptyList()),
        )
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun repeatedLegacyShelfTokenReturnsErrorWithoutRefetching() {
        val result = load(
            initial("3 tracks", members(1), shelfCursor = "repeat"),
            legacyShelf("repeat", listOf(member(2)), next = "repeat"),
        )
        assertLoadError(result)
    }
}
