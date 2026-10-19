package com.maxrave.simpmusic.companion

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
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

/** Read the real Compose accessibility tree while a connected isolated watch is playing. */
class PhysicalProgressTest {
    @Test fun displayedClockAdvancesBetweenWatchSnapshots(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("physicalProgress") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        check(context.packageName == "com.maxrave.simpmusic.test.dev")
        val node = getConnectedNodes(context).single().id
        var response = JSONObject()
        suspend fun request(action: String, payload: JSONObject = JSONObject()): JSONObject {
            val reply = requestWatch(context, node, action, payload, 30_000)
            check(reply?.ok == true) { "Watch did not acknowledge $action" }
            response = JSONObject(reply.data!!)
            return response.getJSONObject("playback")
        }
        suspend fun command(name: String, position: Long? = null, id: String? = null): JSONObject {
            val payload = JSONObject().put("command", name)
            if (position != null) payload.put("positionMs", position)
            if (id != null) payload.put("videoId", id)
            return request(Bridge.ACTION_REMOTE, payload)
        }
        var initial = request(Bridge.ACTION_PLAYBACK_STATE)
        if (initial.optString("videoId").isBlank() || initial.optLong("durationMs") <= 120_000) {
            val pack = response.getJSONObject("offlinePack")
            check(pack.getInt("ready") > 0) { "Prepare an offline test track first" }
            request(Bridge.ACTION_REMOTE, JSONObject().put("command", Bridge.REMOTE_PLAY_OFFLINE)
                .put("packRevision", pack.getString("revision")))
            withTimeout(60_000) {
                do { delay(1_000); initial = request(Bridge.ACTION_PLAYBACK_STATE) }
                while (initial.optLong("durationMs") <= 120_000 || !initial.optBoolean("isPlaying"))
            }
        }
        val id = initial.getString("videoId")
        check(id.isNotBlank() && initial.getLong("durationMs") > 120_000)
        var scenario: ActivityScenario<CompanionMainActivity>? = null
        try {
            command(Bridge.REMOTE_SEEK, 60_000, id)
            if (!request(Bridge.ACTION_PLAYBACK_STATE).getBoolean("isPlaying")) command(Bridge.REMOTE_PLAY_PAUSE)
            scenario = ActivityScenario.launch(CompanionMainActivity::class.java)
            scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            delay(6_000)
            withTimeout(60_000) {
                while (!request(Bridge.ACTION_PLAYBACK_STATE).getBoolean("isPlaying")) delay(1_000)
            }
            val samples = mutableListOf<Pair<Long, Int>>()
            val sliderSamples = mutableListOf<Float>()
            repeat(9) {
                val player = request(Bridge.ACTION_PLAYBACK_STATE)
                assertTrue("Test track stopped during clock check", player.getBoolean("isPlaying"))
                assertFalse("Playing audio incorrectly reported buffering", player.getBoolean("isBuffering"))
                val root = automation.rootInActiveWindow ?: automation.windows.mapNotNull { it.root }
                    .firstOrNull { it.packageName?.toString() == context.packageName }
                assertNotNull("Companion must be visible", root)
                val clocks = mutableListOf<Int>()
                fun visit(node: AccessibilityNodeInfo) {
                    node.rangeInfo?.let { range ->
                        if (range.min == 0f && range.max == 1f) sliderSamples += range.current
                    }
                    val text = node.text?.toString().orEmpty()
                    if (text.matches(Regex("\\d+:\\d{2}"))) {
                        val parts = text.split(':')
                        clocks += parts[0].toInt() * 60 + parts[1].toInt()
                    }
                    for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
                }
                visit(root!!)
                val elapsed = clocks.firstOrNull { it < initial.getLong("durationMs") / 1_000 - 10 }
                assertNotNull("Elapsed clock missing from companion UI", elapsed)
                samples += SystemClock.elapsedRealtime() to elapsed!!
                Log.i("CompanionDeviceCheck", "displayed elapsedSeconds=$elapsed")
                delay(1_000)
            }
            val changes = samples.zipWithNext { a, b -> b.second - a.second }
            assertTrue("Clock skipped seconds: $changes", changes.all { it in 0..2 })
            assertTrue("Clock stayed on remote snapshots: $samples", samples.map { it.second }.distinct().size >= 7)
            assertTrue("Clock did not advance: $samples", samples.last().second - samples.first().second >= 7)
            assertTrue("Slider did not advance between snapshots: $sliderSamples", sliderSamples.distinct().size >= 7)
        } finally {
            scenario?.close()
            val current = request(Bridge.ACTION_PLAYBACK_STATE)
            if (current.optString("videoId") == id) {
                command(Bridge.REMOTE_SEEK, initial.getLong("positionMs"), id)
                if (initial.getBoolean("isPlaying") != request(Bridge.ACTION_PLAYBACK_STATE).getBoolean("isPlaying")) {
                    command(Bridge.REMOTE_PLAY_PAUSE)
                }
            }
        }
    }
}
