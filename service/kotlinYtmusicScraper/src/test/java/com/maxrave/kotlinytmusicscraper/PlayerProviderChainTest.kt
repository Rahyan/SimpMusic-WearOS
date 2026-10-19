package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.utils.firstPlayableProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlayerProviderChainTest {
    @Test fun directSuccessDoesNotStartSlowFallback() = runBlocking {
        var fallbackCalled = false
        val result = firstPlayableProvider(
            listOf("direct" to { "stream" }, "fallback" to { fallbackCalled = true; "other" }),
            isPlayable = { _, _ -> true },
        )
        assertEquals("direct" to "stream", result)
        assertFalse(fallbackCalled)
    }

    @Test fun brokenExtractorAndRejectedUrlDoNotPreventRecovery() = runBlocking {
        val failures = mutableListOf<String>()
        val result = firstPlayableProvider(
            listOf("broken" to { throw IllegalStateException("extractor failed") },
                "blocked" to { "403" }, "working" to { "200" }),
            isPlayable = { _, response -> response == "200" },
            onFailure = { name, _ -> failures.add(name) },
        )
        assertEquals("working" to "200", result)
        assertEquals(listOf("broken"), failures)
    }

    @Test fun cancellationStopsAllNetworkFallbacks() = runBlocking {
        var fallbackCalled = false
        try {
            firstPlayableProvider(
                listOf("cancelled" to { throw CancellationException("deadline") },
                    "fallback" to { fallbackCalled = true; "stream" }),
                isPlayable = { _, _ -> true },
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(fallbackCalled)
        }
    }
}
