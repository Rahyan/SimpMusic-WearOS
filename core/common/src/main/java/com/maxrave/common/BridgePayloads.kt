package com.maxrave.common

import org.json.JSONArray
import org.json.JSONObject

fun canonicalBridgeJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + canonicalBridgeJson(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalBridgeJson(value.get(it)) }
    is String -> JSONObject.quote(value)
    else -> value.toString()
}

fun collectionFingerprint(payloads: List<JSONObject>): String =
    bridgeDigest(payloads.map(::canonicalBridgeJson).sorted().joinToString("\n"))

data class WatchQueueTrack(val index: Int, val videoId: String, val title: String, val artist: String)
data class WatchOfflinePack(val revision: String, val total: Int, val ready: Int, val readySeconds: Int,
    val chargingOnly: Boolean, val message: String)
data class WatchPlaybackSnapshot(
    val generatedAt: Long, val title: String, val artist: String, val artwork: String,
    val videoId: String, val isPlaying: Boolean, val isBuffering: Boolean,
    val positionMs: Long, val durationMs: Long, val canNext: Boolean, val canPrevious: Boolean,
    val queueRevision: String, val queueSize: Int, val currentIndex: Int,
    val queue: List<WatchQueueTrack>, val volume: Int, val maxVolume: Int,
    val offlinePack: WatchOfflinePack? = null,
) {
    fun isFresh(now: Long): Boolean = now - generatedAt in 0..15_000
}

fun decodeWatchPlayback(data: JSONObject?): WatchPlaybackSnapshot? {
    val state = data?.optJSONObject("playback") ?: return null
    val timestamp = state.optLong("generatedAt")
    if (timestamp <= 0) return null
    val queue = state.optJSONArray("queue") ?: JSONArray()
    return WatchPlaybackSnapshot(
        generatedAt = timestamp, title = state.optString("title"), artist = state.optString("artist"),
        artwork = state.optString("artwork"), videoId = state.optString("videoId"),
        isPlaying = state.optBoolean("isPlaying"), isBuffering = state.optBoolean("isBuffering"),
        positionMs = state.optLong("positionMs").coerceAtLeast(0), durationMs = state.optLong("durationMs").coerceAtLeast(0),
        canNext = state.optBoolean("canNext"), canPrevious = state.optBoolean("canPrevious"),
        queueRevision = state.optString("queueRevision"), queueSize = state.optInt("queueSize").coerceAtLeast(0),
        currentIndex = state.optInt("currentIndex", -1),
        queue = (0 until minOf(queue.length(), 40)).mapNotNull { index ->
            val track = queue.optJSONObject(index) ?: return@mapNotNull null
            val id = track.optString("videoId")
            if (id.isBlank()) null else WatchQueueTrack(track.optInt("index", -1), id,
                track.optString("title"), track.optString("artist"))
        }, volume = state.optInt("volume"), maxVolume = state.optInt("maxVolume"),
        offlinePack = data.optJSONObject("offlinePack")?.let { pack ->
            val total = pack.optInt("total").coerceIn(0, 100)
            WatchOfflinePack(pack.optString("revision"), total, pack.optInt("ready").coerceIn(0, total),
                pack.optInt("readySeconds").coerceAtLeast(0), pack.optBoolean("chargingOnly"), pack.optString("message"))
        },
    )
}
