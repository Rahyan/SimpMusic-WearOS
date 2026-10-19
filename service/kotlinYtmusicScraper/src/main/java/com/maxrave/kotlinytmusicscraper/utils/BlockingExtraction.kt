package com.maxrave.kotlinytmusicscraper.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import java.io.InterruptedIOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancellation closes only this extraction's active HTTP call, never another track's request. */
internal object BlockingExtraction {
    private val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8),
        { task -> Thread(task, "youtube-extraction").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private val current = ThreadLocal<Session>()
    private class Session {
        val cancelled = AtomicBoolean(false)
        val call = AtomicReference<Call?>()
        fun cancel() { cancelled.set(true); call.get()?.cancel() }
    }
    fun <T> request(call: Call, block: () -> T): T {
        val session = current.get() ?: return block()
        session.call.set(call)
        try {
            if (session.cancelled.get()) { call.cancel(); throw InterruptedIOException("Extraction cancelled") }
            return block()
        } finally { session.call.compareAndSet(call, null) }
    }
    suspend fun <T : Any> run(timeoutMs: Long = 20_000, block: () -> T): T = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val session = Session()
            val submitted = AtomicReference<java.util.concurrent.Future<*>?>()
            fun cancelSubmitted() {
                session.cancel()
                submitted.get()?.let { future ->
                    future.cancel(true)
                    if (future is Runnable) executor.remove(future)
                }
            }
            continuation.invokeOnCancellation { cancelSubmitted() }
            val future = try { executor.submit {
                current.set(session)
                try {
                    if (session.cancelled.get()) return@submit
                    val result = block()
                    if (continuation.isActive) continuation.resume(result)
                } catch (failure: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                } finally { current.remove() }
            } } catch (_: RejectedExecutionException) {
                continuation.resumeWithException(java.io.IOException("Extraction workers are busy; try another provider"))
                return@suspendCancellableCoroutine
            }
            submitted.set(future)
            if (session.cancelled.get()) cancelSubmitted()
        }
    } ?: throw java.net.SocketTimeoutException("Extraction deadline exceeded")
}
