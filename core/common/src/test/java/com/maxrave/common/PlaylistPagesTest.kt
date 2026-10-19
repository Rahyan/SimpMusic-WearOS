package com.maxrave.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaylistPagesTest {
    @Test fun emptyResponseNeedsAnExplicitZeroCount() {
        assertFalse(matchesReportedPlaylistCount(null, 0))
        assertFalse(matchesReportedPlaylistCount("", 0))
        assertFalse(matchesReportedPlaylistCount("Unavailable", 0))
        assertTrue(matchesReportedPlaylistCount("0 songs", 0))
    }

    @Test fun reportedCountRejectsASilentlyTruncatedWalk() {
        assertFalse(matchesReportedPlaylistCount("100 songs", 60))
        assertTrue(matchesReportedPlaylistCount("100 songs", 100))
    }

    @Test fun unparseableAndNegativeCountsAreNotComplete() {
        assertFalse(matchesReportedPlaylistCount("1K songs", 1000))
        assertFalse(matchesReportedPlaylistCount("-1 songs", 0))
        assertFalse(matchesReportedPlaylistCount("0:00", 0))
    }

    @Test fun completeWalkPreservesOrderAndRepeatedTracks() = runBlocking {
        val pages = mapOf("one" to (listOf("b", "a") to "two"), "two" to (listOf("c") to null))
        assertEquals(listOf("a", "b", "a", "c"), collectPlaylistPages(listOf("a"), "one") { pages.getValue(it) })
    }

    @Test fun failedPageCannotReturnPartialPlaylist() {
        assertThrows(IOException::class.java) { runBlocking {
            collectPlaylistPages(listOf("first"), "one") { token ->
                if (token == "two") throw IOException("network")
                listOf("second") to "two"
            }
        } }
    }

    @Test fun repeatedContinuationFailsInsteadOfLooping() {
        var calls = 0
        assertThrows(IllegalStateException::class.java) { runBlocking {
            collectPlaylistPages(emptyList<String>(), "one") { calls++; listOf("a") to "one" }
        } }
        assertEquals(1, calls)
    }

    @Test fun unboundedPaginationFailsAtLimit() {
        var calls = 0
        assertThrows(IllegalStateException::class.java) { runBlocking {
            collectPlaylistPages(emptyList<String>(), "0", maxPages = 2) { token ->
                calls++; listOf(token) to (token.toInt() + 1).toString()
            }
        } }
        assertEquals(2, calls)
    }

    @Test fun cancellationPropagates() {
        assertThrows(CancellationException::class.java) { runBlocking {
            collectPlaylistPages(emptyList<String>(), "one") { throw CancellationException("cancel") }
        } }
    }
}
