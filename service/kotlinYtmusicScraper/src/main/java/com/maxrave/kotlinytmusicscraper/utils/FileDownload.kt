package com.maxrave.kotlinytmusicscraper.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.readByteArray
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.io.IOException
import java.util.UUID

/** The terminal flag means finished; only terminal progress 1 means a complete file. */
internal fun Triple<Boolean, Float, Int>.isFailedTransfer(): Boolean = first && !(second >= 1f && second.isFinite())

internal fun downloadFile(client: HttpClient, url: String, filename: String, expectedLength: Long? = null,
    onFailure: (Exception) -> Unit = {}): Flow<Triple<Boolean, Float, Int>> =
    channelFlow {
        val path = filename.toPath()
        val temporary = (filename + ".part-" + UUID.randomUUID()).toPath()
        var complete = false
        try {
            client.prepareGet(url).execute { response ->
                if (response.status.value !in 200..299) throw IOException("Download HTTP ${response.status.value}")
                val formatLength = expectedLength?.takeIf { it > 0 }
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0 }
                // This request asks for the entire file. A successful partial response is
                // only complete if its declared byte range covers that entire file.
                val rangeLength = if (response.status.value == 206) {
                    val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(
                        response.headers[HttpHeaders.ContentRange]?.trim().orEmpty())
                    val first = range?.groupValues?.get(1)?.toLongOrNull()
                    val last = range?.groupValues?.get(2)?.toLongOrNull()
                    val total = range?.groupValues?.get(3)?.toLongOrNull()
                    if (first != 0L || total == null || total <= 0 || last != total - 1)
                        throw IOException("Incomplete byte range")
                    total
                } else null
                val knownLengths = listOfNotNull(formatLength, contentLength, rangeLength)
                val length = knownLengths.firstOrNull() ?: 0L
                val start = System.nanoTime()
                var lastUpdate = start
                var downloaded = 0L
                val channel = response.bodyAsChannel()
                FileSystem.SYSTEM.sink(temporary).buffer().use { sink ->
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(8192)
                        while (!packet.exhausted()) {
                            val bytes = packet.readByteArray()
                            sink.write(bytes)
                            downloaded += bytes.size
                        }
                        val now = System.nanoTime()
                        if (now - lastUpdate >= 100_000_000) {
                            val progress = if (length > 0) (downloaded.toFloat() / length).coerceIn(0f, 1f) else 0f
                            val seconds = ((now - start) / 1_000_000_000.0).coerceAtLeast(.001)
                            trySend(Triple(false, progress, (downloaded / seconds / 1024).toInt()))
                            lastUpdate = now
                        }
                    }
                }
                if (downloaded == 0L || knownLengths.any { downloaded != it }) throw IOException("Incomplete download")
            }
            FileSystem.SYSTEM.atomicMove(temporary, path)
            complete = true
            send(Triple(true, 1f, 0))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(error)
            send(Triple(true, 0f, 0))
        } finally {
            if (!complete) runCatching { FileSystem.SYSTEM.delete(temporary, mustExist = false) }
        }
    }.flowOn(Dispatchers.IO)
