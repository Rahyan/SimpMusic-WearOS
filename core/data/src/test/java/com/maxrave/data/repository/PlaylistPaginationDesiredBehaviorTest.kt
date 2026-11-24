package com.maxrave.data.repository

import com.maxrave.data.repository.PlaylistBrowseFixture.CAROUSEL_CURSOR
import com.maxrave.data.repository.PlaylistBrowseFixture.append
import com.maxrave.data.repository.PlaylistBrowseFixture.appendWithCarousel
import com.maxrave.data.repository.PlaylistBrowseFixture.assertMembers
import com.maxrave.data.repository.PlaylistBrowseFixture.carousel
import com.maxrave.data.repository.PlaylistBrowseFixture.carouselSection
import com.maxrave.data.repository.PlaylistBrowseFixture.initial
import com.maxrave.data.repository.PlaylistBrowseFixture.legacyShelf
import com.maxrave.data.repository.PlaylistBrowseFixture.load
import com.maxrave.data.repository.PlaylistBrowseFixture.member
import com.maxrave.data.repository.PlaylistBrowseFixture.memberSection
import com.maxrave.data.repository.PlaylistBrowseFixture.members
import com.maxrave.data.repository.PlaylistBrowseFixture.sectionContinuation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression cases that failed on the original beta.2 implementation. */
class PlaylistPaginationDesiredBehaviorTest {
    @Test
    fun legacyMemberSectionBeforeCarouselKeepsVerifiedMembers() {
        val result = load(
            initial("2 tracks", members(1), shelfCursor = "legacy"),
            sectionContinuation(
                "legacy",
                listOf(
                    memberSection(listOf(member(2, playlistSetVideoId = "entry-2"))),
                    carouselSection(includePlayableRow = true),
                ),
            ),
        )
        assertTrue(assertMembers(result, 2).isComplete)
    }

    @Test
    fun carouselBeforeLegacyMemberSectionKeepsVerifiedMembers() {
        val result = load(
            initial("2 tracks", members(1), shelfCursor = "legacy"),
            sectionContinuation(
                "legacy",
                listOf(
                    carouselSection(includePlayableRow = true),
                    memberSection(listOf(member(2, playlistSetVideoId = "entry-2"))),
                ),
            ),
        )
        assertTrue(assertMembers(result, 2).isComplete)
    }

    @Test
    fun terminalCarouselAfterValidMemberAppendKeepsPlaylistEligible() {
        val result = load(
            initial("2 tracks", members(1), memberCursor = "members"),
            appendWithCarousel("members", listOf(member(2)), nextSection = CAROUSEL_CURSOR),
            carousel(),
        )
        assertTrue("A separate terminal carousel must not veto verified members", assertMembers(result, 2).isComplete)
    }

    @Test
    fun legacyShelfRowsAreAppendedAcrossPages() {
        val result = load(
            initial("3 tracks", members(1), shelfCursor = "legacy-1"),
            legacyShelf("legacy-1", listOf(member(2)), next = "legacy-2"),
            legacyShelf("legacy-2", listOf(member(3))),
        )
        assertTrue(assertMembers(result, 3).isComplete)
    }

    @Test
    fun emptyMemberAppendRemainsIncompleteEvenWhenDecodedCountMatches() {
        val result = load(
            initial("1 track", members(1), memberCursor = "empty"),
            append("empty", emptyList()),
        )
        assertFalse("An empty append cannot resolve an outstanding member cursor", assertMembers(result, 1).isComplete)
    }
}
