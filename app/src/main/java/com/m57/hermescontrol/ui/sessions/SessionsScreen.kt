package com.m57.hermescontrol.ui.sessions

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.ChatScreen
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.ProfileSessionInfo
import com.m57.hermescontrol.data.sessions.SessionAgent
import com.m57.hermescontrol.data.sessions.SidebarRoom
import com.m57.hermescontrol.data.sessions.agentTitleFor
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.LoadingState
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.StatCard
import com.m57.hermescontrol.ui.common.ToastEffect
import com.m57.hermescontrol.ui.common.listContentPadding

/**
 * Sync App sidebar — 2026-09-22 alignment with the desktop's
 * "project room + agent roster" view.
 *
 * Each top-level row is a project room, drawn from a {@link SidebarRoom}.
 * Underneath, the room renders two kinds of agent-reach UI:
 *
 * 1. **Chips** for wired agents (those with a delivery cron job pointing
 *    into the room). Clicking a chip opens that agent's conversation —
 *    looked up by the `<项目> · <智能体>` title, since the chip's
 *    `profile` may not yet have wired a session.
 * 2. **Auto-detected child sessions** — `<项目> · <智能体>` titled rows
 *    already in `ProfileSessionInfo.allSessions`. These render as
 *    secondary rows under the room when no delivery wiring has set up
 *    a chip for the same agent yet.
 *
 * Highlight (the "selected" room/chip) follows
 * `NavigationController.lastOpenedSessionId` — survives the chat screen
 * clearing `pendingSessionId` (same single source of truth the desktop
 * sidebar uses, `$focusedStoredSessionId`).
 */
@Composable
fun SessionsScreen(onOpenDrawer: () -> Unit) {
    val viewModel: SessionsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeId = NavigationController.pendingSessionId ?: NavigationController.lastOpenedSessionId

    LaunchedEffect(Unit) {
        viewModel.loadSessions()
        viewModel.loadStats()
    }

    HermesScaffold(
        title = { Text(stringResource(R.string.screen_history)) },
        navigationIcon = NavIcon.Menu(onOpen = onOpenDrawer),
        onRefresh = viewModel::loadSessions,
        isRefreshing = state.isLoading,
        actions = { TopBarActions(state, viewModel) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.rooms.isEmpty() -> LoadingState()
                state.errorMessage != null && state.rooms.isEmpty() -> {
                    ErrorState(
                        message = state.errorMessage ?: "",
                        onRetry = viewModel::loadSessions,
                    )
                }
                state.rooms.isEmpty() -> EmptyState(
                    title = stringResource(R.string.sessions_empty_title),
                    subtitle = stringResource(R.string.sessions_empty_subtitle),
                )
                else -> SidebarList(state, activeId, viewModel)
            }

            ToastEffect(toastMessage = state.toastMessage, onClearToast = viewModel::clearToast)
        }
    }
}

@Composable
private fun TopBarActions(
    state: SessionsUiState,
    viewModel: SessionsViewModel,
) {
    val spacing = LocalSpacing.current
    if (state.isSelecting) {
        TextButton(onClick = viewModel::selectAll) {
            Icon(
                imageVector = Icons.Filled.SelectAll,
                contentDescription = null,
                modifier = Modifier.size(spacing.md),
            )
            Spacer(Modifier.width(spacing.xs))
            Text(stringResource(R.string.sessions_select_all))
        }
        IconButton(onClick = viewModel::requestBulkDelete, enabled = state.selectedIds.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = stringResource(R.string.sessions_bulk_delete),
                tint =
                    if (state.selectedIds.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = viewModel::toggleSelecting) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.sessions_cancel),
            )
        }
    } else {
        IconButton(onClick = viewModel::toggleSelecting) {
            Icon(
                imageVector = Icons.Filled.SelectAll,
                contentDescription = stringResource(R.string.sessions_select_all),
            )
        }
    }
}

@Composable
private fun SidebarList(
    state: SessionsUiState,
    activeId: String?,
    viewModel: SessionsViewModel,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("sessions_list"),
        contentPadding = listContentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item("stats") {
            StatsRow(state, viewModel)
        }
        items(items = state.rooms, key = { it.sessionId ?: it.title }) { room ->
            SidebarRoomRow(
                room = room,
                activeId = activeId,
                onRoomClick = {
                    // The room is the channel, not its bound session —
                    // agent replies land in the channel's own store, so
                    // opening the session transcript would show a stale
                    // thread (2026-09-23 "messages not synced" report).
                    val channel = room.channel
                    if (channel != null) {
                        openRoom(channel.id, room.title)
                    } else {
                        val sid = room.session?.id ?: room.sessionId
                        if (sid != null) openSession(sid)
                    }
                },
                onAgentClick = { agent ->
                    val childSession = resolveAgentConversation(room, agent)
                    if (childSession != null) {
                        openSession(childSession.id, childSession.profile ?: agent.profile, childSession.title)
                    } else {
                        // No child session yet — chip is "wired but no
                        // agent has spoken"; open the room so the user
                        // sees the wiring but no agent row lights up.
                        room.session?.let { openSession(it.id, it.profile, it.title) }
                    }
                },
            )
        }
        if (state.isLoadingMore) {
            item("loading_more") {
                Box(
                    Modifier.fillMaxWidth().padding(spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(spacing.lg))
                }
            }
        }
    }
}

@Composable
private fun StatsRow(
    state: SessionsUiState,
    @Suppress("UNUSED_PARAMETER") viewModel: SessionsViewModel,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        StatCard(
            label = stringResource(R.string.sessions_stat_total),
            value = if (state.isLoadingStats) "…" else state.stats.total.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.sessions_stat_active),
            value = if (state.isLoadingStats) "…" else state.stats.active.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SidebarRoomRow(
    room: SidebarRoom,
    activeId: String?,
    onRoomClick: () -> Unit,
    onAgentClick: (SessionAgent) -> Unit,
) {
    val spacing = LocalSpacing.current
    val statusColors = LocalHermesStatusColors.current
    val isRoomActive = room.sessionId != null && room.sessionId == activeId
    val agentsWithSessions = resolveAgentsWithConversations(room)
    // Desktop parity: the roster renders ONLY chips derived from the
    // delivery wiring + participants (`agentsForSession` in
    // agent-roster.tsx). Child sessions with no wiring are NOT extra chips
    // — they're reachable via the wired chip's title-prefix fallback
    // (`resolveAgentConversation` matches `X · Y` and `X · Y (n)`).
    // The earlier "orphan chip" layer rendered the `X · Y (2..n)` dedupe
    // siblings as extra blank boxes (label parse returned empty strings —
    // 10 gray squares on 星阶 in the 2026-09-23 screenshot).

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .testTag("sidebar_room_${room.title}")
                .clickable(onClick = onRoomClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                                    if (isRoomActive) statusColors.success.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surface,
                            ),
                        border = if (isRoomActive) BorderStroke(1.dp, statusColors.success) else null,
    ) {
        Column(modifier = Modifier.padding(spacing.sm)) {
            // ── Room row ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(spacing.md), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = statusColors.success,
                    )
                }
                Spacer(Modifier.width(spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isRoomActive) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    room.session?.preview?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = room.session?.message_count?.toString() ?: "0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Chips cluster ───────────────────────────────────────────
            if (agentsWithSessions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    // Hermes chip always first, mirroring the desktop.
                    val her = agentsWithSessions.firstOrNull { it.label == "Hermes" }
                    if (her != null) {
                        AgentChip(
                            label = "Hermes",
                            avatar = her.avatar,
                            isActive = activeId == room.session?.id && her.label == "Hermes",
                            onClick = { onAgentClick(her) },
                        )
                    }

                    agentsWithSessions
                        .filter { it.label != "Hermes" }
                        .forEach { agent ->
                            val child = resolveAgentConversation(room, agent)
                            AgentChip(
                                label = agent.label,
                                avatar = agent.avatar,
                                isActive = child != null && child.id == activeId,
                                onClick = { onAgentClick(agent) },
                            )
                        }
                }
            }
        }
    }
}

/**
 * An agent chip — the visual contract from the desktop sidebar:
 * avatar circle + label, muted when the chip is wired but has no child
 * session yet (the desktop renders this as a `wiredNoChild` style with
 * a softer text color and no completion dot).
 *
 * `isActive` is the highlighted state when this chip's child session
 * is currently the focused conversation.
 */
@Composable
private fun AgentChip(
    label: String,
    avatar: String?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val statusColors = LocalHermesStatusColors.current
    val transition = rememberInfiniteTransition(label = "agent_chip_arc")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1800),
                repeatMode = RepeatMode.Restart,
            ),
        label = "agent_chip_arc_angle",
    )

    val borderColor =
        if (isActive) statusColors.success else MaterialTheme.colorScheme.outline

    AssistChip(
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (avatar != null && avatar.length == 1) {
                    Text(
                        text = avatar,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.rotate(if (isActive) angle else 0f),
                    )
                    Spacer(Modifier.width(spacing.xs))
                }
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
            }
        },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor =
                    if (isActive) statusColors.success.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surface,
            ),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = borderColor),
    )

}

/** Resolve the agent's conversation under a room by name. */
private fun resolveAgentConversation(
    room: SidebarRoom,
    agent: SessionAgent,
): ProfileSessionInfo? {
    // Hermes chip — `X · Hermes` is the room's own session (the maintainer
    // speaks IN the room, so its own conversation is the room session).
    if (agent.label == "Hermes") return room.session

    // First try the conventional title.
    val want = agentTitleFor(room.title, agent.label)
    val exact = room.childSessions.firstOrNull { it.title == want }
    if (exact != null) return exact

    // Then prefix — handles the `(n)` dedupe-counter variant the naming
    // script appends when the agent label is already taken.
    return room.childSessions.firstOrNull { it.title?.startsWith(want) == true }
}

/**
 * Agents for whom there is at least one conversation under the room —
 * wired agents (from the cron jobs) AND children that already exist.
 * Hermes is added explicitly because the roster never renders it through
 * the jobs list.
 */
private fun resolveAgentsWithConversations(room: SidebarRoom): List<SessionAgent> {
    val wiredByLabel = room.agents.associateBy { it.label }
    val labels = mutableSetOf<String>()
    val agents = mutableListOf<SessionAgent>()
    // Hermes first (the roster always renders Hermes at index 0).
    if (room.session != null) {
        val herWired = wiredByLabel["Hermes"]
        agents.add(SessionAgent(label = "Hermes", avatar = herWired?.avatar, profile = herWired?.profile))
        labels.add("Hermes")
    }
    for (a in room.agents) {
        if (a.label in labels) continue
        agents.add(a)
        labels.add(a.label)
    }
    return agents
}

private fun openSession(sessionId: String, profile: String? = null, titleHint: String? = null) {
    NavigationController.pendingSessionId = sessionId
    NavigationController.pendingSessionProfile = profile
    NavigationController.pendingSessionTitle = titleHint
    NavigationController.navigateTo(ChatScreen)
}

/** Open a project room (channel) — the group chat view, not a session. */
private fun openRoom(channelId: String, title: String) {
    NavigationController.pendingSessionId = null
    NavigationController.pendingSessionProfile = null
    NavigationController.pendingSessionTitle = null
    NavigationController.pendingRoomId = channelId
    NavigationController.pendingRoomTitle = title
    NavigationController.navigateTo(ChatScreen)
}