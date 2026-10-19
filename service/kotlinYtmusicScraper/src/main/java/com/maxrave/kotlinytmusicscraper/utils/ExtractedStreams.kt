package com.maxrave.kotlinytmusicscraper.utils

import com.maxrave.kotlinytmusicscraper.models.response.PlayerResponse

/** Independent extraction must not depend on formats from an unsuccessful InnerTube response. */
internal fun responseWithExtractedStreams(
    seed: PlayerResponse,
    formats: List<PlayerResponse.StreamingData.Format>,
): PlayerResponse? {
    val usable = formats.filter { !it.url.isNullOrBlank() }
    if (usable.isEmpty()) return null
    return seed.copy(
        playabilityStatus = PlayerResponse.PlayabilityStatus("OK", null),
        streamingData = PlayerResponse.StreamingData(
            formats = null,
            adaptiveFormats = usable,
            expiresInSeconds = seed.streamingData?.expiresInSeconds ?: 0,
        ),
    )
}
