package com.maxrave.simpmusic.companion.bridge

import com.maxrave.common.decodeWatchPlayback
import com.maxrave.simpmusic.companion.playerSnapshotIsFresh
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaybackSnapshotCacheTest {
    private fun snapshot(generatedAt: Long) = decodeWatchPlayback(
        JSONObject().put("playback", JSONObject().put("generatedAt", generatedAt)),
    )!!

    @Test fun duplicateReplyDoesNotRefreshAnExpiredReceipt() {
        val cache = PlaybackSnapshotCache()
        val state = snapshot(100_000)
        assertTrue(cache.receive("watch", state, 1_000))
        assertFalse(cache.receive("watch", snapshot(100_000), 20_000))
        val saved = cache.get("watch")!!
        assertEquals(1_000L, saved.receipt!!.elapsedTimeMs)
        assertFalse(playerSnapshotIsFresh(saved.snapshot, 20_000, saved.receipt))
    }

    @Test fun readingAndReopeningThePlayerDoesNotExtendTheReceipt() {
        val cache = PlaybackSnapshotCache()
        cache.receive("watch", snapshot(100_000), 1_000)
        repeat(5) { assertEquals(1_000L, cache.get("watch")!!.receipt!!.elapsedTimeMs) }
        val saved = cache.get("watch")!!
        assertFalse(playerSnapshotIsFresh(saved.snapshot, 16_001, saved.receipt))
    }

    @Test fun liveResponseSupersedesCachedHistoryAfterAProcessRestart() {
        val restored = ReceivedWatchPlayback(snapshot(Long.MAX_VALUE))
        assertFalse(playerSnapshotIsFresh(restored.snapshot, 1_000, restored.receipt))
        val cache = PlaybackSnapshotCache()
        assertNull(cache.get("watch"))
        assertTrue(cache.receive("watch", snapshot(100_000), 1_000))
        val live = cache.get("watch")!!
        assertTrue(playerSnapshotIsFresh(live.snapshot, 1_000, live.receipt))
    }

    @Test fun delayedOlderReplyCannotReplaceANewerPlayerState() {
        val cache = PlaybackSnapshotCache()
        cache.receive("watch", snapshot(200_000), 1_000)
        assertFalse(cache.receive("watch", snapshot(199_000), 2_000))
        assertEquals(200_000L, cache.get("watch")!!.snapshot.generatedAt)
        assertEquals(1_000L, cache.get("watch")!!.receipt!!.elapsedTimeMs)
    }

    @Test fun newPollRenewsOnlyTheRespondingWatch() {
        val cache = PlaybackSnapshotCache()
        cache.receive("first", snapshot(100_000), 1_000)
        cache.receive("second", snapshot(100_000), 1_000)
        assertTrue(cache.receive("first", snapshot(103_000), 20_000))
        val first = cache.get("first")!!
        val second = cache.get("second")!!
        assertTrue(playerSnapshotIsFresh(first.snapshot, 20_000, first.receipt))
        assertFalse(playerSnapshotIsFresh(second.snapshot, 20_000, second.receipt))
    }
}
