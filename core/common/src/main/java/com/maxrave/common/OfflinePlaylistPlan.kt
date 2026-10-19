package com.maxrave.common

data class OfflinePlaylistPlan(
    val downloadIds: List<String>,
    val removeIds: Set<String>,
    val ownedEstimates: Map<String, Long>,
    val budgetBlocked: Boolean,
)

fun estimatedOfflineBytes(track: OfflineTrackEstimate): Long =
    (track.durationSeconds?.takeIf { it > 0 } ?: 300).toLong() * 32_000

/** Only files created by playlist sync can be removed; manual and pinned music is protected. */
fun planOfflinePlaylists(
    required: List<OfflineTrackEstimate>,
    owned: Map<String, Long>,
    existing: Set<String>,
    busyOrReady: Set<String>,
    protected: Set<String>,
    reservedBytes: Map<String, Long>,
    budgetBytes: Long,
    removeStale: Boolean,
): OfflinePlaylistPlan {
    require(budgetBytes > 0)
    require(required.all { it.id.isNotBlank() && it.title.isNotBlank() })
    val tracks = required.distinctBy { it.id }
    val wanted = tracks.mapTo(mutableSetOf()) { it.id }
    val remove = if (removeStale) owned.keys - wanted - protected else emptySet()
    val retained = owned.filterKeys { it !in remove }.toMutableMap()
    var reserved = retained.entries.sumOf { (id, estimate) -> (reservedBytes[id] ?: estimate).coerceAtLeast(0) }
    var blocked = false
    val download = mutableListOf<String>()
    for (track in tracks) {
        if (track.id in busyOrReady) continue
        val estimate = estimatedOfflineBytes(track)
        val alreadyOwned = track.id in retained
        val newOwnership = !alreadyOwned && track.id !in existing
        val extra = if (newOwnership) estimate else 0L
        if (alreadyOwned && reserved > budgetBytes) { blocked = true; continue }
        if (extra > 0 && (reserved > budgetBytes || extra > budgetBytes - reserved)) {
            blocked = true
            continue
        }
        if (newOwnership) { retained[track.id] = estimate; reserved += estimate }
        download += track.id
    }
    return OfflinePlaylistPlan(download, remove, retained, blocked)
}

/** Watch registration only; the phone's downloader leaves these callbacks unset. */
object WatchOfflineDownloadEvents {
    @Volatile var onManualDownload: (suspend (String) -> Unit)? = null
    @Volatile var onPolicyChanged: (suspend () -> Unit)? = null
}
