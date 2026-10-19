package com.maxrave.logger

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Logger {
    init {
        Napier.base(DebugAntilog())
    }

    private val logger = Napier
    @Volatile private var file: RollingLogFile? = null
    @Volatile private var debugEnabled = true
    private val writer by lazy {
        ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, ArrayBlockingQueue(128),
            { task -> Thread(task, "diagnostic-log").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardPolicy())
            .apply { allowCoreThreadTimeOut(true) }
    }
    fun configure(directory: File, debug: Boolean) { file = RollingLogFile(directory); debugEnabled = debug }
    private fun record(level: String, tag: String, message: String) {
        val target = file ?: return
        writer.execute { runCatching { target.append("${System.currentTimeMillis()} $level/${tag.take(80)}: $message") } }
    }

    fun d(
        tag: String,
        message: String,
    ) {
        if (debugEnabled) logger.d(tag = tag, message = LogSanitizer.clean(message))
    }

    fun i(
        tag: String,
        message: String,
    ) {
        val safe = LogSanitizer.clean(message)
        logger.i(tag = tag, message = safe); record("I", tag, safe)
    }

    fun w(
        tag: String,
        message: String,
    ) {
        val safe = LogSanitizer.clean(message)
        logger.w(tag = tag, message = safe); record("W", tag, safe)
    }

    fun e(
        tag: String,
        message: String,
        e: Throwable? = null,
    ) {
        val safe = LogSanitizer.clean(message + (e?.let { " (${it.javaClass.simpleName}: ${it.message.orEmpty()})" } ?: ""))
        val safeError = e?.let { RuntimeException(LogSanitizer.clean(it.message.orEmpty())).apply { stackTrace = it.stackTrace } }
        logger.e(tag = tag, message = safe, throwable = safeError); record("E", tag, safe)
    }
}
