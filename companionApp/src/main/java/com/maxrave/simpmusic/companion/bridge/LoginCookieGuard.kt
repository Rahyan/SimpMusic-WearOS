package com.maxrave.simpmusic.companion.bridge

import java.net.URI

/** Anonymous music pages also set cookies; accept only the exact HTTPS music origin and an auth cookie. */
internal fun hasMusicLoginCookie(url: String, cookie: String?): Boolean {
    val origin = runCatching { URI(url) }.getOrNull() ?: return false
    if (origin.scheme != "https" || origin.host != "music.youtube.com") return false
    return cookie.orEmpty().split(';').any { part ->
        val pair = part.trim().split('=', limit = 2)
        pair.size == 2 && pair[0] in setOf("SAPISID", "__Secure-3PAPISID") && pair[1].isNotBlank()
    }
}
