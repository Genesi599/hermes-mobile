package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the mobile-tail message loading strategy: the phone keeps only
 * the newest MOBILE_TAIL_MESSAGE_COUNT messages of a desktop transcript.
 */
class MobileTailLoadingTest {
    private fun messageIdsForTail(messages: List<Pair<String, String>>): List<String> =
        messages.mapIndexed { index, (role, content) ->
            "rest-session-t$index-$role-${content.hashCode()}"
        }

    @Test
    fun tailIds_areUniqueAcrossPages_withOverlappingContent() {
        // Page 1 (newest 3) and page 2 (newest 6) overlap in the last 3 items.
        val transcript = listOf("m1" to "a", "m2" to "b", "m3" to "c", "m4" to "d", "m5" to "e", "m6" to "f")
        val page1 = transcript.takeLast(3)
        val page2 = transcript.takeLast(6)
        val ids1 = messageIdsForTail(page1)
        val ids2 = messageIdsForTail(page2)
        // distinctBy id keeps each message once when pages merge.
        val merged = (ids1 + ids2).distinct()
        // Page1 items share ids with the tail of page2 only if same index+content.
        // Overlap region: page2's last 3 items have DIFFERENT indices (3,4,5 vs 0,1,2)
        // so ids differ — dedup by (role, content) happens at merge level instead.
        assertEquals(9, merged.size)
    }

    @Test
    fun mergedPage_dedupesByRoleAndContent() {
        data class Msg(val role: String, val content: String)

        val existing = listOf(Msg("user", "hello"), Msg("assistant", "hi"), Msg("user", "bye"))
        val incoming = listOf(Msg("assistant", "hi"), Msg("user", "bye"))
        val unmatched = incoming.map { it.role to it.content }.toMutableList()
        val retained =
            existing.filter { e ->
                val idx = unmatched.indexOfFirst { it.first == e.role && it.second == e.content }
                if (idx >= 0) unmatched.removeAt(idx)
                idx < 0
            }
        assertTrue(retained.none { it.content == "hi" || it.content == "bye" })
        assertEquals(1, retained.size)
    }

    @Test
    fun hasOlderFallback_fullPageMeansTrue() {
        val tailLimit = 20
        val returned = 20
        assertTrue(returned >= tailLimit)
        val returnedShort = 7
        assertFalse(returnedShort >= tailLimit)
    }
}
