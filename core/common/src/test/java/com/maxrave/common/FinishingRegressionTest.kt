package com.maxrave.common

import com.maxrave.logger.LogSanitizer
import com.maxrave.logger.RollingLogFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import kotlin.random.Random

class FinishingRegressionTest {
    @Test fun appendsPreserveHistoryAndPlayNext() {
        val order = listOf(3, 1, 0, 4, 2)
        repeat(100) { seed ->
            val appended = insertShuffledTrack(order, 5, 0, setOf(4), Random(seed))
            assertEquals(order, appended.filter { it != 5 })
            assertTrue(appended.indexOf(5) > appended.indexOf(4))
            assertEquals((0..5).toList(), appended.sorted())
        }
    }
    @Test fun insertedPlayNextIsActuallyNextInShuffle() {
        assertEquals(listOf(4, 1, 2, 0, 5, 3), insertShuffledTrack(listOf(3, 1, 0, 4, 2), 2, 1, emptySet(), Random(0), playNext = true))
    }
    @Test fun movesPreserveOtherEntriesAndDuplicateIdentities() {
        val order = listOf(3, 1, 0, 4, 2)
        val result = moveShuffledTrack(order, 4, 1)
        assertEquals(listOf(4, 1, 2, 0, 3), result)
        assertEquals((0..4).toList(), result.sorted())
        assertEquals(order.filter { it != 4 }.map { movedPlaylistIndex(it, 4, 1) }, result.filter { it != 1 })
    }
    @Test fun localIntentWinsAndOtherPhoneSurvives() {
        assertTrue(effectiveImportedMembership(false, true, false))
        assertFalse(effectiveImportedMembership(true, false, true))
        assertTrue(effectiveImportedMembership(false, null, true))
        assertFalse(effectiveImportedMembership(false, null, false))
    }
    @Test fun importedChangesNeverRegisterAsLocalAcrossDispatchers() = runBlocking {
        val edits = mutableListOf<String>()
        LocalLibraryEdits.listener = { _, id, _ -> edits.add(id) }
        try {
            withContext(ImportedLibraryMutation) { withContext(Dispatchers.IO) { LocalLibraryEdits.changed("songs", "remote", true) } }
            LocalLibraryEdits.changed("songs", "local", true)
            assertEquals(listOf("local"), edits)
        } finally { LocalLibraryEdits.listener = null }
    }
    @Test fun secretsUrlsAndStructuredDumpsAreRemoved() {
        for (line in listOf("Cookie: secret", "visitorData secret", "poToken secret", "signatureCipher secret"))
            assertFalse(LogSanitizer.clean(line).contains("secret"))
        val url = LogSanitizer.clean("HTTP 403 https://rr.googlevideo.com/videoplayback?sig=PRIVATE&expire=1")
        assertTrue(url.contains("HTTP 403")); assertFalse(url.contains("PRIVATE")); assertFalse(url.contains("expire"))
    }
    @Test fun diagnosticFilesStayBounded() {
        val dir = Files.createTempDirectory("rolling-diagnostics").toFile()
        try {
            val log = RollingLogFile(dir, 100)
            repeat(100) { log.append("Safe log entry $it") }
            assertEquals(3, dir.listFiles()!!.size)
            assertTrue(dir.listFiles()!!.all { it.length() <= 100 })
            assertTrue(dir.resolve("app.log").readText().contains("99"))
        } finally { dir.deleteRecursively() }
    }
}
