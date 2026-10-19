package com.maxrave.simpmusic.wear.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/** Optional network content may complete without an emission; it must not crash the player UI. */
internal suspend fun <T> Flow<T>.optionalValue(): T? = try {
    withTimeoutOrNull(8_000L) { firstOrNull() }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
