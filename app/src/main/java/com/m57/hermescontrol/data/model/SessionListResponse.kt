package com.m57.hermescontrol.data.model
import kotlinx.serialization.Serializable

@Serializable
data class SessionListResponse(
    val sessions: List<SessionInfo>,
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)

@Serializable
data class SessionInfo(
    val id: String,
    val title: String? = null,
    val created_at: String? = null,
    val message_count: Int? = null,
    val status: String? = null,
    val preview: String? = null,
    val started_at: Double? = null,
    val source: String? = null,
    val parent_session_id: String? = null,
    val display_name: String? = null,
    // Owning profile (2026-09-22 Sync App alignment):
    //   - `/api/profiles/sessions?profile=all` sets this so the App can pin an
    //     agent-roster chip click to the right profile before `resumeSession`.
    //   - `/api/sessions` (current-profile only) omits it; the caller already
    //     knows it's the active profile, so we default to null and let
    //     `agentProfileSet(jobs)` resolve ownership where it matters.
    val profile: String? = null,
)

@Serializable
data class SessionStatsResponse(
    val total: Int = 0,
    val active: Int = 0,
)

@Serializable
data class SessionRenameRequest(
    val title: String,
)

@Serializable
data class BulkDeleteRequest(
    val ids: List<String>,
    val delete_all: Boolean = false,
)

@Serializable
data class PruneRequest(
    val days: Int,
)

@Serializable
data class SessionPromptResponse(
    val prompt: String? = null,
    val id: String? = null,
)
