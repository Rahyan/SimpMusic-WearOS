package com.maxrave.data.repository

import com.maxrave.data.repository.PlaylistBrowseFixture.CAROUSEL_CURSOR
import com.maxrave.data.repository.PlaylistBrowseFixture.assertMembers
import com.maxrave.data.repository.PlaylistBrowseFixture.carousel
import com.maxrave.data.repository.PlaylistBrowseFixture.initial
import com.maxrave.data.repository.PlaylistBrowseFixture.load
import com.maxrave.data.repository.PlaylistBrowseFixture.members
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected behavior after a membership-aware pagination fix. This test is red on beta.2. */
class PlaylistCarouselRegressionTest {
    @Test
    fun completeMembersWithSecondaryCarouselAreEligible() {
        val result = load(initial("14 tracks", members(14), sectionCursor = CAROUSEL_CURSOR), carousel())
        assertTrue("An unrelated carousel must not veto 14 verified members", assertMembers(result, 14).isComplete)
    }
}
