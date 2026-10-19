package com.maxrave.simpmusic.wear.wearable

import android.content.Context
import com.maxrave.common.bridgeDigest

internal class WatchLocalMembership(context: Context) {
    private val prefs = context.getSharedPreferences("watch_bridge_ownership", Context.MODE_PRIVATE)
    private fun key(category: String, id: String) = "local_${category}_${bridgeDigest(id)}"
    fun intent(category: String, id: String): Boolean? =
        key(category, id).let { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
    @Synchronized fun record(category: String, id: String, member: Boolean) {
        val setKey = "${category}_local_likes"
        val likes = prefs.getStringSet(setKey, emptySet()).orEmpty().toMutableSet()
        if (member) likes.add(id) else likes.remove(id)
        prefs.edit().putBoolean(key(category, id), member).putStringSet(setKey, likes).commit()
    }
    fun liked(category: String): Set<String> = prefs.getStringSet("${category}_local_likes", emptySet()).orEmpty()
}
