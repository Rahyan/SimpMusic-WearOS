package com.maxrave.simpmusic.companion.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionBridgePayloadsTest {
    @Test
    fun decodeCompanionRequest_returnsEnvelopeWhenValid() {
        val raw =
            JSONObject()
                .put("requestId", "req-1")
                .put("action", "remote")
                .put("payload", JSONObject().put("command", "next"))
                .toString()

        val envelope = decodeCompanionRequest(raw)

        assertNotNull(envelope)
        assertEquals("req-1", envelope?.requestId)
        assertEquals("remote", envelope?.action)
        assertEquals("next", envelope?.payload?.optString("command"))
    }

    @Test
    fun decodeCompanionRequest_returnsNullWhenMissingRequiredFields() {
        val missingAction = JSONObject().put("requestId", "req-2").toString()

        val envelope = decodeCompanionRequest(missingAction)

        assertEquals(null, envelope)
    }

    @Test
    fun isAckForRequest_matchesSuccessfulAck() {
        val response =
            JSONObject()
                .put("requestId", "ack-1")
                .put("action", "remote")
                .put("ok", true)
                .put("message", "ok")
                .toString()

        assertTrue(isAckForRequest(response, "ack-1", "remote"))
    }

    @Test
    fun isAckForRequest_rejectsFailedOrMismatchedAck() {
        val failedAck =
            JSONObject()
                .put("requestId", "ack-1")
                .put("action", "remote")
                .put("ok", false)
                .put("message", "failed")
                .toString()
        val wrongRequestId =
            JSONObject()
                .put("requestId", "ack-2")
                .put("action", "remote")
                .put("ok", true)
                .toString()

        assertFalse(isAckForRequest(failedAck, "ack-1", "remote"))
        assertFalse(isAckForRequest(wrongRequestId, "ack-1", "remote"))
    }

    @Test
    fun responseIndicatesAuthExpiry_detectsMessageAndDataFlag() {
        val messageResponse =
            CompanionResponseEnvelope(
                requestId = "r1",
                action = "status",
                ok = false,
                message = "Unauthorized (401). Please re-login.",
                data = null,
            )
        val dataResponse =
            CompanionResponseEnvelope(
                requestId = "r2",
                action = "status",
                ok = false,
                message = "Needs refresh",
                data = JSONObject().put("authExpired", true),
            )

        assertTrue(responseIndicatesAuthExpiry(messageResponse))
        assertTrue(responseIndicatesAuthExpiry(dataResponse))
    }
}
