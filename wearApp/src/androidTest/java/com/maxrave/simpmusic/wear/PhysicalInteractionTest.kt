package com.maxrave.simpmusic.wear

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.AudioManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in real input checks. Preview mode needs no account, media or connected physical watch. */
class PhysicalInteractionTest {
    @Test fun drawerFollowsHeldTouchAndBezelMovesOneVolumeStep() {
        val arguments = InstrumentationRegistry.getArguments()
        val preview = arguments.getString("interactionPreview") == "true"
        assumeTrue(preview || arguments.getString("physicalInteractions") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "com.maxrave.simpmusic.test.dev")
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val width = context.resources.displayMetrics.widthPixels.toFloat()
        val height = context.resources.displayMetrics.heightPixels.toFloat()
        fun find(text: String): AccessibilityNodeInfo? {
            fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (!node.refresh()) return null
                if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it)?.let { found -> return found } }
                return null
            }
            return automation.rootInActiveWindow?.let(::visit)
        }
        fun screenshot(name: String) {
            val bitmap = checkNotNull(automation.takeScreenshot())
            val directory = File(context.filesDir, "interaction-evidence").apply { check(isDirectory || mkdirs()) }
            try {
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
        var downTime = 0L
        var touching = false
        fun touch(action: Int, y: Float) {
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) downTime = now
            val event = MotionEvent.obtain(downTime, now, action, width / 2, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue("Touch injection failed for action $action", automation.injectInputEvent(event, true))
                touching = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
            } finally { event.recycle() }
        }
        fun holdDrag(from: Float, to: Float) {
            touch(MotionEvent.ACTION_DOWN, from)
            repeat(8) { i -> SystemClock.sleep(30); touch(MotionEvent.ACTION_MOVE, from + (to - from) * (i + 1) / 8) }
            SystemClock.sleep(400)
        }
        fun rotary(axis: Float) {
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN }
            val coords = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_SCROLL, axis) }
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        val intent = if (preview) Intent().setClassName(context, "com.maxrave.simpmusic.wear.WearPreviewActivity").putExtra("mode", "player")
            else Intent(context, WearMainActivity::class.java).putExtra("tile_action", "offline_queue")
        ActivityScenario.launch<androidx.activity.ComponentActivity>(intent).use { scenario ->
            scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            try {
                SystemClock.sleep(5_000)
                holdDrag(height * .94f, height * .47f)
                val held = Rect().also { checkNotNull(find("Controls")).getBoundsInScreen(it) }
                assertTrue("Sheet didn't follow held opening drag: $held", held.top > height * .35f && held.top < height * .9f)
                screenshot("drawer-opening-held")
                touch(MotionEvent.ACTION_UP, height * .47f)
                SystemClock.sleep(900)
                val open = Rect().also { checkNotNull(find("Controls")).getBoundsInScreen(it) }
                assertTrue("Sheet didn't settle open: $open", open.top < height * .3f)
                for (label in listOf("Shuffle off", "Repeat off", "Queue", "Volume", "Continue on phone")) assertNotNull("Missing $label", find(label))
                screenshot("drawer-open")
                val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                // Exercise sustained list scrolling as well as the single-detent volume
                // check below, without treating the list's animated fling as synchronous.
                repeat(3) { rotary(-1f); SystemClock.sleep(40) }
                SystemClock.sleep(300)
                assertEquals("Drawer rotary must scroll, not change volume", before, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                val afterRotary = find("Controls")?.let { node -> Rect().also(node::getBoundsInScreen) }
                screenshot("drawer-after-rotary")
                assertTrue("Drawer did not receive rotary scrolling: before=$open after=$afterRotary", afterRotary == null || afterRotary.top < open.top)
                repeat(5) { rotary(1f); SystemClock.sleep(40) }
                SystemClock.sleep(300)
                val atBoundary = Rect().also { checkNotNull(find("Player drawer handle")).getBoundsInScreen(it) }
                assertTrue("Rotary at the list boundary moved the drawer", atBoundary.top < height * .1f)
                rotary(-1f)
                SystemClock.sleep(300)
                // Rotary may have scrolled the content; the fixed handle must still close it.
                holdDrag(height * .15f, height * .6f)
                val closing = Rect().also { checkNotNull(find("Player drawer handle")).getBoundsInScreen(it) }
                assertTrue("Sheet didn't follow held closing drag: $closing", closing.top > height * .3f)
                screenshot("drawer-closing-held")
                touch(MotionEvent.ACTION_UP, height * .6f)
                SystemClock.sleep(900)
                assertNull("Sheet didn't settle closed", find("Player drawer handle"))
                if (preview) {
                    holdDrag(height * .94f, height * .47f)
                    touch(MotionEvent.ACTION_UP, height * .47f)
                    SystemClock.sleep(900)
                    // Continue down through the list's top into a sheet drag, in one gesture.
                    // Start just below the fixed handle: part of this distance first
                    // returns the scrolled list to its top, then pulls the sheet closed.
                    holdDrag(height * .24f, height * .95f)
                    val handedOff = Rect().also { checkNotNull(find("Player drawer handle")).getBoundsInScreen(it) }
                    assertTrue("List didn't hand enough remaining drag to the sheet: $handedOff", handedOff.top > height * .25f)
                    screenshot("drawer-list-drag-held")
                    touch(MotionEvent.ACTION_UP, height * .95f)
                    SystemClock.sleep(900)
                    screenshot("drawer-list-drag-released")
                    assertNull("List drag didn't settle the sheet closed", find("Player drawer handle"))
                }
                // One raw encoder detent, rather than a pixel-sized synthetic swipe.
                val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                check(maximum >= 2)
                val base = originalVolume.coerceIn(1, maximum - 1)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, base, 0)
                rotary(-1f)
                SystemClock.sleep(250)
                assertEquals("One detent changed multiple volume steps", base + 1, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                screenshot("player-volume-feedback")
                rotary(1f)
                SystemClock.sleep(250)
                assertEquals(base, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                SystemClock.sleep(1_700)
                screenshot("player-feedback-dismissed")
            } finally {
                // A cancel without a live pointer is rejected by InputDispatcher. Cleanup must
                // not replace an earlier assertion with that unrelated injection failure.
                try { if (touching) runCatching { touch(MotionEvent.ACTION_CANCEL, height / 2) } }
                finally { audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0) }
            }
        }
    }
}
