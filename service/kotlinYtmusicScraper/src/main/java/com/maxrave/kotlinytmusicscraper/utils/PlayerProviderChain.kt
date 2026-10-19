package com.maxrave.kotlinytmusicscraper.utils

import kotlinx.coroutines.CancellationException

/** Extract and validate one provider at a time; expensive fallbacks run only when needed. */
internal suspend fun <T : Any> firstPlayableProvider(
    providers: List<Pair<String, suspend () -> T?>>,
    isPlayable: suspend (String, T) -> Boolean,
    onFailure: (String, Exception) -> Unit = { _, _ -> },
): Pair<String, T>? {
    for ((name, extract) in providers) {
        try {
            val response = extract() ?: continue
            if (isPlayable(name, response)) return name to response
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(name, error)
        }
    }
    return null
}
