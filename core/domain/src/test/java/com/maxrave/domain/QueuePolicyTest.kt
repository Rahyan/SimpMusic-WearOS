package com.maxrave.domain

import com.maxrave.domain.mediaservice.handler.*
import org.junit.Assert.*
import org.junit.Test

class QueuePolicyTest {
    private fun queue(id: String?) = QueueData(data = QueueData.Data(playlistId = id, playlistType = PlaylistType.PLAYLIST))
    @Test fun endlessIdsAndReasons() {
        for (id in listOf("RDAMVMabc", "VLRDTMAKabc", "RDAMPLPLabc")) {
            assertEquals(AddToQueueBlock.RADIO, queue(id).addToQueueBlock(false))
            assertEquals(AddToQueueBlock.RADIO, queue(id).addToQueueBlock(true))
        }
        for (id in listOf("RDCLAKabc", "VLRDCLAKabc", "PLabc", "favorites")) assertNull(queue(id).addToQueueBlock(false))
        assertEquals(AddToQueueBlock.ENDLESS_QUEUE, queue("PLabc").addToQueueBlock(true))
        assertEquals(AddToQueueBlock.ENDLESS_QUEUE, (null as QueueData?).addToQueueBlock(true))
        assertNull(queue("wear_offline_queue").addToQueueBlock(true))
    }
    @Test fun trimmingKeepsCurrentAndHistory() {
        assertEquals(0, RadioQueueTrim.countToDropFromFront(120, 171))
        assertEquals(47, RadioQueueTrim.countToDropFromFront(147, 200))
        assertEquals(0, RadioQueueTrim.countToDropFromFront(200, 200))
        assertFalse(RadioQueueTrim.appliesTo(PlaylistType.RADIO, "RDCLAKabc"))
        assertFalse(RadioQueueTrim.appliesTo(PlaylistType.RADIO, "favorites"))
        assertTrue(RadioQueueTrim.appliesTo(PlaylistType.RADIO, "RDAMVMabc"))
    }
    @Test fun mirroredTrimmingIsPositionalEvenWithDuplicates() {
        val tracks = listOf("a", "b", "a", "c")
        assertEquals(listOf("a", "c"), RadioQueueTrim.afterFrontRemoved(tracks, listOf("a", "b")) { it })
        assertNull(RadioQueueTrim.afterFrontRemoved(tracks, listOf("a", "a")) { it })
        assertNull(RadioQueueTrim.afterFrontRemoved(tracks, tracks + "extra") { it })
    }
}
