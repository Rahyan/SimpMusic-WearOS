package com.maxrave.kotlinytmusicscraper.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val api_key: String,
    val userAgent: String,
    val platform: String? = null,
    val osVersion: String? = null,
    val referer: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val timeZone: String? = null,
    val utcOffsetMinutes: Int? = null,
    val xClientName: Int? = null,
) {
    fun toContext(
        locale: YouTubeLocale,
        visitorData: String?,
    ) = Context(
        client =
            Context.Client(
                clientName = clientName,
                clientVersion = clientVersion,
                platform = platform,
                osVersion = osVersion,
                gl = if (clientName == "ANDROID_VR") "US" else locale.gl,
                hl = if (clientName == "ANDROID_VR") "en" else locale.hl,
                visitorData = visitorData,
                userAgent = userAgent,
                deviceMake = deviceMake,
                deviceModel = deviceModel,
                osName = osName,
                timeZone = timeZone,
                utcOffsetMinutes = utcOffsetMinutes,
            ),
    )

    companion object {
        private const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"

        const val USER_AGENT_WEB =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private const val USER_AGENT_ANDROID =
            "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
        private const val USER_AGENT_IOS = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
        private const val USER_AGENT_MWEB =
            "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)"
        private const val USER_AGENT_ANDROID_VR =
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; en_US; Quest 3) gzip"

        val ANDROID_MUSIC =
            YouTubeClient(
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.27.52",
                api_key = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
                userAgent = USER_AGENT_ANDROID,
                osName = "Android",
                osVersion = "11",
                xClientName = 21,
            )

        val ANDROID =
            YouTubeClient(
                clientName = "ANDROID",
                clientVersion = "17.13.3",
                api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
                userAgent = USER_AGENT_ANDROID,
            )

        val ANDROID_VR =
            YouTubeClient(
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
                userAgent = USER_AGENT_ANDROID_VR,
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12L",
                platform = "MOBILE",
                utcOffsetMinutes = 0,
                xClientName = 28,
            )

        // yt-dlp's current JS-less default (2026-10). Keep payload and UA in sync.
        val VISIONOS =
            YouTubeClient(
                clientName = "VISIONOS",
                clientVersion = "1.02",
                api_key = "",
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                deviceMake = "Apple",
                deviceModel = "RealityDevice17,1",
                osName = "visionOS",
                osVersion = "26.5.23O471",
                xClientName = 101,
            )

        val WEB =
            YouTubeClient(
                clientName = "WEB",
                clientVersion = "2.20250312.04.00",
                api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
                userAgent = USER_AGENT_WEB,
            )

        val WEB_REMIX =
            YouTubeClient(
                clientName = "WEB_REMIX",
                clientVersion = "1.20260707.12.00",
                api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
                userAgent = USER_AGENT_WEB,
                referer = REFERER_YOUTUBE_MUSIC,
                xClientName = 67,
            )

        val WEB_EMBEDDED =
            YouTubeClient(
                clientName = "WEB_EMBEDDED_PLAYER",
                clientVersion = "2.20260708.00.00",
                api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)",
                referer = "https://www.youtube.com/",
                xClientName = 56,
            )

        val TVHTML5_SIMPLY =
            YouTubeClient(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
                userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
            )

        val TVHTML5 =
            YouTubeClient(
                clientName = "TVHTML5",
                clientVersion = "7.20260707.07.00",
                api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
                userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
                xClientName = 7,
            )

        val IOS =
            YouTubeClient(
                clientName = "IOS",
                clientVersion = "19.45.4",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                userAgent = USER_AGENT_IOS,
                api_key = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
                osName = "iPhone",
                osVersion = "17.5.1.21F90",
                timeZone = "UTC",
                utcOffsetMinutes = 0,
                xClientName = 5,
            )

        val MWEB =
            YouTubeClient(
                clientName = "MWEB",
                clientVersion = "2.20241202.07.00",
                api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
                userAgent = USER_AGENT_MWEB,
                timeZone = "UTC",
                utcOffsetMinutes = 0,
                xClientName = 2,
            )
    }
}
