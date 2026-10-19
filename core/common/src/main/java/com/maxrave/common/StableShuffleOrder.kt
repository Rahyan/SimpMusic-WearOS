package com.maxrave.common

/** Preserve the already-played prefix and existing order when a radio/queue appends a song. */
fun insertShuffledTrack(order: List<Int>, index: Int, current: Int, pinned: Set<Int>, random: kotlin.random.Random, playNext: Boolean = false): List<Int> {
    require(order.sorted() == order.indices.toList())
    require(index in 0..order.size)
    val currentPosition = order.indexOf(current)
    var firstFree = currentPosition + 1
    if (!playNext || current < 0) {
        while (firstFree < order.size && order[firstFree] in pinned) firstFree++
    }
    val insertion = if (current >= 0 && playNext) currentPosition + 1
        else random.nextInt(firstFree, order.size + 1)
    return order.map { if (it >= index) it + 1 else it }.toMutableList().apply { add(insertion, index) }
}

fun movedPlaylistIndex(index: Int, from: Int, to: Int): Int = when {
    index == from -> to
    from < to && index in (from + 1)..to -> index - 1
    from > to && index in to until from -> index + 1
    else -> index
}

/** Move one selected entry beside its target without reshuffling the remaining entries. */
fun moveShuffledTrack(order: List<Int>, from: Int, to: Int): List<Int> {
    require(from in order.indices && to in order.indices)
    if (from == to) return order
    val result = order.toMutableList().apply { remove(from) }
    val slot = result.indexOf(to) + if (from < to) 1 else 0
    result.add(slot, from)
    return result.map { movedPlaylistIndex(it, from, to) }
}
