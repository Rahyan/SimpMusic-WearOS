package com.maxrave.simpmusic.companion.bridge

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

data class ConnectedNode(
    val id: String,
    val name: String,
)

suspend fun getConnectedNodes(context: Context): List<ConnectedNode> {
    val nodeClient = Wearable.getNodeClient(context.applicationContext)
    val nodes =
        suspendCancellableCoroutine<List<Node>> { continuation ->
            nodeClient.connectedNodes
                .addOnSuccessListener { value ->
                    if (continuation.isActive) continuation.resume(value)
                }.addOnFailureListener {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        }
    return nodes.map { ConnectedNode(id = it.id, name = it.displayName) }
}

suspend fun sendMessageToNode(
    context: Context,
    nodeId: String,
    path: String,
    payload: ByteArray,
): Boolean {
    val messageClient = Wearable.getMessageClient(context.applicationContext)
    return suspendCancellableCoroutine { continuation ->
        messageClient.sendMessage(nodeId, path, payload)
            .addOnSuccessListener {
                if (continuation.isActive) continuation.resume(true)
            }.addOnFailureListener {
                if (continuation.isActive) continuation.resume(false)
            }
    }
}

fun buildCompanionEnvelope(
    action: String,
    payload: JSONObject,
    requestId: String = UUID.randomUUID().toString(),
): ByteArray =
    JSONObject()
        .put("protocolVersion", com.maxrave.common.WearCompanionBridge.PROTOCOL_VERSION)
        .put("requestId", requestId)
        .put("action", action)
        .put("payload", payload)
        .toString()
        .toByteArray(Charsets.UTF_8)

suspend fun requestWatch(context: Context, nodeId: String, action: String, payload: JSONObject = JSONObject(), timeoutMs: Long = 15_000): com.maxrave.common.BridgeAcknowledgement? {
    val id = UUID.randomUUID().toString()
    val body = buildCompanionEnvelope(action, payload, id)
    if (body.size > 100_000) return null
    return com.maxrave.common.BridgeRequests.sendAndAwait(nodeId, id, action, timeoutMs) {
        sendMessageToNode(context, nodeId, com.maxrave.common.WearCompanionBridge.PATH_REQUEST, body)
    }
}
