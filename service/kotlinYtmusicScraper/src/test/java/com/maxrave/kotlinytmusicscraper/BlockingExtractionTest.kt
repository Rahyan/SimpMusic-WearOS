package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.utils.BlockingExtraction
import com.maxrave.kotlinytmusicscraper.utils.firstPlayableProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BlockingExtractionTest {
    @Test fun deadlineStopsBlockingWorkAndAllowsFallback() = runBlocking {
        val interrupted = CountDownLatch(1)
        val result = firstPlayableProvider(listOf(
            "stalled" to { BlockingExtraction.run(150) {
                try { Thread.sleep(5_000); "bad" } finally { interrupted.countDown() }
            } },
            "fallback" to { "audio" }
        ), { _, _ -> true })
        assertEquals("fallback" to "audio", result)
        assertTrue(interrupted.await(2, TimeUnit.SECONDS))
    }
    @Test fun successfulExtractionReturnsNormally() = runBlocking {
        assertEquals("audio", BlockingExtraction.run { "audio" })
    }
    @Test fun parentCancellationInterruptsItsWorker() = runBlocking {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val task = launch(kotlinx.coroutines.Dispatchers.Default) {
            BlockingExtraction.run { started.countDown(); try { Thread.sleep(10_000); "late" } finally { stopped.countDown() } }
        }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        task.cancelAndJoin()
        assertTrue(stopped.await(2, TimeUnit.SECONDS))
        assertEquals("next", BlockingExtraction.run { "next" })
    }
}
