package com.maxrave.simpmusic.wear.auth

/** The relay supplies a Cookie header, not a watch WebView database. Use session expiry. */
internal fun relayCookiesToNetscape(header: String): String = buildString {
    append("# Netscape HTTP Cookie File\n# SimpMusic phone relay\n\n")
    header.split(';').forEach { entry ->
        val pair = entry.trim().split('=', limit = 2)
        if (pair.size == 2 && pair[0].isNotBlank() &&
            pair.none { it.contains('\t') || it.contains('\n') || it.contains('\r') }) {
            append(".youtube.com\tTRUE\t/\tTRUE\t0\t")
            append(pair[0]).append('\t').append(pair[1]).append('\n')
        }
    }
}
