package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.utils.downloadFile
import com.maxrave.kotlinytmusicscraper.utils.isFailedTransfer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileDownloadTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun completeFileIsTheOnlySuccessfulTerminalResult(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        HttpClient(MockEngine { respond("music", headers = headersOf(HttpHeaders.ContentLength, "5")) }).use { client ->
            val result = downloadFile(client, "https://example.test/audio", output.path).toList()
            assertEquals(Triple(true, 1f, 0), result.last())
            assertEquals("music", output.readText())
        }
    }

    @Test fun truncatedTransferFailsAndRemovesPartialFile(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        HttpClient(MockEngine { respond("part", headers = headersOf(HttpHeaders.ContentLength, "9")) }).use { client ->
            val result = downloadFile(client, "https://example.test/audio", output.path).toList()
            assertTrue(result.last().isFailedTransfer())
            assertFalse(output.exists())
        }
    }

    @Test fun formatLengthDetectsACompleteButCappedHttpResponse(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        HttpClient(MockEngine { respond("part", headers = headersOf(HttpHeaders.ContentLength, "4")) }).use { client ->
            val result = downloadFile(client, "https://example.test/audio", output.path, expectedLength = 40).toList()
            assertTrue(result.last().isFailedTransfer())
            assertFalse(output.exists())
        }
    }

    @Test fun forbiddenResponseCannotProduceADownloadedFile(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        HttpClient(MockEngine { respond("Forbidden", HttpStatusCode.Forbidden) }).use { client ->
            assertTrue(downloadFile(client, "https://example.test/audio", output.path).toList().last().isFailedTransfer())
            assertFalse(output.exists())
        }
    }

    @Test fun aPartialRangeIsNotACompleteFile(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        HttpClient(MockEngine { respond("part", HttpStatusCode.PartialContent,
            headersOf(HttpHeaders.ContentRange, "bytes 0-3/40")) }).use { client ->
            assertTrue(downloadFile(client, "https://example.test/audio", output.path).toList().last().isFailedTransfer())
            assertFalse(output.exists())
        }
    }

    @Test fun aRangeCoveringTheWholeFileIsAccepted(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        HttpClient(MockEngine { respond("music", HttpStatusCode.PartialContent,
            headersOf(HttpHeaders.ContentRange, "bytes 0-4/5")) }).use { client ->
            assertEquals(Triple(true, 1f, 0), downloadFile(client, "https://example.test/audio", output.path).toList().last())
            assertEquals("music", output.readText())
        }
    }

    @Test fun formatLengthDoesNotHideATruncatedHttpBody(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        HttpClient(MockEngine { respond("part", headers = headersOf(HttpHeaders.ContentLength, "9")) }).use { client ->
            assertTrue(downloadFile(client, "https://example.test/audio", output.path, expectedLength = 4).toList().last().isFailedTransfer())
            assertFalse(output.exists())
        }
    }

    @Test fun cancellationRemainsCancellationAndRemovesPartialFile(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        val body = ByteChannel(autoFlush = true).apply { writeFully("part".toByteArray()) }
        HttpClient(MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentLength, "100")) }).use { client ->
            val task = launch { downloadFile(client, "https://example.test/audio", output.path).toList() }
            try {
                withTimeout(3_000) { while (folder.root.list()!!.none { it.contains(".part-") }) delay(10) }
                task.cancelAndJoin()
                assertTrue(task.isCancelled)
                assertFalse(output.exists())
                assertTrue(folder.root.list()!!.isEmpty())
            } finally {
                task.cancelAndJoin()
                body.cancel(kotlinx.coroutines.CancellationException("Test finished"))
            }
        }
    }

    @Test fun finishedDoesNotMeanSuccessful() {
        assertTrue(Triple(true, 0f, 0).isFailedTransfer())
        assertTrue(Triple(true, Float.NaN, 0).isFailedTransfer())
        assertFalse(Triple(false, .5f, 0).isFailedTransfer())
        assertFalse(Triple(true, 1f, 0).isFailedTransfer())
    }

    @Test fun failedReplacementKeepsThePreviousFile(): Unit = runBlocking {
        val output = folder.newFile().apply { writeText("previous download") }
        HttpClient(MockEngine { respond("Forbidden", HttpStatusCode.Forbidden) }).use { client ->
            assertTrue(downloadFile(client, "https://example.test/audio", output.path).toList().last().isFailedTransfer())
            assertEquals("previous download", output.readText())
            assertEquals(listOf(output.name), folder.root.list()!!.toList())
        }
    }

    @Test fun completionWaitsForABusyProgressConsumer(): Unit = runBlocking {
        val output = File(folder.root, "audio")
        val body = ByteChannel(autoFlush = true)
        val payload = ByteArray(16384) { 'm'.code.toByte() }
        val requestStarted = CompletableDeferred<Unit>()
        val progressSeen = CompletableDeferred<Unit>()
        val chunksWritten = java.util.concurrent.atomic.AtomicInteger()
        val writer = launch {
            requestStarted.await()
            body.writeFully(payload.copyOfRange(0, 8192))
            chunksWritten.incrementAndGet()
            delay(150) // The response is open while the second chunk arrives.
            body.writeFully(payload.copyOfRange(8192, payload.size))
            chunksWritten.incrementAndGet()
            progressSeen.await()
            body.close()
        }
        try {
            HttpClient(MockEngine { requestStarted.complete(Unit); respond(body) }).use { client ->
                val result = mutableListOf<Triple<Boolean, Float, Int>>()
                try { withTimeout(5_000) {
                    downloadFile(client, "https://example.test/audio", output.path).buffer(0).collect { update ->
                        result += update
                        if (!update.first) {
                            progressSeen.complete(Unit)
                            // The producer finishes the file while this consumer is still
                            // processing progress. Its terminal result must not be dropped.
                            delay(500)
                        }
                    }
                } } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                    throw AssertionError("Request=${requestStarted.isCompleted}, chunks=${chunksWritten.get()}, progress=${progressSeen.isCompleted}, updates=$result", error)
                }
                assertEquals(Triple(true, 1f, 0), result.last())
                assertArrayEquals(payload, output.readBytes())
            }
        } finally {
            writer.cancelAndJoin()
            body.cancel(kotlinx.coroutines.CancellationException("Test finished"))
        }
    }
}
