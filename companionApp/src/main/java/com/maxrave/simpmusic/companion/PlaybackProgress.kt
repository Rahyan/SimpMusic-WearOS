package com.maxrave.simpmusic.companion

import com.maxrave.common.WatchPlaybackSnapshot

internal data class PlaybackReceipt(val elapsedTimeMs: Long) {
    fun ageAt(tickAt: Long): Long = (tickAt - elapsedTimeMs).coerceAtLeast(0)
}

/** Only a live response establishes freshness; phone/watch wall clocks need not agree. */
internal fun playerSnapshotIsFresh(snapshot: WatchPlaybackSnapshot?, tickAt: Long, receipt: PlaybackReceipt?): Boolean {
    if (snapshot == null || receipt == null) return false
    // A response can arrive between UI ticks. It is already fresh before the next tick.
    return receipt.ageAt(tickAt) <= 15_000
}

/** Predict only within the freshness window; never extrapolate a paused/buffering player. */
internal fun projectedPlaybackPosition(positionMs: Long, durationMs: Long, playing: Boolean,
    buffering: Boolean, elapsedMs: Long): Long {
    val base = positionMs.coerceAtLeast(0)
    if (durationMs <= 0) return base
    val clamped = base.coerceAtMost(durationMs)
    if (!playing || buffering) return clamped
    return clamped + elapsedMs.coerceIn(0, 15_000).coerceAtMost(durationMs - clamped)
}
