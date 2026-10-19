package com.maxrave.simpmusic.companion

import com.maxrave.common.WatchPlaybackSnapshot

internal data class PlaybackReceipt(val wallTimeMs: Long, val elapsedTimeMs: Long)

/** New packets must not wait for a cached UI tick; later wall-clock adjustments must not freeze expiry. */
internal fun playerSnapshotIsFresh(snapshot: WatchPlaybackSnapshot?, tickAt: Long, receipt: PlaybackReceipt): Boolean {
    if (snapshot == null) return false
    val elapsed = (tickAt - receipt.elapsedTimeMs).coerceAtLeast(0)
    val effectiveWallTime = try { Math.addExact(receipt.wallTimeMs, elapsed) } catch (_: ArithmeticException) { return false }
    return snapshot.isFresh(effectiveWallTime)
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
