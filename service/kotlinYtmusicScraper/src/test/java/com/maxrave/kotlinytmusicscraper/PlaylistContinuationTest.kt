package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.models.response.BrowseResponse
import com.maxrave.kotlinytmusicscraper.parser.getPlaylistContinuation
import com.maxrave.kotlinytmusicscraper.parser.playlistContinuationContents
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

    @Test
    fun legacySingleColumnSectionCursorIsDiscovered() {
        val result = response(singleColumn(sectionCursor = "legacy-section"))
        assertEquals("legacy-section", result.getPlaylistContinuation())
    }

    @Test
    fun legacySingleColumnPlaylistShelfCursorIsDiscovered() {
        val result = response(singleColumn(shelfCursor = "legacy-members"))
        assertEquals("legacy-members", result.getPlaylistContinuation())
    }

    @Test
    fun existingCursorHelperPrefersLegacySectionCursorOverExplicitPlaylistShelfCursor() {
        val result = response(singleColumn(sectionCursor = "section", shelfCursor = "legacy-members"))
        // These candidates come from different containers. The existing helper returns
        // only the first one, with no evidence that it paginates playlist members.
        assertEquals("section", result.getPlaylistContinuation())
    }

    private fun singleColumn(sectionCursor: String? = null, shelfCursor: String? = null): String = """
        {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
          "sectionListRenderer":{"contents":[
            {"musicPlaylistShelfRenderer":{"playlistId":"PL-fixture","collapsedItemCount":0,"contents":[$row]${legacyCursor(shelfCursor)}}}
          ]${legacyCursor(sectionCursor)}}
        }}}]}}}
    """.trimIndent()

    private fun legacyCursor(cursor: String?): String = cursor?.let {
        ""","continuations":[{"nextContinuationData":{"continuation":"$it"}}]"""
    }.orEmpty()
}
