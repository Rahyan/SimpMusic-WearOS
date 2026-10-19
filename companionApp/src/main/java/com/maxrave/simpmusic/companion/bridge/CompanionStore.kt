package com.maxrave.simpmusic.companion.bridge

import android.content.Context
import com.maxrave.common.WearCompanionBridge
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object CompanionStore {
    private const val PREFS_NAME = "simpmusic_companion_store"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_COOKIE_SAVED_AT = "cookie_saved_at"
    private const val KEY_RELOGIN_REASON = "relogin_reason"
    private const val KEY_LOG = "companion_log"
    private const val MAX_LOG_CHARS = 16000
    private const val SESSION_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L

    data class SessionState(
        val hasCookie: Boolean,
        val isExpired: Boolean,
        val requiresRelogin: Boolean,
        val reloginReason: String,
    )

    fun saveCookie(
        context: Context,
        cookie: String,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COOKIE, cookie)
            .putLong(KEY_COOKIE_SAVED_AT, System.currentTimeMillis())
            .remove(KEY_RELOGIN_REASON)
            .apply()
    }

    fun getCookie(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, "")
            .orEmpty()

    fun clearCookie(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_COOKIE)
            .remove(KEY_COOKIE_SAVED_AT)
            .remove(KEY_RELOGIN_REASON)
            .apply()
    }

    fun markReloginRequired(
        context: Context,
        reason: String,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RELOGIN_REASON, reason.trim().ifBlank { "Session expired. Please sign in again." })
            .apply()
    }

    fun getSessionState(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): SessionState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cookie = prefs.getString(KEY_COOKIE, "").orEmpty()
        val hasCookie = cookie.isNotBlank()
        val savedAt = prefs.getLong(KEY_COOKIE_SAVED_AT, 0L)
        val isExpired = hasCookie && savedAt > 0L && (nowMillis - savedAt) > SESSION_MAX_AGE_MS
        val reloginReason = prefs.getString(KEY_RELOGIN_REASON, "").orEmpty()
        return SessionState(
            hasCookie = hasCookie,
            isExpired = isExpired,
            requiresRelogin = reloginReason.isNotBlank(),
            reloginReason = reloginReason,
        )
    }

    fun savePlayback(context: Context, node: String, data: org.json.JSONObject) {
        val snapshot = com.maxrave.common.decodeWatchPlayback(data) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = com.maxrave.common.nodeSyncKey("playback", node)
        val previous = prefs.getString(key, null)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
            ?.let { com.maxrave.common.decodeWatchPlayback(it) }
        if (previous == null || snapshot.generatedAt >= previous.generatedAt) prefs.edit().putString(key, data.toString()).apply()
    }

    fun getPlayback(context: Context, node: String): com.maxrave.common.WatchPlaybackSnapshot? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(com.maxrave.common.nodeSyncKey("playback", node), null)
            ?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
            ?.let { com.maxrave.common.decodeWatchPlayback(it) }

    fun saveLastResponse(
        context: Context,
        payload: String,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(WearCompanionBridge.KEY_LAST_RESPONSE, payload)
            .apply()
    }

    fun saveLastDiagnostics(
        context: Context,
        payload: String,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(WearCompanionBridge.KEY_LAST_DIAGNOSTICS, payload)
            .apply()
    }

    fun getLastResponse(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(WearCompanionBridge.KEY_LAST_RESPONSE, "")
            .orEmpty()

    fun getLastDiagnostics(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(WearCompanionBridge.KEY_LAST_DIAGNOSTICS, "")
            .orEmpty()

    fun appendLog(
        context: Context,
        message: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_LOG, "").orEmpty()
        val timestamp = LocalTime.now().truncatedTo(ChronoUnit.SECONDS)
        val updated =
            if (current.isBlank()) {
                "[$timestamp] $message"
            } else {
                "$current\n[$timestamp] $message"
            }
        prefs.edit()
            .putString(KEY_LOG, updated.takeLast(MAX_LOG_CHARS))
            .apply()
    }

    fun getLog(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOG, "")
            .orEmpty()

    fun clearLog(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOG, "")
            .apply()
    }
}
