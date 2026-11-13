package com.maxrave.simpmusic.companion.bridge

import com.maxrave.common.WatchPlaybackSnapshot
import com.maxrave.simpmusic.companion.PlaybackReceipt

internal data class ReceivedWatchPlayback(
    val snapshot: WatchPlaybackSnapshot,
    val receipt: PlaybackReceipt? = null,
)

/** Receipts live only in this process; restoring a saved snapshot cannot reactivate controls. */
internal class PlaybackSnapshotCache {
    private val latest = mutableMapOf<String, ReceivedWatchPlayback>()

    @Synchronized
    fun receive(node: String, snapshot: WatchPlaybackSnapshot, elapsedTimeMs: Long): Boolean {
        val previous = latest[node]?.snapshot
        // The listener and awaiting caller both handle the same reply. Do not reset its age.
        if (previous != null && (snapshot.generatedAt < previous.generatedAt || snapshot == previous)) return false
        latest[node] = ReceivedWatchPlayback(snapshot, PlaybackReceipt(elapsedTimeMs))
        return true
    }

    @Synchronized
    fun get(node: String): ReceivedWatchPlayback? = latest[node]
}
