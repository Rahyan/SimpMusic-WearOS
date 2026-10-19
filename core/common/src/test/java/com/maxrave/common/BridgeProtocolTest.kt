package com.maxrave.common

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BridgeProtocolTest {
    @Test fun manifestPagesRetryWithoutRemovingEarlierPages() {
        val revision = bridgeDigest("library")
        val first = mergeManifestPage(null, revision, 0, listOf("a", "b"))
        val final = mergeManifestPage(first, revision, 2, listOf("c"))
        assertEquals(setOf("a", "b", "c"), final.ids)
        assertEquals(final, mergeManifestPage(final, revision, 2, listOf("c")))
        assertEquals(setOf("d"), mergeManifestPage(final, bridgeDigest("changed"), 0, listOf("d")).ids)
        assertThrows(IllegalArgumentException::class.java) { mergeManifestPage(first, revision, 3, listOf("c")) }
        assertThrows(IllegalArgumentException::class.java) { mergeManifestPage(first, bridgeDigest("changed"), 2, listOf("c")) }
    }
    @Test fun acknowledgementRequiresTheExactNodeRequestAndAction() = runBlocking {
        val ack = BridgeAcknowledgement(true, "Applied")
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            BridgeRequests.sendAndAwait("watch-a", "one", "seek", 1_000) { true }
        }
        assertFalse(BridgeRequests.complete("watch-b", "one", "seek", ack))
        assertFalse(BridgeRequests.complete("watch-a", "old", "seek", ack))
        assertFalse(BridgeRequests.complete("watch-a", "one", "next", ack))
        assertTrue(BridgeRequests.complete("watch-a", "one", "seek", ack))
        assertEquals(ack, waiting.await())
        assertFalse(BridgeRequests.complete("watch-a", "one", "seek", ack))
    }
    @Test fun timeoutAndCancellationRemovePendingRequests() = runBlocking {
        assertNull(BridgeRequests.sendAndAwait("w", "timeout", "play", 20) { true })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { BridgeRequests.sendAndAwait("w", "cancel", "play") { true } }
        job.cancelAndJoin()
        assertFalse(BridgeRequests.complete("w", "cancel", "play", BridgeAcknowledgement(true, "")))
        assertNull(BridgeRequests.sendAndAwait("w", "failed", "play") { false })
    }
    @Test fun fastRepliesAreRegisteredBeforeTransportCompletes() = runBlocking {
        val reply = BridgeAcknowledgement(false, "Queue changed")
        assertEquals(reply, BridgeRequests.sendAndAwait("w", "fast", "queue") {
            BridgeRequests.complete("w", "fast", "queue", reply); true
        })
    }
    @Test fun metadataChangesInvalidateFingerprintButCollectionOrderDoesNot() {
        val a = JSONObject().put("id", "a").put("title", "First").put("tracks", JSONArray(listOf("x", "y")))
        val b = JSONObject().put("title", "Second").put("id", "b")
        assertEquals(collectionFingerprint(listOf(a,b)), collectionFingerprint(listOf(b,a)))
        val before = collectionFingerprint(listOf(a,b))
        a.put("title", "Renamed")
        assertNotEquals(before, collectionFingerprint(listOf(a,b)))
        val renamed = collectionFingerprint(listOf(a,b))
        a.put("tracks", JSONArray(listOf("y", "x")))
        assertNotEquals(renamed, collectionFingerprint(listOf(a,b)))
        assertNotEquals(nodeSyncKey("songs", "watch-a"), nodeSyncKey("songs", "watch-b"))
    }
    @Test fun incompleteAndOtherDeviceMembershipIsPreserved() {
        val owned = setOf("a", "b", "c")
        assertEquals(emptySet<String>(), removedRemoteMembership(owned, setOf("a"), false))
        assertEquals(setOf("b"), removedRemoteMembership(owned, setOf("a"), true, setOf("c")))
        assertEquals(owned, removedRemoteMembership(owned, emptySet(), true))
    }
    @Test fun replayCacheExpiresAndRemainsBounded() {
        val cache = BridgeReplyCache(2, 10)
        cache.put("a", "one", 100); cache.put("b", "two", 100); cache.put("c", "three", 100)
        assertNull(cache.get("a", 100)); assertEquals("three", cache.get("c", 105))
        assertNull(cache.get("c", 111)); assertNull(cache.get("b", 99))
    }
    @Test fun invalidAndStaleSnapshotsCannotEnableControls() {
        assertNull(decodeWatchPlayback(JSONObject()))
        assertNull(decodeWatchPlayback(JSONObject().put("playback", JSONObject())))
        val data = JSONObject().put("playback", JSONObject().put("generatedAt", 100_000).put("positionMs", -5).put("durationMs", -9))
        val snapshot = decodeWatchPlayback(data)!!
        assertEquals(0L, snapshot.positionMs); assertEquals(0L, snapshot.durationMs)
        assertTrue(snapshot.isFresh(105_000)); assertFalse(snapshot.isFresh(120_000)); assertFalse(snapshot.isFresh(99_000))
    }
}
