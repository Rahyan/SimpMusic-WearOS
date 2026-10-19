package com.maxrave.simpmusic.wear

import com.maxrave.simpmusic.wear.ui.components.RotaryVolumeSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryVolumeStepsTest {
    @Test fun detentsAreIndependentOfDevicePixelScaling() {
        for (factor in listOf(48f, 80f, 120f)) {
            val ticks = RotaryVolumeSteps(factor)
            assertEquals(1, ticks.consume(factor, 1_000))
            assertEquals(-1, ticks.consume(-factor, 1_100))
            assertEquals(3, ticks.consume(factor * 3, 1_200))
        }
    }
    @Test fun splitEventsAccumulateOneDetent() {
        val ticks = RotaryVolumeSteps(80f)
        assertEquals(0, ticks.consume(20f, 1_000))
        assertEquals(0, ticks.consume(20f, 1_050))
        assertEquals(0, ticks.consume(20f, 1_100))
        assertEquals(1, ticks.consume(20f, 1_150))
    }
    @Test fun OldOrReversedPartialTurnsDoNotCauseExtraSteps() {
        val ticks = RotaryVolumeSteps(80f)
        assertEquals(0, ticks.consume(60f, 1_000))
        assertEquals(0, ticks.consume(40f, 2_000))
        assertEquals(0, ticks.consume(-40f, 2_050))
        assertEquals(-1, ticks.consume(-40f, 2_100))
        assertEquals(0, ticks.consume(Float.NaN, 2_150))
    }
}
