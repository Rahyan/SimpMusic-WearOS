package com.maxrave.simpmusic.companion.bridge

import org.json.JSONObject

data class CompanionRequestEnvelope(
    val requestId: String,
    val action: String,
    val payload: JSONObject,
)

data class CompanionResponseEnvelope(
    val requestId: String,
    val action: String,
    val ok: Boolean,
    val message: String,
    val data: JSONObject?,
)

fun decodeCompanionRequest(raw: String): CompanionRequestEnvelope? {
    val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val requestId = envelope.optString("requestId").trim()
    val action = envelope.optString("action").trim()
    if (requestId.isBlank() || action.isBlank()) return null
    return CompanionRequestEnvelope(
        requestId = requestId,
        action = action,
        payload = envelope.optJSONObject("payload") ?: JSONObject(),
    )
}

fun decodeCompanionResponse(raw: String): CompanionResponseEnvelope? {
    val response = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val requestId = response.optString("requestId").trim()
    val action = response.optString("action").trim()
    if (requestId.isBlank() || action.isBlank()) return null
    return CompanionResponseEnvelope(
        requestId = requestId,
        action = action,
        ok = response.optBoolean("ok", false),
        message = response.optString("message").trim(),
        data = response.optJSONObject("data"),
    )
}

fun isAckForRequest(
    raw: String,
    requestId: String,
    action: String,
): Boolean {
    val response = decodeCompanionResponse(raw) ?: return false
    return response.requestId == requestId &&
        response.action == action &&
        response.ok
}

fun responseIndicatesAuthExpiry(response: CompanionResponseEnvelope): Boolean {
    if (response.data?.optBoolean("authExpired", false) == true) return true
    val message = response.message.lowercase()
    return message.contains("expired") ||
        message.contains("unauthorized") ||
        message.contains("401") ||
        message.contains("re-login") ||
        message.contains("relogin") ||
        message.contains("sign in again")
}
