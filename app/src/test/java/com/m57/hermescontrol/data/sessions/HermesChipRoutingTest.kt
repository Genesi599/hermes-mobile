package com.m57.hermescontrol.data.sessions

import com.m57.hermescontrol.data.model.ProfileSessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: the Hermes chip under a room must open Hermes's OWN private
 * conversation (`<room> · Hermes`), never the room's bound session.
 *
 * Root cause this guards (2026-09-23, "hermesagent 点不进去，应该和群聊分开呀"):
 * `resolveAgentConversation` short-circuited `if (agent.label == "Hermes")
 * return room.session`, so the chip opened the room's stale bound transcript
 * and the group chat / private chat surfaces stayed mixed.
 *
 * These tests pin the DATA-LAYER contract the fix relies on:
 *  1. `<room> · Hermes` IS classified as an agent conversation of the room
 *     (so it lands in `childSessions`), and
 *  2. the room's bare title is NOT (so the room itself stays out of the chips).
 */
class HermesChipRoutingTest {
    private fun session(id: String, title: String, profile: String = "default") =
        ProfileSessionInfo(
            id = id,
            title = title,
            profile = profile,
            message_count = 1,
        )

    private val project = "应用开发"

    @Test
    fun `hermes private conversation is a child of the room`() {
        val all =
            listOf(
                session("cb0dc0", project), // the ROOM's own bound session
                session("d23e98", "$project · Hermes"),
                session("278e05", "$project · Hermes 工程师", "hermeseng"),
            )

        val children = childrenForRoom(all, project)
        val ids = children.map { it.id }

        assertTrue("Hermes private chat must be a room child", "d23e98" in ids)
        assertTrue("other agents must stay children", "278e05" in ids)
        assertTrue("the room's own session is NOT a child", "cb0dc0" !in ids)
    }

    @Test
    fun `agentTitleFor builds the exact title the builder filters on`() {
        val want = agentTitleFor(project, "Hermes")
        assertEquals("$project · Hermes", want)
        assertTrue(isAgentConversationTitle(want, project))
    }

    @Test
    fun `hermes chip target is the private session not the room session`() {
        val roomSession = session("cb0dc0", project)
        val hermesChat = session("d23e98", "$project · Hermes")
        val all = listOf(roomSession, hermesChat)

        val children = childrenForRoom(all, project)
        val want = agentTitleFor(project, "Hermes")
        val resolved = children.firstOrNull { it.title == want }

        assertEquals("Hermes chip must resolve to its own chat", "d23e98", resolved?.id)
        assertTrue("and must NOT be the room session", resolved?.id != roomSession.id)
    }
}
