package com.m57.hermescontrol.data.sessions

import com.m57.hermescontrol.data.model.Channel
import com.m57.hermescontrol.data.model.CronJob
import com.m57.hermescontrol.data.model.ProfileSessionInfo
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the desktop-mirroring room + roster derivation (2026-09-23 Sync App
 * alignment). The desktop sidebar derives the same tree from the same inputs
 * in `apps/desktop/src/lib/session-agents.ts`; these tests pin the App side
 * so refactors don't drift away from the desktop contract.
 *
 * What we cover:
 *  - Rooms come from bound channels (`channel.session_id` ↔ session id);
 *    sessions without a bound channel never render as rooms.
 *  - Agent chats (`<project> · <agent>`) hang under their room's
 *    `childSessions`.
 *  - Cron jobs with `attach_to_session=true` add producer-labelled chips
 *    (label / profile / avatar) to the room they target.
 *  - Channel `participants` extend the chip set with agents that have spoken
 *    but have no cron wiring (bare, label-only chips).
 *  - Hermes is never a chip (it's the maintainer, rendered separately).
 */
class SessionTreeBuilderTest {
    private fun session(
        id: String,
        title: String?,
    ): ProfileSessionInfo =
        ProfileSessionInfo(
            id = id,
            title = title,
            message_count = 1,
        )

    private fun channel(
        project: String,
        sessionId: String? = null,
        participants: List<String> = emptyList(),
        updatedAt: Long = 0,
    ): Channel =
        Channel(
            id = "chan-$project",
            project = project,
            title = project,
            session_id = sessionId,
            updated_at = updatedAt,
            participants =
                if (participants.isEmpty()) {
                    null
                } else {
                    buildJsonArray { participants.forEach { add(JsonPrimitive(it)) } }
                },
        )

    private fun cronJob(
        id: String,
        attach: Boolean = true,
        target: String? = null,
        label: String? = null,
        profile: String? = null,
        avatar: String? = null,
    ): CronJob =
        CronJob(
            id = id,
            name = id,
            agent_label = label,
            agent_avatar = avatar,
            agent_profile = profile,
            attach_to_session = attach,
            target_session_id = target,
        )

    @Test
    fun `rooms come from bound channels only`() {
        val sessions =
            listOf(
                session("s-app", "应用开发"),
                session("s-bcell", "B 细胞"),
            )
        val channels =
            listOf(
                channel("应用开发", sessionId = "s-app"),
            )
        val rooms = buildSidebarTree(sessions, channels, emptyList())

        assertEquals(1, rooms.size)
        assertEquals("s-app", rooms.single().sessionId)
        assertEquals("应用开发", rooms.single().title)
        // The un-bound session never becomes a room.
        assertTrue(rooms.none { it.sessionId == "s-bcell" })
    }

    @Test
    fun `agent chats hang under the matching project room`() {
        val sessions =
            listOf(
                session("room-app", "应用开发"),
                session("chip-app-balancemon", "应用开发 · 余额监控工程师"),
                session("chip-app-hermes", "应用开发 · Hermes"),
            )
        val channels = listOf(channel("应用开发", sessionId = "room-app"))
        val rooms = buildSidebarTree(sessions, channels, emptyList())

        assertEquals(1, rooms.size)
        val room = rooms.single()
        assertEquals("应用开发", room.title)
        // Both agent children hang under the room row.
        assertEquals(
            setOf("chip-app-balancemon", "chip-app-hermes"),
            room.childSessions.map { it.id }.toSet(),
        )
    }

    @Test
    fun `cron job adds producer-labelled chip to the target room`() {
        val sessions = listOf(session("room-app", "应用开发"))
        val channels = listOf(channel("应用开发", sessionId = "room-app"))
        val jobs =
            listOf(
                cronJob(
                    id = "cron-1",
                    target = "room-app",
                    label = "Hermes 工程师",
                    profile = "hermeseng",
                    avatar = "🔨",
                ),
            )
        val rooms = buildSidebarTree(sessions, channels, jobs)

        val room = rooms.single()
        val chip = room.agents.single()
        assertEquals("Hermes 工程师", chip.label)
        assertEquals("hermeseng", chip.profile)
        assertEquals("🔨", chip.avatar)
    }

    @Test
    fun `channel participants add bare chips when no wiring exists`() {
        val sessions = listOf(session("room-app", "应用开发"))
        val channels =
            listOf(
                channel(
                    "应用开发",
                    sessionId = "room-app",
                    participants = listOf("维基管家"),
                ),
            )
        val rooms = buildSidebarTree(sessions, channels, emptyList())

        val room = rooms.single()
        val chip = room.agents.single()
        assertEquals("维基管家", chip.label)
        // Bare chip — no delivery wiring, so no profile to open.
        assertNull(chip.profile)
    }

    @Test
    fun `wired participant keeps its profile from the cron job`() {
        val sessions = listOf(session("room-app", "应用开发"))
        val channels =
            listOf(
                channel(
                    "应用开发",
                    sessionId = "room-app",
                    participants = listOf("余额监控工程师"),
                ),
            )
        val jobs =
            listOf(
                cronJob(
                    id = "cron-1",
                    target = "room-app",
                    label = "余额监控工程师",
                    profile = "balancemon",
                ),
            )
        val rooms = buildSidebarTree(sessions, channels, jobs)

        val room = rooms.single()
        val chip = room.agents.single()
        assertEquals("余额监控工程师", chip.label)
        assertEquals("balancemon", chip.profile)
    }

    @Test
    fun `hermes is never rendered as an agent chip`() {
        val sessions = listOf(session("room-app", "应用开发"))
        val channels =
            listOf(
                channel(
                    "应用开发",
                    sessionId = "room-app",
                    participants = listOf("Hermes"),
                ),
            )
        val rooms = buildSidebarTree(sessions, channels, emptyList())

        assertTrue(rooms.single().agents.isEmpty())
    }

    @Test
    fun `jobs targeting another room do not corrupt the chips`() {
        val sessions = listOf(session("room-app", "应用开发"))
        val channels = listOf(channel("应用开发", sessionId = "room-app"))
        val jobs =
            listOf(
                cronJob(
                    id = "ghost",
                    target = "missing-room",
                    label = "幽灵管家",
                ),
            )
        val rooms = buildSidebarTree(sessions, channels, jobs)

        // The ghost job's target doesn't match any room — the room's chip
        // list stays clean.
        assertTrue(rooms.single().agents.isEmpty())
    }

    @Test
    fun `rooms sort by channel recency newest first`() {
        val sessions =
            listOf(
                session("s-old", "老项目"),
                session("s-new", "新项目"),
            )
        val channels =
            listOf(
                channel("老项目", sessionId = "s-old", updatedAt = 100),
                channel("新项目", sessionId = "s-new", updatedAt = 200),
            )
        val rooms = buildSidebarTree(sessions, channels, emptyList())

        assertEquals(listOf("新项目", "老项目"), rooms.map { it.title })
    }
}
