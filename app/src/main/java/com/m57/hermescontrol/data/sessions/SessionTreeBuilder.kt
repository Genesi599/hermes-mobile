package com.m57.hermescontrol.data.sessions

import com.m57.hermescontrol.data.model.Channel
import com.m57.hermescontrol.data.model.CronJob
import com.m57.hermescontrol.data.model.ProfileSessionInfo
import com.m57.hermescontrol.data.model.hasBoundSession
import com.m57.hermescontrol.data.model.participantsAsList

/**
 * One agent that participates in a project room.
 *
 * Mirrors `apps/desktop/src/lib/session-agents.ts:SessionAgent` on the
 * desktop. `profile` is the agent's Hermes profile — clicking its chip on
 * the sidebar routes the user into the agent's own conversation; `avatar`
 * is whatever the delivery wiring (cron job) recorded, falling back to
 * the agent's label initial when missing.
 */
data class SessionAgent(
    val label: String,
    val avatar: String? = null,
    val profile: String? = null,
)

/**
 * One row in the new Sync App sidebar tree (2026-09-22 Sync App alignment).
 *
 * The sidebar flattens to a list of these and renders each `room` followed
 * by its `agents` as a chip cluster — same visual structure the desktop
 * sidebar shows. A `room` whose bound session is itself the focused
 * conversation is rendered as the "selected" room row; a chip whose target
 * session id is the focused conversation is rendered as the "selected"
 * chip (inherently the same chip-clicks-uses-desktop-parity outcome the
 * desktop builds via `$focusedStoredSessionId`).
 */
data class SidebarRoom(
    /** The bound session id (the room itself). Null = orphan room. */
    val sessionId: String?,
    /** The room's display title — the project name. */
    val title: String,
    /** The session this row corresponds to, when the room is bound. */
    val session: ProfileSessionInfo?,
    /** The channel record behind the room (participants, etc.). */
    val channel: Channel?,
    /** Agents whose chips hang under this room. */
    val agents: List<SessionAgent>,
    /** The agent private chats hung under this room, derived from naming. */
    val childSessions: List<ProfileSessionInfo>,
)

/**
 * The project name in `Hermes` — same literal the desktop uses in
 * `apps/desktop/src/lib/session-agents.ts:HERMES_NAME` and the naming script
 * `agent_session_name.py`. Duplicated here (the data layer has no access
 * to `chat-identity.ts`) so the sidebar's Hermes-chip-routing decision
 * ("should this `X · Hermes` row be treated as Hermes's own conversation?")
 * stays consistent with the desktop's.
 *
 * If you change this string, also update `agent_session_name.py` on the
 * host side and `chat-identity.ts` on the desktop side — they all have to
 * agree on the same `Hermes` label.
 */
private const val HERMES_NAME = "Hermes"

/** The separator `agent_session_name.py` writes: `项目 · 智能体`. */
private const val AGENT_TITLE_SEPARATOR = " · "

/**
 * Strip the dedupe counter the naming script appends when a title is
 * taken: `星阶 · 流程搭档 (3)` → `星阶 · 流程搭档`.
 *
 * Mirrors the desktop `sessionToOpenForAgent` rule: an exact match wins,
 * a prefix match falls back. The dedupe counter has to be stripped from
 * the candidate title before the prefix match so `(3)` siblings don't
 * win over the agent's actual conversation.
 */
private fun stripDedupeCounter(title: String): String {
    val trimmed = title.trim()
    val idx = trimmed.lastIndexOf(" (")
    val tail = if (idx > 0 && trimmed.endsWith(")")) trimmed.substring(idx) else ""
    val digits = tail.drop(2).dropLast(1)
    return if (digits.isNotEmpty() && digits.all { it.isDigit() }) {
        trimmed.substring(0, idx)
    } else {
        trimmed
    }
}

/**
 * Is `childTitle` an agent private chat under `project`?
 *
 * Convention: `<项目> · <智能体>` (and the dedupe-counter variant). The
 * maintainer (Hermes) is recognized by `HERMES_NAME`; other agent names
 * come from the cron delivery jobs.
 */
fun isAgentConversationTitle(
    childTitle: String?,
    project: String,
): Boolean {
    if (childTitle.isNullOrBlank()) return false
    val trimmed = childTitle.trim()
    if (trimmed == project) return false // The room itself, not a child.
    val stripped = stripDedupeCounter(trimmed)
    if (!stripped.startsWith(project)) return false
    val suffix = stripped.substring(project.length)
    return suffix == AGENT_TITLE_SEPARATOR + HERMES_NAME ||
        suffix.startsWith(AGENT_TITLE_SEPARATOR) && suffix.length > AGENT_TITLE_SEPARATOR.length
}

/**
 * Extract the agent name from a child session title, or null when the
 * title doesn't match the `<项目> · <智能体>` shape.
 */
fun agentNameFromTitle(
    childTitle: String?,
    project: String,
): String? {
    if (!isAgentConversationTitle(childTitle, project)) return null
    val stripped = stripDedupeCounter(childTitle!!.trim())
    return stripped.substring(project.length + AGENT_TITLE_SEPARATOR.length).takeIf { it.isNotBlank() }
}

/**
 * The agents participating in `roomSessionId` — derived from two truths,
 * same as the desktop's `agentsForSession(jobs, sessionId, channelParticipants(room))`:
 *
 * 1. **Delivery wiring** — a cron job whose output is `attach_to_session`
 *    into this room with a producer label is, by construction, an agent
 *    speaking in this room.
 * 2. **The room's own record** — `Channel.participants` lists the agent
 *    labels that have actually SAID something in the room, so a woken
 *    agent appears even before its cron job has the producer fields set.
 *
 * The maintainer (Hermes) is NOT in this list — the roster renders its
 * own chip first, for every room, whether or not it has spoken yet.
 * The naming convention `<项目> · Hermes` lets the roster find Hermes's
 * OWN project conversation in the child list (see
 * `SessionTreeBuilder.childrenForRoom`).
 */
fun agentsForRoom(
    jobs: List<CronJob>,
    roomSessionId: String?,
    channel: Channel?,
): List<SessionAgent> {
    if (roomSessionId.isNullOrBlank()) return emptyList()

    val agents = mutableListOf<SessionAgent>()
    val seen = mutableSetOf<String>()

    fun push(
        label: String,
        profile: String?,
        avatar: String?,
    ) {
        if (label.isBlank() || label == HERMES_NAME || label in seen) return
        seen.add(label)
        agents.add(SessionAgent(label = label, avatar = avatar?.takeIf { it.isNotBlank() }, profile = profile?.takeIf { it.isNotBlank() }))
    }

    if (roomSessionId.isNotBlank()) {
        for (job in jobs) {
            if (job.attach_to_session != true) continue
            if (job.target_session_id?.trim() != roomSessionId) continue
            val label = job.agent_label?.trim().orEmpty()
            if (label.isBlank()) continue
            push(label, job.agent_profile, job.agent_avatar)
        }
    }

    val participants = channel?.takeIf { it.hasBoundSession() }?.participantsAsList().orEmpty()
    for (label in participants) {
        // profile/avatar come from the delivery wiring when it exists; an
        // agent that only spoke in the room still chips (label only — its
        // conversation cannot be reached by profile without wiring).
        val wired = jobs.firstOrNull { it.agent_label?.trim() == label }
        push(
            label,
            wired?.agent_profile?.takeIf { it.isNotBlank() },
            wired?.agent_avatar?.takeIf { it.isNotBlank() },
        )
    }

    return agents
}

/**
 * The set of agent-conversation sessions under this project — derived
 * purely from naming convention (`<项目> · <智能体>`), no backend lookup.
 *
 * Each is an entry in `allSessions` whose title parses as a child of
 * `project`. The sidebar renders these under the room row as part of
 * the chip cluster: when the chip's `profile` is known (from the cron
 * delivery wiring), the chip click navigates into that session;
 * otherwise the chip click falls back to navigating into this row's
 * own session (the agent's first message would land in the room).
 */
fun childrenForRoom(
    allSessions: List<ProfileSessionInfo>,
    project: String,
): List<ProfileSessionInfo> =
    allSessions.filter { isAgentConversationTitle(it.title, project) }

/**
 * The room for a project — the channel whose `session_id` matches the
 * session the room row renders. Returns the channel record, or null
 * when the project has no bound channel yet.
 *
 * The desktop implements the same lookup via `roomBySession(sessionId)`;
 * on mobile we take the project-name index instead (the session-id map
 * costs a `List<Channel>` walk per refresh — cheaper than a per-chip
 * callback). Both paths converge on the same `channelParticipants` data.
 */
fun roomForProject(
    channels: List<Channel>,
    project: String,
    sessionId: String?,
): Channel? {
    // Prefer the channel whose `session_id` matches the row's session id
    // (this is what makes the row a ROOM on the sidebar — `hasBoundSession`).
    if (sessionId != null) {
        val bySession = channels.firstOrNull { it.session_id == sessionId }
        if (bySession != null) return bySession
    }
    return channels.firstOrNull { it.project == project }
}

/**
 * The sidebar tree, derived from the three sidebar inputs:
 *
 * - `allSessions`: the cross-profile session list from
 *   `GET /api/profiles/sessions?profile=all` (room + children, flat).
 * - `channels`: the room list from `GET /api/channels`
 *   (decides which sessions render as project rooms vs. private chats).
 * - `jobs`: the cron delivery wiring from `GET /api/cron/jobs`
 *   (the agent chip cast: who delivers into each room).
 *
 * The output list is sorted by room recency — same order the rooms list
 * returns — with the agent children rendered under their room row, and
 * orphan sessions (no bound channel, no matching project) skipped.
 *
 * Does NOT make a Hermes-side conversation either; the room itself is
 * always present (its maintainer's chip is part of the rendering
 * contract), so this returns rooms in their bound order without any
 * "selected" / "active" bias.
 */
fun buildSidebarTree(
    allSessions: List<ProfileSessionInfo>,
    channels: List<Channel>,
    jobs: List<CronJob>,
): List<SidebarRoom> {
    // Index sessions by id for the room lookup.
    val sessionsById = allSessions.associateBy { it.id }

    // Index channels by session_id (the room binding) — one channel per room
    // because the backend binds a channel ↔ session 1:1.
    val channelBySessionId = channels
        .filter { it.hasBoundSession() }
        .associateBy { it.session_id!! }

    // Index sessions by project — derived from their bound channel.
    val sessionsByProject = mutableMapOf<String, ProfileSessionInfo>()
    for (channel in channels) {
        if (!channel.hasBoundSession()) continue
        val session = sessionsById[channel.session_id] ?: continue
        sessionsByProject[channel.project] = session
    }

    // Sort channels by `updated_at` desc (matches the desktop's "newest
    // activity first" contract from `listChannels`).
    val orderedChannels = channels.sortedByDescending { it.updated_at }

    val rooms = mutableListOf<SidebarRoom>()
    for (channel in orderedChannels) {
        if (!channel.hasBoundSession()) continue
        val session = sessionsById[channel.session_id] ?: continue
        val agents = agentsForRoom(jobs, channel.session_id, channel)
        val children = childrenForRoom(allSessions, channel.project)
        rooms.add(
            SidebarRoom(
                sessionId = session.id,
                title = channel.project,
                session = session,
                channel = channel,
                agents = agents,
                childSessions = children,
            ),
        )
    }
    return rooms
}

/**
 * The agent's conversation title under `project` — `星阶 · 流程搭档`.
 *
 * Used when the chip is missing a `profile` (no delivery wiring) and we
 * need a stable key to look up the child session by name. Mirrors the
 * desktop `sessionToOpenForAgent` title-prefix construction.
 */
fun agentTitleFor(
    project: String,
    agentLabel: String,
): String = "$project$AGENT_TITLE_SEPARATOR$agentLabel"

/**
 * Whether a session title looks like Hermes's own project conversation.
 *
 * The desktop's `isHermesConversation` uses the same rule (` · Hermes`
 * at the tail, dedupe counter allowed). Used to filter Hermes's own
 * conversation out of the "flat session list" view: Hermes speaks
 * INSIDE its conversations, so its own session is a child of a room
 * (reached from the chip), not a standalone row beside it.
 */
fun isHermesConversationTitle(
    title: String?,
): Boolean {
    if (title.isNullOrBlank()) return false
    val trimmed = title.trim()
    val stripped = stripDedupeCounter(trimmed)
    return stripped.endsWith(AGENT_TITLE_SEPARATOR + HERMES_NAME)
}