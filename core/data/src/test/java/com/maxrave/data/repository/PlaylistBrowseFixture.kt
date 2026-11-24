package com.maxrave.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.maxrave.data.db.DatabaseDao
import com.maxrave.data.db.LocalDataSource
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.utils.Resource
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.Ytmusic
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

internal object PlaylistBrowseFixture {
    private const val PLAYLIST_ID = "PL-fixture"
    const val CAROUSEL_CURSOR = "secondary-carousel"

    data class Page(
        val cursor: String?,
        val body: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
    )

    fun initial(
        reportedCount: String?,
        members: List<String>,
        sectionCursor: String? = null,
        memberCursor: String? = null,
        shelfCursor: String? = null,
        siblingSections: List<String> = emptyList(),
    ): Page {
        val count = reportedCount?.let {
            ""","secondSubtitle":{"runs":[{"text":"Public"},{"text":" • "},{"text":${JsonPrimitive(it)}},{"text":" • "},{"text":"42 minutes"}]}"""
        }.orEmpty()
        val rows = members + listOfNotNull(memberCursor?.let(::marker))
        val memberShelf =
            """{"musicPlaylistShelfRenderer":{"playlistId":"$PLAYLIST_ID","collapsedItemCount":0,"contents":[${rows.joinToString()}]${legacyCursor(shelfCursor)}}}"""

        return Page(null, """
            {
              "responseContext": {},
              "contents": {"twoColumnBrowseResultsRenderer": {
                "tabs": [{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                  {"musicResponsiveHeaderRenderer":{"title":{"runs":[{"text":"Fixture playlist"}]}$count}}
                ]}}}}],
                "secondaryContents": {"sectionListRenderer": {
                  "contents":[${(listOf(memberShelf) + siblingSections).joinToString()}]
                  ${legacyCursor(sectionCursor)}
                }}
              }}
            }
        """.trimIndent())
    }

    fun carousel(cursor: String = CAROUSEL_CURSOR): Page =
        sectionContinuation(cursor, listOf(carouselSection()))

    fun carouselSection(includePlayableRow: Boolean = false): String {
        val cards = (1..10).map { number ->
            """
            {"musicTwoRowItemRenderer":{
              "title":{"runs":[{"text":"Carousel card $number"}]},
              "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                {"url":"https://fixture.invalid/cover-$number","width":120,"height":120}
              ]}}},
              "navigationEndpoint":{"browseEndpoint":{
                "browseId":"VL-related-$number",
                "browseEndpointContextSupportedConfigs":{
                  "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}
                }
              }}
            }}
            """.trimIndent()
        }
        // A responsive list renderer can also occur inside a carousel. Its parent is
        // the evidence that this otherwise parseable track is unrelated to the playlist.
        val contents = if (includePlayableRow) cards + member(99, id = "carousel-video") else cards
        return """
            {"musicCarouselShelfRenderer":{
              "itemSize":"MUSIC_CAROUSEL_SHELF_ITEM_SIZE_SMALL",
              "contents":[${contents.joinToString()}]
            }}
        """.trimIndent()
    }

    fun append(
        cursor: String,
        members: List<String>,
        next: String? = null,
        target: String = "playlist-members",
    ): Page = Page(cursor, """{"responseContext":{},${appendActions(members, next, target)}}""")

    fun appendWithCarousel(
        cursor: String,
        members: List<String>,
        nextMember: String? = null,
        nextSection: String? = null,
    ): Page =
        Page(cursor, """
            {"responseContext":{},
             ${appendActions(members, nextMember)},
             ${sectionContents(listOf(carouselSection(includePlayableRow = true)), nextSection)}}
        """.trimIndent())

    fun sectionContinuation(cursor: String, sections: List<String>, next: String? = null): Page =
        Page(cursor, """{"responseContext":{},${sectionContents(sections, next)}}""")

    fun memberSection(members: List<String>, next: String? = null): String =
        """{"musicShelfRenderer":{"contents":[${members.joinToString()}]${legacyCursor(next)}}}"""

    fun legacyShelf(cursor: String, members: List<String>, next: String? = null): Page =
        Page(cursor, """
            {"responseContext":{},"continuationContents":{"musicPlaylistShelfContinuation":{
              "contents":[${members.joinToString()}]${legacyCursor(next)}
            }}}
        """.trimIndent())

    private fun appendActions(members: List<String>, next: String?, target: String = "playlist-members"): String {
        val rows = members + listOfNotNull(next?.let(::marker))
        return """
            "onResponseReceivedActions":[{"appendContinuationItemsAction":{
              "targetId":${JsonPrimitive(target)},
              "continuationItems":[${rows.joinToString()}]
            }}]
        """.trimIndent()
    }

    private fun sectionContents(sections: List<String>, next: String?): String = """
        "continuationContents":{"sectionListContinuation":{
          "contents":[${sections.joinToString()}]${legacyCursor(next)}
        }}
    """.trimIndent()

    private fun legacyCursor(cursor: String?): String = cursor?.let {
        ""","continuations":[{"nextContinuationData":{"continuation":${JsonPrimitive(it)}}}]"""
    }.orEmpty()

    fun members(count: Int): List<String> = (1..count).map { member(it) }

    fun member(
        number: Int,
        id: String = "member-$number",
        title: String? = "Track $number",
        includeArtist: Boolean = true,
        playlistSetVideoId: String? = null,
        entryVideoId: String = id,
    ): String {
        val titleText = title?.let {
            """"text":{"runs":[{"text":${JsonPrimitive(it)},"navigationEndpoint":{"watchEndpoint":{"videoId":${JsonPrimitive(id)}}}}]}"""
        }.orEmpty()
        val artistColumn = if (includeArtist) {
            """,{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Fixture artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UC-fixture-artist"}}}]}}}"""
        } else {
            ""
        }
        val entryId = playlistSetVideoId?.let { ""","playlistSetVideoId":${JsonPrimitive(it)}""" }.orEmpty()
        return """
            {"musicResponsiveListItemRenderer":{
              "playlistItemData":{"videoId":${JsonPrimitive(entryVideoId)}$entryId},
              "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{$titleText}}$artistColumn]
            }}
        """.trimIndent()
    }

    fun marker(cursor: String): String =
        """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":${JsonPrimitive(cursor)}}}}}"""

    fun load(vararg pages: Page): Resource<PlaylistBrowse> = runBlocking {
        val context = mock(Context::class.java)
        `when`(context.packageManager).thenReturn(mock(PackageManager::class.java))
        val youTube = YouTube(context)
        val repository = PlaylistRepositoryImpl(context, LocalDataSource(mock(DatabaseDao::class.java)), youTube)

        // The release has no constructor seam for its HTTP engine. Replace the client on this
        // instance before the first request, retaining the production request and JSON decoding.
        val ytmusic = YouTube::class.java.getDeclaredField("ytMusic").apply {
            isAccessible = true
        }.get(youTube) as Ytmusic
        val requests = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { request ->
            val requestBody = request.body as OutgoingContent.ByteArrayContent
            val body = Json.parseToJsonElement(requestBody.bytes().decodeToString()).jsonObject
            val cursor = body["continuation"]?.jsonPrimitive?.contentOrNull
            val browseId = body["browseId"]?.jsonPrimitive?.contentOrNull
            val index = requests.size
            requests.add(request.url.encodedPath to cursor)
            if (request.method != HttpMethod.Post || browseId != (if (cursor == null) "VL$PLAYLIST_ID" else null)) {
                requests.add("unexpected browse request" to cursor)
            }
            val page = checkNotNull(pages.getOrNull(index)) { "Unexpected browse request at index $index" }
            check(cursor == page.cursor) { "Unexpected cursor at index $index" }
            respond(page.body, page.status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            defaultRequest { url("https://fixture.invalid/youtubei/v1/") }
            install(ContentNegotiation) { json(ytmusic.normalJson) }
        }
        try {
            Ytmusic::class.java.getDeclaredField("activeClient").apply { isAccessible = true }.set(ytmusic, client)
            val result = withTimeout(10_000) { repository.getFullPlaylistData(PLAYLIST_ID).single() }
            assertEquals(pages.map { "/youtubei/v1/browse" to it.cursor }, requests)
            result
        } finally {
            client.close()
            engine.close()
        }
    }

    fun assertMembers(result: Resource<PlaylistBrowse>, expectedCount: Int): PlaylistBrowse {
        assertTrue("Expected decoded playlist, got error: ${result.message}", result is Resource.Success)
        val playlist = checkNotNull(result.data)
        assertEquals(expectedCount, playlist.trackCount)
        assertEquals((1..expectedCount).map { "member-$it" }, playlist.tracks.map { it.videoId })
        return playlist
    }

    fun assertLoadError(result: Resource<PlaylistBrowse>) {
        assertTrue("Expected load failure, got ${result.data}", result is Resource.Error)
        assertNull(result.data)
    }
}
