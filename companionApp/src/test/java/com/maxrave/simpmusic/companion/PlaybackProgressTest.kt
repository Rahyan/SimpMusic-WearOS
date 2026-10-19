package com.maxrave.simpmusic.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.maxrave.common.decodeWatchPlayback
import org.json.JSONObject
import org.junit.Test

class PlaybackProgressTest {
    private fun snapshot(generatedAt: Long) = decodeWatchPlayback(JSONObject().put("playback",
        JSONObject().put("generatedAt", generatedAt)))!!

    @Test fun newSnapshotMustNotWaitForTheNextUiClockTick() {
        val state = snapshot(10_950)
        assertFalse(state.isFresh(10_000)) // The cached tick predates a legitimate new packet.
        val receipt = PlaybackReceipt(wallTimeMs = 11_000, elapsedTimeMs = 1_000)
        assertTrue(playerSnapshotIsFresh(state, tickAt = 0, receipt))
        assertFalse(playerSnapshotIsFresh(state, tickAt = 15_951, receipt))
    }

    @Test fun receiptDoesNotReviveExpiredOrFutureSnapshots() {
        assertFalse(playerSnapshotIsFresh(snapshot(1_000), 1_000, PlaybackReceipt(30_000, 1_000)))
        assertFalse(playerSnapshotIsFresh(snapshot(50_000), 1_000, PlaybackReceipt(40_000, 1_000)))
        assertFalse(playerSnapshotIsFresh(null, 1_000, PlaybackReceipt(40_000, 1_000)))
    }

    @Test fun wallClockChangesDoNotStopFreshnessFromExpiring() {
        val receipt = PlaybackReceipt(wallTimeMs = 1_000_050, elapsedTimeMs = 2_000)
        assertTrue(playerSnapshotIsFresh(snapshot(1_000_000), 16_950, receipt))
        assertFalse(playerSnapshotIsFresh(snapshot(1_000_000), 16_951, receipt))
    }
    @Test fun advancesEachSecondBetweenPackets() {
        for (elapsed in 0L..6L) assertEquals(97_000 + elapsed * 1_000,
            projectedPlaybackPosition(97_000, 200_000, true, false, elapsed * 1_000))
    }
    @Test fun pauseBufferSeekAndTrackChangeUseTheNewAnchor() {
        assertEquals(97_000L, projectedPlaybackPosition(97_000, 200_000, false, false, 6_000))
        assertEquals(97_000L, projectedPlaybackPosition(97_000, 200_000, true, true, 6_000))
        assertEquals(20_000L, projectedPlaybackPosition(20_000, 200_000, false, false, 0))
        assertEquals(1_000L, projectedPlaybackPosition(0, 200_000, true, false, 1_000))
    }
    @Test fun stopsAtEndOrFreshnessLimitAndHandlesInvalidTiming() {
        assertEquals(200_000L, projectedPlaybackPosition(199_000, 200_000, true, false, 6_000))
        assertEquals(112_000L, projectedPlaybackPosition(97_000, 200_000, true, false, 60_000))
        assertEquals(97_000L, projectedPlaybackPosition(97_000, 200_000, true, false, -1))
        assertEquals(0L, projectedPlaybackPosition(-1, 0, true, false, 6_000))
        assertEquals(Long.MAX_VALUE, projectedPlaybackPosition(Long.MAX_VALUE - 1, Long.MAX_VALUE, true, false, 6_000))
    }
}
