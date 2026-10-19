package com.maxrave.kotlinytmusicscraper.utils

/** Use the supplied request cookie, including when it differs from the active account. */
internal fun sapisidFromCookieHeader(cookie: String?): String? {
    val values = cookie.orEmpty().split(';').mapNotNull { part ->
        val pair = part.trim().split('=', limit = 2)
        if (pair.size == 2 && pair[1].isNotBlank()) pair[0] to pair[1] else null
    }.toMap()
    return values["SAPISID"] ?: values["__Secure-3PAPISID"]
}
