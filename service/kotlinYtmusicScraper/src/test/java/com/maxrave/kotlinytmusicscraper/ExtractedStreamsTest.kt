package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.models.ResponseContext
import com.maxrave.kotlinytmusicscraper.models.response.PlayerResponse
import com.maxrave.kotlinytmusicscraper.utils.responseWithExtractedStreams
import org.junit.Assert.*
import org.junit.Test

class ExtractedStreamsTest {
    private val unavailable = PlayerResponse(ResponseContext(serviceTrackingParams = null),
        PlayerResponse.PlayabilityStatus("ERROR", "This video is unavailable"),
        playerConfig = null, streamingData = null, videoDetails = null, playbackTracking = null, captions = null)
    private fun audio(url: String?) = PlayerResponse.StreamingData.Format(
        itag = 251, url = url, mimeType = "audio/webm", bitrate = 128000,
        width = null, height = null, contentLength = 4000000, quality = "tiny", fps = null,
        qualityLabel = null, averageBitrate = 128000, audioQuality = "AUDIO_QUALITY_MEDIUM",
        approxDurationMs = "210000", audioSampleRate = 48000, audioChannels = 2,
        loudnessDb = null, lastModified = null, signatureCipher = null)

    @Test fun independentExtractionRecoversWhenPlayerHasNoFormats() {
        val result = responseWithExtractedStreams(unavailable, listOf(audio("https://example.test/audio")))!!
        assertEquals("OK", result.playabilityStatus.status)
        assertEquals(251, result.streamingData!!.adaptiveFormats.single().itag)
        assertTrue(result.streamingData.adaptiveFormats.single().isAudio)
        assertEquals(4000000L, result.streamingData.adaptiveFormats.single().contentLength)
    }
    @Test fun emptyOrMissingUrlsCannotTurnFailureIntoSuccess() {
        assertNull(responseWithExtractedStreams(unavailable, emptyList()))
        assertNull(responseWithExtractedStreams(unavailable, listOf(audio(null), audio(""))))
    }
}
