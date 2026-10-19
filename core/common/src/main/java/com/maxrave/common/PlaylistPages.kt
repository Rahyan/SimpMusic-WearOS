package com.maxrave.common

/** An unreadable count must never turn a partial or unavailable response into an empty playlist. */
fun matchesReportedPlaylistCount(reportedCount: String?, decodedCount: Int): Boolean {
    val count = reportedCount?.trim()?.split("\\s+".toRegex(), limit = 2)?.firstOrNull()?.toIntOrNull()
    return count != null && count >= 0 && count == decodedCount
}

/** Failed, cyclic or unbounded pagination must never be presented as a complete playlist. */
suspend fun <T> collectPlaylistPages(
    initial: List<T>,
    firstToken: String?,
    maxPages: Int = 100,
    fetch: suspend (String) -> Pair<List<T>, String?>,
): List<T> {
    require(maxPages in 1..1000)
    val result = initial.toMutableList()
    val visited = mutableSetOf<String>()
    var next = firstToken
    while (next != null) {
        check(visited.size < maxPages && visited.add(next)) { "Playlist pagination did not finish." }
        val page = fetch(next)
        result += page.first
        next = page.second
    }
    return result
}
