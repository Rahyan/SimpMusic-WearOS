package com.maxrave.common

import org.junit.Assert.*
import org.junit.Test

class OfflinePlaylistPlanTest {
    private fun track(id: String) = OfflineTrackEstimate(id, "Track $id", 60)
    private val size = 60L * 32_000

    @Test fun sharedTracksDownloadOnce() {
        val plan = planOfflinePlaylists(listOf(track("a"), track("b"), track("a")), emptyMap(), emptySet(),
            emptySet(), emptySet(), emptyMap(), size * 2, true)
        assertEquals(listOf("a", "b"), plan.downloadIds)
        assertEquals(setOf("a", "b"), plan.ownedEstimates.keys)
        assertFalse(plan.budgetBlocked)
    }

    @Test fun manualDownloadsAreNeverClaimedOrRemoved() {
        val plan = planOfflinePlaylists(listOf(track("manual")), mapOf("stale" to size), setOf("manual", "stale"),
            setOf("manual"), emptySet(), emptyMap(), size * 2, true)
        assertEquals(setOf("stale"), plan.removeIds)
        assertFalse("manual" in plan.ownedEstimates)
        assertTrue(plan.downloadIds.isEmpty())
    }

    @Test fun pinnedAndQueuedTracksSurviveCleanup() {
        val plan = planOfflinePlaylists(listOf(track("shared")), mapOf("shared" to size, "queue" to size, "old" to size),
            setOf("shared", "queue", "old"), setOf("shared"), setOf("queue"), emptyMap(), size * 3, true)
        assertEquals(setOf("old"), plan.removeIds)
        assertEquals(setOf("shared", "queue"), plan.ownedEstimates.keys)
    }

    @Test fun cleanupRequiresOptIn() {
        val plan = planOfflinePlaylists(emptyList(), mapOf("old" to size), setOf("old"), setOf("old"),
            emptySet(), emptyMap(), size * 2, false)
        assertTrue(plan.removeIds.isEmpty())
        assertEquals(setOf("old"), plan.ownedEstimates.keys)
    }

    @Test fun budgetStopsNewFilesWithoutEvictingReadyMusic() {
        val plan = planOfflinePlaylists(listOf(track("ready"), track("new")), mapOf("ready" to size), setOf("ready"),
            setOf("ready"), emptySet(), mapOf("ready" to size), size, true)
        assertTrue(plan.budgetBlocked)
        assertTrue(plan.downloadIds.isEmpty())
        assertTrue(plan.removeIds.isEmpty())
    }

    @Test fun largerActualFileAndPendingReservationsUseBudget() {
        val plan = planOfflinePlaylists(listOf(track("ready"), track("new")), mapOf("ready" to size), setOf("ready"),
            setOf("ready"), emptySet(), mapOf("ready" to size * 3), size * 2, true)
        assertTrue(plan.budgetBlocked)
        assertEquals(setOf("ready"), plan.ownedEstimates.keys)
    }

    @Test fun loweringBudgetAlsoStopsRetriesOfOwnedFiles() {
        val plan = planOfflinePlaylists(listOf(track("failed")), mapOf("failed" to size * 2), setOf("failed"),
            emptySet(), emptySet(), emptyMap(), size, true)
        assertTrue(plan.budgetBlocked)
        assertTrue(plan.downloadIds.isEmpty())
        assertTrue(plan.removeIds.isEmpty())
    }

    @Test fun retryingManualFileDoesNotTransferOwnership() {
        val plan = planOfflinePlaylists(listOf(track("manual")), emptyMap(), setOf("manual"), emptySet(),
            emptySet(), emptyMap(), size, true)
        assertEquals(listOf("manual"), plan.downloadIds)
        assertTrue(plan.ownedEstimates.isEmpty())
    }
}
