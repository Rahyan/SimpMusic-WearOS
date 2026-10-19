package com.maxrave.common

data class ManifestProgress(val revision: String, val offset: Int, val ids: Set<String>)

/** Retry-safe ordered pages. Only the final acknowledged page authorizes reconciliation. */
fun mergeManifestPage(previous: ManifestProgress?, revision: String, offset: Int, ids: List<String>): ManifestProgress {
    require(revision.length == 64 && revision.all { it in "0123456789abcdef" }) { "Invalid collection revision." }
    require(offset >= 0 && ids.size <= 60 && ids.distinct().size == ids.size) { "Invalid collection page." }
    require(ids.all { it.isNotBlank() && it.length <= 512 }) { "Invalid collection identifier." }
    if (offset == 0) return ManifestProgress(revision, ids.size, ids.toSet())
    require(previous != null && previous.revision == revision) { "Restart library sync from the first page." }
    if (offset + ids.size == previous.offset && previous.ids.containsAll(ids)) return previous
    require(offset == previous.offset) { "Library page arrived out of order." }
    require(previous.ids.intersect(ids.toSet()).isEmpty()) { "Duplicate collection identifier." }
    require(previous.ids.size + ids.size <= 10_000) { "Watch library limit reached." }
    return ManifestProgress(revision, offset + ids.size, previous.ids + ids)
}
