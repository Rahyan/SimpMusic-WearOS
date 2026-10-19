package com.maxrave.simpmusic.wear.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OptionalFlowValueTest {
    @Test fun emptyOrFailedProviderAllowsFallbackInsteadOfCrashingPlayer() = runBlocking {
        assertNull(emptyFlow<String>().optionalValue())
        assertNull(flow<String> { throw IllegalStateException("network failure") }.optionalValue())
        assertEquals("lyrics", flowOf("lyrics").optionalValue())
    }
    @Test fun switchingTracksCancelsTheOldLyricsRequest() = runBlocking {
        try {
            flow<String> { throw CancellationException("track changed") }.optionalValue()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
