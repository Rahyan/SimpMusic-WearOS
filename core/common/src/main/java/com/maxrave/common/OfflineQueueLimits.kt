package com.maxrave.common

data class OfflineTrackEstimate(val id: String, val title: String, val durationSeconds: Int?)

/** Conservative audio estimate at 256 kbps; duplicates download once. */
fun validateOfflineQueue(tracks: List<OfflineTrackEstimate>): Long {
    require(tracks.isNotEmpty() && tracks.size <= 100) { "Choose a queue with 1–100 tracks." }
    require(tracks.all { it.id.isNotBlank() && it.title.isNotBlank() }) { "Queue metadata is incomplete." }
    val estimate = tracks.distinctBy { it.id }.sumOf { (it.durationSeconds?.takeIf { it > 0 } ?: 300).toLong() * 32_000 }
    require(estimate <= 512L * 1024 * 1024) { "This queue exceeds the estimated 512 MB pack limit." }
    return estimate
}
