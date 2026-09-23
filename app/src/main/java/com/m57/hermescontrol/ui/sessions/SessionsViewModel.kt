package com.m57.hermescontrol.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.model.BulkDeleteRequest
import com.m57.hermescontrol.data.model.Channel
import com.m57.hermescontrol.data.model.ChannelListResponse
import com.m57.hermescontrol.data.model.CronJob
import com.m57.hermescontrol.data.model.PruneRequest
import com.m57.hermescontrol.data.model.ProfileSessionInfo
import com.m57.hermescontrol.data.model.ProfilesSessionsResponse
import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.model.SessionRenameRequest
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.sessions.SidebarRoom
import com.m57.hermescontrol.data.sessions.buildSidebarTree
import com.m57.hermescontrol.ui.common.safeLaunchLoad
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionStats(
    val total: Int = 0,
    val active: Int = 0,
)

/**
 * State of the Sync App sidebar (2026-09-22 alignment with the desktop
 * "project rooms + agent roster" model).
 *
 * The screen renders the rooms list with agent chips underneath each
 * room row; clicking a chip or a room sets
 * `NavigationController.pendingSessionId` and pushes the chat screen.
 * The `activeSessionId` lives in the `NavigationController` and is read
 * directly by the screen for highlight — not duplicated in this state
 * — that matches the desktop's `$focusedStoredSessionId` pattern
 * (see `apps/desktop/src/app/chat/sidebar/agent-roster.tsx`).
 *
 * The legacy session-modification state (rename / delete / prune) stays
 * in here too: the room + roster view drives the same one-conversation
 * commands the old flat list did, just from a different row.
 */
data class SessionsUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val rooms: List<SidebarRoom> = emptyList(),
    val flatSessions: List<SessionInfo> = emptyList(),
    val total: Int = 0,
    val errorMessage: String? = null,
    val stats: SessionStats = SessionStats(),
    val isLoadingStats: Boolean = false,
    val statsError: String? = null,
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val renamingSessionId: String? = null,
    val deletingSessionIds: Set<String> = emptySet(),
    val showPruneDialog: Boolean = false,
    val isPruning: Boolean = false,
    val isDeletingBulk: Boolean = false,
    val toastMessage: String? = null,
    val sessionToDeleteConfirm: String? = null,
    val showBulkDeleteConfirm: Boolean = false,
) {
    val hasMore: Boolean get() = total > flatSessions.size
}

/**
 * Sidebar view model — fetches the three sources the desktop sidebar
 * uses, in parallel, and assembles the room + roster view via
 * `buildSidebarTree`.
 *
 * The three sources:
 *   1. `/api/profiles/sessions?profile=all`  — every cross-profile session.
 *   2. `/api/channels`                       — the rooms (which sessions
 *                                              are project rooms + who has
 *                                              spoken there).
 *   3. `/api/cron/jobs?profile=all`          — the delivery wiring (which
 *                                              agent speaks into which room
 *                                              via `attach_to_session`).
 *
 * On the desktop, all three are fetched by separate stores; on the App we
 * batch them into a single load so the three refresh in lockstep. A
 * partial failure (e.g. cron jobs 500 while channels/sessions succeed)
 * degrades gracefully: the rooms render with only the participants from
 * the channels response, no chip from the failing cron jobs list.
 */
class SessionsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var statsJob: Job? = null

    /** Page size sent to the server — matches the default the gateway uses. */
    private companion object {
        const val PAGE_SIZE = 20
    }

    /** Load (or reload) the sidebar. Used by pull-to-refresh and initial load.
     *  Mirrors `safeLaunchLoad`'s contract: the loading flag flips synchronously
     *  (before the coroutine body runs) so callers can assert it immediately. */
    fun loadSessions() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob =
            viewModelScope.launch {
                val out = fetchSidebarInputs()
                if (out == null) {
                    // Both required sources failed; show an error banner but
                    // keep any previously-rendered rooms visible so a
                    // transient blip doesn't blank the sidebar.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = lastSidebarError
                                ?: "Failed to load sidebar — check connection.",
                        )
                    }
                    return@launch
                }
                val (rooms, flatSessions) = out
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        rooms = rooms,
                        flatSessions = flatSessions,
                        total = flatSessions.size,
                        selectedIds = emptySet(),
                    )
                }
            }
    }

    /** Legacy paged load — kept for callers that want the flat session list
     *  (e.g. tests, future "show all" view). Not wired into the UI yet. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val result =
                safeApiCall {
                    ApiClient.hermesApi.getSessions(
                        limit = PAGE_SIZE,
                        offset = state.flatSessions.size,
                        order = "recent",
                    )
                }
            when (result) {
                is NetworkResult.Success -> {
                    val data = result.data
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            flatSessions = it.flatSessions + data.sessions,
                            total = data.total,
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            errorMessage = "Failed to load more: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    // ── Stats ────────────────────────────────────────────────────────────

    fun loadStats() {
        statsJob =
            safeLaunchLoad(
                currentJob = statsJob,
                apiCall = {
                    safeApiCall { ApiClient.hermesApi.getSessionStats() }
                },
                onStart = { _uiState.update { it.copy(isLoadingStats = true, statsError = null) } },
                onSuccess = { data ->
                    _uiState.update {
                        it.copy(
                            isLoadingStats = false,
                            stats =
                                data?.let { SessionStats(total = it.total, active = it.active) }
                                    ?: SessionStats(),
                        )
                    }
                },
                onError = { errorMsg ->
                    _uiState.update {
                        it.copy(
                            isLoadingStats = false,
                            statsError = errorMsg,
                        )
                    }
                },
            )
    }

    // ── Bulk selection ───────────────────────────────────────────────────

    fun toggleSelecting() {
        _uiState.update {
            it.copy(
                isSelecting = !it.isSelecting,
                selectedIds = if (it.isSelecting) emptySet() else it.selectedIds,
            )
        }
    }

    fun exitSelecting() {
        _uiState.update { it.copy(isSelecting = false, selectedIds = emptySet()) }
    }

    fun toggleSessionSelection(id: String) {
        _uiState.update {
            val updated = it.selectedIds.toMutableSet()
            if (updated.contains(id)) updated.remove(id) else updated.add(id)
            it.copy(selectedIds = updated)
        }
    }

    fun selectAll() {
        _uiState.update {
            it.copy(selectedIds = it.flatSessions.map { s -> s.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    // ── Rename ───────────────────────────────────────────────────────────

    fun startRenaming(sessionId: String) {
        _uiState.update { it.copy(renamingSessionId = sessionId) }
    }

    fun cancelRenaming() {
        _uiState.update { it.copy(renamingSessionId = null) }
    }

    fun renameSession(
        sessionId: String,
        newTitle: String,
    ) {
        if (newTitle.isBlank()) {
            _uiState.update { it.copy(renamingSessionId = null, toastMessage = "Title cannot be empty") }
            return
        }
        viewModelScope.launch {
            val result =
                safeApiCall {
                    ApiClient.hermesApi.renameSession(
                        sessionId = sessionId,
                        body = SessionRenameRequest(title = newTitle),
                    )
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            renamingSessionId = null,
                            flatSessions =
                                it.flatSessions.map { s ->
                                    if (s.id == sessionId) s.copy(title = newTitle) else s
                                },
                            toastMessage = "Session renamed",
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            renamingSessionId = null,
                            toastMessage = "Rename failed: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    // ── Copy prompt ──────────────────────────────────────────────────────

    fun copySessionPrompt(sessionId: String) {
        viewModelScope.launch {
            val result =
                safeApiCall {
                    ApiClient.hermesApi.getSessionPrompt(sessionId)
                }
            when (result) {
                is NetworkResult.Success -> {
                    val promptText = result.data?.prompt ?: "No prompt available"
                    _uiState.update { it.copy(toastMessage = promptText) }
                }

                is NetworkResult.Failure -> {
                    _uiState.update { it.copy(toastMessage = "Failed to get prompt: ${result.error.message}") }
                }
            }
        }
    }

    // ── Delete (single) ──────────────────────────────────────────────────

    fun requestDeleteSession(sessionId: String) {
        _uiState.update { it.copy(sessionToDeleteConfirm = sessionId) }
    }

    fun cancelDeleteSession() {
        _uiState.update { it.copy(sessionToDeleteConfirm = null) }
    }

    fun confirmDeleteSession() {
        val sessionId = _uiState.value.sessionToDeleteConfirm ?: return
        _uiState.update {
            it.copy(
                sessionToDeleteConfirm = null,
                deletingSessionIds = it.deletingSessionIds + sessionId,
            )
        }
        viewModelScope.launch {
            val result =
                safeApiCall {
                    ApiClient.hermesApi.deleteSession(sessionId)
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            deletingSessionIds = it.deletingSessionIds - sessionId,
                            flatSessions = it.flatSessions.filter { s -> s.id != sessionId },
                            rooms = it.rooms.map { room ->
                                room.copy(
                                    session = if (room.session?.id == sessionId) null else room.session,
                                    childSessions = room.childSessions.filter { c -> c.id != sessionId },
                                )
                            }.filter { room -> room.session != null || room.childSessions.isNotEmpty() },
                            total = it.total - 1,
                            toastMessage = "Session deleted",
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            deletingSessionIds = it.deletingSessionIds - sessionId,
                            toastMessage = "Delete failed: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    // ── Bulk delete ──────────────────────────────────────────────────────

    fun requestBulkDelete() {
        _uiState.update { it.copy(showBulkDeleteConfirm = true) }
    }

    fun cancelBulkDelete() {
        _uiState.update { it.copy(showBulkDeleteConfirm = false) }
    }

    fun confirmBulkDelete() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return

        _uiState.update { it.copy(showBulkDeleteConfirm = false, isDeletingBulk = true) }
        viewModelScope.launch {
            val result =
                safeApiCall {
                    ApiClient.hermesApi.bulkDeleteSessions(
                        body = BulkDeleteRequest(ids = ids),
                    )
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeletingBulk = false,
                            isSelecting = false,
                            selectedIds = emptySet(),
                            flatSessions = it.flatSessions.filter { s -> s.id !in ids },
                            total = it.total - ids.size,
                            toastMessage = "${ids.size} session(s) deleted",
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isDeletingBulk = false,
                            toastMessage = "Delete failed: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    // ── Prune ────────────────────────────────────────────────────────────

    fun showPruneDialog() {
        _uiState.update { it.copy(showPruneDialog = true) }
    }

    fun hidePruneDialog() {
        _uiState.update { it.copy(showPruneDialog = false) }
    }

    fun pruneSessions(days: Int) {
        if (days < 1) return
        _uiState.update { it.copy(isPruning = true, showPruneDialog = false) }
        viewModelScope.launch {
            val result =
                safeApiCall {
                    ApiClient.hermesApi.pruneSessions(
                        body = PruneRequest(days = days),
                    )
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPruning = false,
                            toastMessage = "Old sessions pruned",
                        )
                    }
                    loadSessions()
                    loadStats()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isPruning = false,
                            toastMessage = "Prune failed: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    // ── Toast ────────────────────────────────────────────────────────────

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // ── Internals ────────────────────────────────────────────────────────

    /**
     * Fetch the sidebar sources in parallel and tolerate partial failures:
     * each sub-fetch is wrapped in its own `safeApiCall` so a failing cron
     * endpoint does not zero out channels + sessions. The desktop handles
     * this with per-store error boundaries; the App collapses the same
     * behavior into one round trip here.
     *
     * @return null when both required sources (profiles/sessions and
     *         channels) failed — the caller surfaces an error banner.
     */
    private suspend fun fetchSidebarInputs(): Pair<List<SidebarRoom>, List<SessionInfo>>? =
        coroutineScope {
            val sessionsAsync = async { safeApiCall<ProfilesSessionsResponse> { ApiClient.hermesApi.getProfilesSessions() } }
            val channelsAsync = async { safeApiCall<ChannelListResponse> { ApiClient.hermesApi.getChannels() } }
            val jobsAsync = async { safeApiCall<List<CronJob>> { ApiClient.hermesApi.getCronJobs() } }
            val allSessionsAsync = async { safeApiCall<SessionListResponse> { ApiClient.hermesApi.getSessions(limit = PAGE_SIZE) } }
            val sessionsRes = sessionsAsync.await()
            val channelsRes = channelsAsync.await()
            val jobsRes = jobsAsync.await()
            val allSessionsRes = allSessionsAsync.await()

            // Sessions + channels are the *two* required sources for a
            // sidebar to render; when both fail the sidebar cannot build
            // anything — signal the caller to show the error state. Either
            // one alone still renders (degraded: rooms without children, or
            // children without room metadata). Cron jobs are optional: a
            // missing endpoint just means chips render as "bare" until the
            // gateway regains access.
            val sessionsFailed = sessionsRes is NetworkResult.Failure
            val channelsFailed = channelsRes is NetworkResult.Failure
            if (sessionsFailed && channelsFailed) {
                lastSidebarError = (sessionsRes as? NetworkResult.Failure)?.error?.message
                    ?: (channelsRes as? NetworkResult.Failure)?.error?.message
                return@coroutineScope null
            }

            val profileSessions = sessionsRes.takeIfSuccess()?.sessions.orEmpty()
            val channels = channelsRes.takeIfSuccess()?.channels.orEmpty()
            val jobs = jobsRes.takeIfSuccess().orEmpty()
            val flat = allSessionsRes.takeIfSuccess()?.sessions.orEmpty()

            val rooms = buildSidebarTree(profileSessions, channels, jobs)
            rooms to flat
        }

    /** Message of the most recent failed sidebar fetch, for the error banner. */
    private var lastSidebarError: String? = null

    private fun <T : Any> NetworkResult<T>.takeIfSuccess(): T? =
        (this as? NetworkResult.Success<T>)?.data
}