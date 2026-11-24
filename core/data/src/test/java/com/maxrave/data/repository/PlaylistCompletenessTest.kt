package com.maxrave.data.repository

import com.maxrave.data.repository.PlaylistBrowseFixture.CAROUSEL_CURSOR
import com.maxrave.data.repository.PlaylistBrowseFixture.Page
import com.maxrave.data.repository.PlaylistBrowseFixture.append
import com.maxrave.data.repository.PlaylistBrowseFixture.assertLoadError
import com.maxrave.data.repository.PlaylistBrowseFixture.assertMembers
import com.maxrave.data.repository.PlaylistBrowseFixture.carousel
import com.maxrave.data.repository.PlaylistBrowseFixture.initial
import com.maxrave.data.repository.PlaylistBrowseFixture.load
import com.maxrave.data.repository.PlaylistBrowseFixture.member
import com.maxrave.data.repository.PlaylistBrowseFixture.members
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCompletenessTest {
    /** beta.2 marked a complete 14-member playlist incomplete after this carousel response. */
    @Test
    fun completeMembersStayCompleteAfterSecondaryCarousel() {
        val result = load(initial("14 tracks", members(14), sectionCursor = CAROUSEL_CURSOR), carousel())
        assertTrue(assertMembers(result, 14).isComplete)
    }

    @Test
    fun sameInitialMembersAreCompleteWithoutTheSecondaryCursor() {
        val result = load(initial("14 tracks", members(14)))
        assertTrue(assertMembers(result, 14).isComplete)
    }

    @Test
    fun carouselCannotFillMissingMember() {
        val result = load(initial("20 tracks", members(19), sectionCursor = CAROUSEL_CURSOR), carousel())
        assertFalse(assertMembers(result, 19).isComplete)
    }

    @Test
    fun validMemberContinuationCompletesPlaylist() {
        val result = load(
            initial("3 tracks", members(1), memberCursor = "members"),
            append("members", listOf(member(2), member(3))),
        )
        assertTrue(assertMembers(result, 3).isComplete)
    }

    @Test
    fun memberCursorTakesPrecedenceOverSecondarySectionCursor() {
        val result = load(
            initial("2 tracks", members(1), sectionCursor = CAROUSEL_CURSOR, memberCursor = "members"),
            append("members", listOf(member(2))),
        )
        assertTrue(assertMembers(result, 2).isComplete)
    }

    @Test
    fun rejectedInitialMemberRemainsIncompleteEvenWhenDecodedCountMatches() {
        val result = load(initial("1 track", listOf(member(1), member(2, id = ""))))
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun conflictingInitialMemberVideoIdRemainsIncompleteWhenDecodedCountMatches() {
        val row = member(1, playlistSetVideoId = "entry-1", entryVideoId = "other-video")
        val result = load(initial("1 track", listOf(row)))
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun ambiguousContinuationRemainsIncompleteEvenWhenDecodedCountMatches() {
        val result = load(
            initial("1 track", members(1), sectionCursor = "unknown"),
            Page("unknown", """{"responseContext":{},"continuationContents":{"sectionListContinuation":{"contents":[]}}}"""),
        )
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun rejectedContinuationRowRemainsIncompleteEvenWhenDecodedCountMatches() {
        val result = load(
            initial("2 tracks", members(1), memberCursor = "members"),
            append("members", listOf(member(2), member(3, includeArtist = false))),
        )
        assertFalse(assertMembers(result, 2).isComplete)
    }

    @Test
    fun blankVideoIdentifierIsRejectedEvenWhenRowCountsMatch() {
        val result = load(
            initial("2 tracks", members(1), memberCursor = "members"),
            append("members", listOf(member(2, id = " "))),
        )
        val playlist = checkNotNull(result.data) { result.message.orEmpty() }
        assertTrue(playlist.tracks.last().videoId.isBlank())
        assertFalse(playlist.isComplete)
    }

    @Test
    fun missingTitleRejectsContinuationRowEvenWhenRemainingCountMatches() {
        val result = load(
            initial("1 track", members(1), memberCursor = "members"),
            append("members", listOf(member(2, title = null))),
        )
        assertFalse(assertMembers(result, 1).isComplete)
    }

    @Test
    fun blankTitleIsRejectedEvenWhenRowCountsMatch() {
        val result = load(
            initial("2 tracks", members(1), memberCursor = "members"),
            append("members", listOf(member(2, title = " "))),
        )
        val playlist = assertMembers(result, 2)
        assertTrue(playlist.tracks.last().title.isBlank())
        assertFalse(playlist.isComplete)
    }

    @Test
    fun missingMalformedAndInconsistentCountsRemainIncomplete() {
        for (count in listOf(null, "unavailable", "2 tracks")) {
            val result = load(initial(count, members(1)))
            assertFalse("Reported count: $count", assertMembers(result, 1).isComplete)
        }
    }

    @Test
    fun failedContinuationReturnsErrorInsteadOfPartialPlaylist() {
        val result = load(
            initial("3 tracks", members(1), memberCursor = "first"),
            append("first", listOf(member(2)), next = "failed"),
            Page("failed", "{}", status = HttpStatusCode.ServiceUnavailable),
        )
        assertLoadError(result)
    }

    @Test
    fun cyclicTokensReturnErrorWithoutRefetchingAnyPage() {
        val result = load(
            initial("4 tracks", members(1), memberCursor = "first"),
            append("first", listOf(member(2)), next = "second"),
            append("second", listOf(member(3)), next = "first"),
        )
        assertLoadError(result)
    }

    @Test
    fun pageLimitReturnsErrorWithoutRequestingPage101() {
        val pages = (1..100).map { number ->
            append("page-$number", listOf(member(number + 1)), next = "page-${number + 1}")
        }
        assertLoadError(load(initial("102 tracks", members(1), memberCursor = "page-1"), *pages.toTypedArray()))
    }
}
