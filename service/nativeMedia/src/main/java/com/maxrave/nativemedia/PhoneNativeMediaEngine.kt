package com.maxrave.nativemedia

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.maxrave.kotlinytmusicscraper.NativeMediaEngine
import com.maxrave.kotlinytmusicscraper.NativeMediaTools
import com.maxrave.kotlinytmusicscraper.NativeTranscodeResult
import com.maxrave.kotlinytmusicscraper.models.ytdlp.YtdlpVideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.serialization.json.Json

/** Registered only by the full phone app; native runtimes initialize on first use. */
class PhoneNativeMediaEngine private constructor(context: Context) : NativeMediaEngine {
    private val appContext = context.applicationContext
    private val extractor by lazy { YoutubeDL.getInstance().also { it.init(appContext) } }

    override fun update() { extractor.updateYoutubeDL(appContext) }

    override fun extract(videoId: String, cookiePath: String?, client: String, poToken: String?, json: Json): YtdlpVideoInfo? {
        val request = YoutubeDLRequest("https://music.youtube.com/watch?v=$videoId")
        if (!cookiePath.isNullOrBlank()) request.addOption("--cookies", cookiePath)
        request.addOption("--extractor-args", "youtube:player_client=$client;youtube:webpage_skip" +
            if (client.contains("web") && poToken != null) ";youtube:po_token=$client.gvs+$poToken;" else "")
        request.addOption("--dump-json")
        return json.decodeFromString<YtdlpVideoInfo>(extractor.execute(request).out)
    }

    override fun transcode(arguments: String): NativeTranscodeResult {
        val session = FFmpegKit.execute(arguments)
        return NativeTranscodeResult(session.returnCode?.value, session.failStackTrace.orEmpty())
    }

    companion object {
        fun install(context: Context) { NativeMediaTools.engine = PhoneNativeMediaEngine(context) }
    }
}
