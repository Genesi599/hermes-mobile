package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Room (channel) message merge — a room only gains lines, so the merge must
 * union-by-id and preserve row identity for unchanged lines.
 */
class RoomMessagesMergeTest {
    @Test
    fun `merge keeps existing rows and appends new ones`() {
        val existing =
            listOf(
                ChatMessage(id = "room-c1-1", role = MessageRole.USER, content = "a"),
                ChatMessage(id = "room-c1-2", role = MessageRole.ASSISTANT, content = "b"),
            )
        val fetched =
            listOf(
                ChatMessage(id = "room-c1-1", role = MessageRole.USER, content = "a"),
                ChatMessage(id = "room-c1-2", role = MessageRole.ASSISTANT, content = "b"),
                ChatMessage(id = "room-c1-3", role = MessageRole.ASSISTANT, content = "new"),
            )
        val merged = mergeRoomMessages(existing, fetched)
        assertEquals(3, merged.size)
        assertEquals("new", merged.last().content)
        // Existing rows keep identity (same instance) — cheap recomposition.
        assertEquals(existing[0], merged[0])
        assertEquals(existing[1], merged[1])
    }

    @Test
    fun `merge handles first fetch into empty list`() {
        val fetched =
            listOf(
                ChatMessage(id = "room-c1-1", role = MessageRole.USER, content = "a"),
            )
        val merged = mergeRoomMessages(emptyList(), fetched)
        assertEquals(fetched, merged)
    }
}
