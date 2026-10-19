package com.maxrave.common

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

data class BridgeAcknowledgement(val ok: Boolean, val message: String, val data: String? = null)

/** Application-level acknowledgements, scoped to the sending node and exact request/action. */
object BridgeRequests {
    private data class Key(val node: String, val request: String, val action: String)
    private val pending = ConcurrentHashMap<Key, CompletableDeferred<BridgeAcknowledgement>>()

    suspend fun sendAndAwait(
        node: String, request: String, action: String, timeoutMs: Long = 15_000,
        send: suspend () -> Boolean,
    ): BridgeAcknowledgement? {
        val key = Key(node, request, action)
        val waiter = CompletableDeferred<BridgeAcknowledgement>()
        check(pending.putIfAbsent(key, waiter) == null) { "Request already pending" }
        return try {
            withTimeoutOrNull(timeoutMs) {
                if (send()) waiter.await() else null
            }
        } finally {
            pending.remove(key, waiter)
            waiter.cancel()
        }
    }

    fun complete(node: String, request: String, action: String, ack: BridgeAcknowledgement): Boolean =
        pending[Key(node, request, action)]?.complete(ack) == true
}

fun bridgeDigest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

fun nodeSyncKey(category: String, node: String): String = "${category}_v2_${bridgeDigest(node)}"

/** Bounded replay cache: repeated command IDs must not toggle playback a second time. */
class BridgeReplyCache(private val capacity: Int = 64, private val lifetimeMs: Long = 120_000) {
    private data class Reply(val body: String, val timestamp: Long)
    private val replies = linkedMapOf<String, Reply>()
    @Synchronized fun get(key: String, now: Long): String? {
        val reply = replies[key] ?: return null
        if (now - reply.timestamp !in 0..lifetimeMs) {
            replies.remove(key)
            return null
        }
        return reply.body
    }
    @Synchronized fun put(key: String, body: String, now: Long) {
        if (body.length > 100_000) return
        replies[key] = Reply(body, now)
        while (replies.size > capacity) replies.remove(replies.keys.first())
    }
}
