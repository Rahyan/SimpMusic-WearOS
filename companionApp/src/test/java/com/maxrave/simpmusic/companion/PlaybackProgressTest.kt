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

    @Test fun eachPollStaysFreshWhenWatchClockIsSlightlyAhead() {
        repeat(5) { poll ->
            val phoneWallTime = 100_000L + poll * 3_000
            val receivedAt = 1_000L + poll * 3_000
            val state = snapshot(phoneWallTime + 75)
            val receipt = PlaybackReceipt(receivedAt)
            assertTrue("A fresh response must not disable controls at poll $poll",
                playerSnapshotIsFresh(state, receivedAt, receipt))
        }
    }

    @Test fun freshResponseDoesNotDependOnWatchAndPhoneWallClocksMatching() {
        val receivedAt = 1_000L
        for (offset in listOf(-60_000L, 60_000L)) {
            val state = snapshot(100_000L + offset)
            assertTrue("Live response with watch clock offset $offset",
                playerSnapshotIsFresh(state, receivedAt, PlaybackReceipt(receivedAt)))
        }
    }

    @Test fun newSnapshotMustNotWaitForTheNextUiClockTick() {
        val state = snapshot(10_950)
        assertFalse(state.isFresh(10_000)) // The cached tick predates a legitimate new packet.
        val receipt = PlaybackReceipt(elapsedTimeMs = 1_000)
        assertTrue(playerSnapshotIsFresh(state, tickAt = 0, receipt))
        assertTrue(playerSnapshotIsFresh(state, tickAt = 16_000, receipt))
        assertFalse(playerSnapshotIsFresh(state, tickAt = 16_001, receipt))
    }

    @Test fun savedSnapshotWithoutALiveReceiptCannotEnableControls() {
        assertFalse(playerSnapshotIsFresh(snapshot(1_000), 1_000, null))
        assertFalse(playerSnapshotIsFresh(null, 1_000, PlaybackReceipt(1_000)))
    }

    @Test fun watchWallClockCannotStopFreshnessFromExpiring() {
        val receipt = PlaybackReceipt(elapsedTimeMs = 2_000)
        for (generatedAt in listOf(1L, Long.MAX_VALUE)) {
            assertTrue(playerSnapshotIsFresh(snapshot(generatedAt), 17_000, receipt))
            assertFalse(playerSnapshotIsFresh(snapshot(generatedAt), 17_001, receipt))
        }
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
