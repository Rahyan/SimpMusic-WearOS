package com.maxrave.logger

import java.io.File

/** Bounded private diagnostics. Caller supplies a background executor. */
internal class RollingLogFile(private val directory: File, private val maxBytes: Long = 262_144) {
    @Synchronized fun append(line: String) {
        directory.mkdirs()
        val active = File(directory, "app.log")
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        if (active.length() + bytes.size > maxBytes) {
            File(directory, "app-2.log").delete()
            File(directory, "app-1.log").renameTo(File(directory, "app-2.log"))
            active.renameTo(File(directory, "app-1.log"))
        }
        active.appendBytes(bytes)
    }
}
