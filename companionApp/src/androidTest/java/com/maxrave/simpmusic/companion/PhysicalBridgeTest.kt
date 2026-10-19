package com.maxrave.simpmusic.companion

import androidx.test.platform.app.InstrumentationRegistry
import com.maxrave.common.WearCompanionBridge as Bridge
import com.maxrave.simpmusic.companion.bridge.getConnectedNodes
import com.maxrave.simpmusic.companion.bridge.requestWatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in round trips against the isolated watch. Do not run during sustained playback checks. */
class PhysicalBridgeTest {
    @Test fun acknowledgementsSeekAndStaleCommands(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(InstrumentationRegistry.getArguments().getString("physicalBridge") == "true")
        check(context.packageName == "com.maxrave.simpmusic.test.dev")
        val node = getConnectedNodes(context).single().id
        suspend fun state(): JSONObject {
            val reply = requestWatch(context, node, Bridge.ACTION_PLAYBACK_STATE, timeoutMs = 30_000)
            assertNotNull("Watch did not acknowledge state", reply)
            assertTrue(reply!!.ok)
            return JSONObject(reply.data!!).getJSONObject("playback")
        }
        suspend fun command(payload: JSONObject, accepted: Boolean = true): JSONObject {
            val reply = requestWatch(context, node, Bridge.ACTION_REMOTE, payload, 30_000)
            assertNotNull("Watch did not acknowledge command", reply)
            assertEquals(accepted, reply!!.ok)
            if (accepted) assertNotNull("Accepted command omitted player state", reply.data)
            return reply.data?.let { JSONObject(it).getJSONObject("playback") } ?: state()
        }
        val initial = state()
        val id = initial.getString("videoId")
        val duration = initial.getLong("durationMs")
        assertTrue("Load a track on the watch first", id.isNotBlank() && duration > 60_000)
        val wasPlaying = initial.getBoolean("isPlaying")
        try {
            if (wasPlaying) command(JSONObject().put("command", Bridge.REMOTE_PLAY_PAUSE))
            val seek = command(JSONObject().put("command", Bridge.REMOTE_SEEK).put("videoId", id).put("positionMs", 45_000))
            assertFalse(seek.getBoolean("isPlaying"))
            assertTrue(kotlin.math.abs(seek.getLong("positionMs") - 45_000) < 1_500)
            withTimeout(30_000) {
                while (state().getBoolean("isBuffering")) delay(500)
            }
            command(JSONObject().put("command", Bridge.REMOTE_SEEK).put("videoId", "stale-test-id").put("positionMs", 0), false)
            command(JSONObject().put("command", Bridge.REMOTE_QUEUE_REMOVE).put("queueRevision", "stale-test-revision")
                .put("index", 0).put("videoId", id), false)
            val unchanged = state()
            assertEquals(initial.getString("queueRevision"), unchanged.getString("queueRevision"))
            assertEquals(initial.getInt("queueSize"), unchanged.getInt("queueSize"))
            assertEquals(id, unchanged.getString("videoId"))
            assertTrue(kotlin.math.abs(unchanged.getLong("positionMs") - 45_000) < 1_500)
        } finally {
            val current = state()
            if (current.optString("videoId") == id) {
                command(JSONObject().put("command", Bridge.REMOTE_SEEK).put("videoId", id)
                    .put("positionMs", initial.getLong("positionMs")))
                if (wasPlaying != state().getBoolean("isPlaying")) {
                    command(JSONObject().put("command", Bridge.REMOTE_PLAY_PAUSE))
                    delay(1_000)
                }
            }
        }
    }
}
