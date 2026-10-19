package com.maxrave.domain.mediaservice.handler

/** Adapted from upstream core b9f4cea; curated RDCLAK playlists have a finite end. */
fun String.isEndlessQueueId(): Boolean = removePrefix("VL").let { it.startsWith("RD") && !it.startsWith("RDCLAK") }

enum class AddToQueueBlock(val message: String) {
    RADIO("Radio keeps extending · Use Play next"),
    ENDLESS_QUEUE("Endless queue is on · Use Play next or turn it off"),
}

fun QueueData?.addToQueueBlock(endlessQueue: Boolean): AddToQueueBlock? = when {
    this?.data?.playlistId == "wear_offline_queue" -> null
    this?.data?.playlistId?.isEndlessQueueId() == true -> AddToQueueBlock.RADIO
    endlessQueue -> AddToQueueBlock.ENDLESS_QUEUE
    else -> null
}

object RadioQueueTrim {
    const val KEEP_HISTORY = 100
    const val TRIM_ABOVE_HISTORY = 120
    fun appliesTo(type: PlaylistType?, id: String?) = type == PlaylistType.RADIO && id?.isEndlessQueueId() == true
    fun countToDropFromFront(currentIndex: Int, queueSize: Int): Int =
        if (currentIndex <= TRIM_ABOVE_HISTORY || currentIndex >= queueSize || queueSize <= KEEP_HISTORY) 0
        else currentIndex - KEEP_HISTORY

    fun <T> afterFrontRemoved(queue: List<T>, removedIds: List<String>, idOf: (T) -> String): List<T>? {
        if (removedIds.size > queue.size || removedIds.indices.any { idOf(queue[it]) != removedIds[it] }) return null
        return queue.drop(removedIds.size)
    }
}
