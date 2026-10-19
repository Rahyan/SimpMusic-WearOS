package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.models.ytdlp.YtdlpVideoInfo
import kotlinx.serialization.json.Json

/** Optional phone extraction/export engines. Ordinary streaming and Media3 downloads do not need these. */
interface NativeMediaEngine {
    fun update()
    fun extract(videoId: String, cookiePath: String?, client: String, poToken: String?, json: Json): YtdlpVideoInfo?
    fun transcode(arguments: String): NativeTranscodeResult
}

data class NativeTranscodeResult(val returnCode: Int?, val failStackTrace: String = "") {
    val state: String get() = when (returnCode) { 0 -> "completed"; 255 -> "cancelled"; else -> "failed" }
}

object NativeMediaTools {
    @Volatile var engine: NativeMediaEngine? = null
    fun transcode(arguments: String): NativeTranscodeResult =
        engine?.transcode(arguments) ?: NativeTranscodeResult(null, "File export is available on the phone.")
    fun isSuccess(code: Int?): Boolean = code == 0
    fun isCancel(code: Int?): Boolean = code == 255
}
