package com.maxrave.common

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfflineQueueTest {
    @Test fun rejectsOversizedIncompleteAndExcessiveQueues() {
        assertThrows(IllegalArgumentException::class.java) { validateOfflineQueue(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { validateOfflineQueue(listOf(OfflineTrackEstimate("", "Song", 60))) }
        assertThrows(IllegalArgumentException::class.java) { validateOfflineQueue(listOf(OfflineTrackEstimate("long", "Long recording", Int.MAX_VALUE))) }
        assertThrows(IllegalArgumentException::class.java) { validateOfflineQueue(List(101) { OfflineTrackEstimate("$it", "Song", 1) }) }
    }
    @Test fun duplicateQueueEntriesDownloadOnceAndMissingDurationsHaveAnEstimate() {
        val track = OfflineTrackEstimate("song", "Song", null)
        assertEquals(9_600_000L, validateOfflineQueue(listOf(track, track)))
        assertEquals(1_920_000L, validateOfflineQueue(listOf(track.copy(durationSeconds = 60))))
    }
    @Test fun oldSnapshotsRemainCompatibleAndMalformedOfflineCountsAreBounded() {
        val payload = JSONObject().put("playback", JSONObject().put("generatedAt", 1))
        assertNull(decodeWatchPlayback(payload)!!.offlinePack)
        payload.put("offlinePack", JSONObject().put("total", 1000).put("ready", 2000).put("readySeconds", -5))
        val pack = decodeWatchPlayback(payload)!!.offlinePack!!
        assertEquals(100, pack.total); assertEquals(100, pack.ready); assertEquals(0, pack.readySeconds)
    }
}
