package com.maxrave.simpmusic.wear.ui.components

/** Undo Compose's device scroll scaling to recover detents, including split/batched events. */
internal class RotaryVolumeSteps(private val pixelsPerTick: Float) {
    private var remainder = 0f
    private var lastTime = Long.MIN_VALUE

    fun consume(pixels: Float, uptime: Long): Int {
        if (!pixels.isFinite() || !pixelsPerTick.isFinite() || pixelsPerTick <= 0f) return 0
        if (lastTime == Long.MIN_VALUE || uptime - lastTime > 300 || remainder * pixels < 0f) remainder = 0f
        lastTime = uptime
        remainder += pixels / pixelsPerTick
        val steps = remainder.toInt()
        remainder -= steps
        return steps
    }
}
