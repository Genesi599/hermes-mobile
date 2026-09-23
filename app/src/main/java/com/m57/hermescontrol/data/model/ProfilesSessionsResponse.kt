package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable

/**
 * Response shape for `GET /api/profiles/sessions?profile=all` — the desktop
 * sidebar's "all profiles in one fetch" endpoint.
 *
 * The mobile sidebar uses it to derive the room + child structure: every
 * session returned carries its owning `profile` so the row's project group
 * is unambiguous even when the same project name lives under several
 * profiles (which can happen on staging setups that clone the .git copy
 * into multiple profiles).
 */
@Serializable
data class ProfilesSessionsResponse(
    val sessions: List<ProfileSessionInfo> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)

/**
 * The "all profiles" view of a session, returned by `/api/profiles/sessions`.
 *
 * Mirrors desktop `apps/desktop/src/hermes/listAllProfileSessions` — the
 * desktop calls the same endpoint with `profile=all` to assemble its
 * cross-profile session list. We extend [SessionInfo] with the fields the
 * desktop sees (`['profile']` + `'cwd'` + `'git_repo_root'`) so the
 * project's grouping key matches the sidebar's project id derivation
 * (see `projectIdForCwd` in the desktop store).
 */
@Serializable
data class ProfileSessionInfo(
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
    val profile: String? = null,
    val cwd: String? = null,
    val git_repo_root: String? = null,
)