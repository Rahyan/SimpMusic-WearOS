package com.maxrave.logger

object LogSanitizer {
    private val secrets = Regex("(?i)cookie|sapisid|authorization|visitor.?data|po.?token|spdc|password|secret|playback.?tracking|access.?token|refresh.?token|signatureCipher")
    private val urls = Regex("https?://[^\\s\\\"<>]+")
    fun clean(message: String): String {
        if (secrets.containsMatchIn(message) || message.count { it == '{' || it == '[' } > 5) return "[sensitive or structured details omitted]"
        return urls.replace(message) { match ->
            runCatching { "${java.net.URI(match.value).host ?: "remote"}/[url omitted]" }.getOrDefault("[url omitted]")
        }.replace('\n', ' ').replace('\r', ' ').take(2048)
    }
}
