package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.models.response.BrowseResponse
import com.maxrave.kotlinytmusicscraper.parser.playlistContinuationContents
import com.maxrave.kotlinytmusicscraper.parser.getPlaylistContinuation
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlaylistContinuationTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun response(raw: String): BrowseResponse = json.decodeFromString(raw.replaceFirst("{", "{\"responseContext\":{},"))
    private val row = """{"musicResponsiveListItemRenderer":{"flexColumns":[],"playlistItemData":{"videoId":"song","playlistSetVideoId":"entry-in-this-playlist"}}}"""
    @Test fun modernActionContinuationKeepsPlaylistEntryIdentity() {
        val response = response("""{"onResponseReceivedActions":[{}, {"appendContinuationItemsAction":{"targetId":"playlist","continuationItems":[$row,{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-page"}}}}]}}]}""")
        val rows = response.playlistContinuationContents()!!
        assertEquals("entry-in-this-playlist", rows.first().musicResponsiveListItemRenderer!!.playlistItemData!!.playlistSetVideoId)
        assertEquals("next-page", response.getPlaylistContinuation())
    }
    @Test fun legacyShelfContinuationIsStillSupported() {
        val response = response("""{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[$row],"continuations":[{"nextContinuationData":{"continuation":"legacy-page"}}]}}}""")
        assertEquals(1, response.playlistContinuationContents()!!.size)
        assertEquals("legacy-page", response.getPlaylistContinuation())
    }
    @Test fun legacySectionContinuationIsStillSupported() {
        val response = response("""{"continuationContents":{"sectionListContinuation":{"contents":[{"musicShelfRenderer":{"contents":[$row]}}],"continuations":[{"nextContinuationData":{"continuation":"section-page"}}]}}}""")
        assertEquals(1, response.playlistContinuationContents()!!.size)
        assertEquals("section-page", response.getPlaylistContinuation())
    }
}
