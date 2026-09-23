package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable

/**
 * A line in a project room (channel). The room owns its own message store —
 * it is NOT a session transcript: agent replies arrive through the delivery
 * path (post_room), and only the human's lines are posted through this
 * client. Mirrors the desktop `ChannelMessage` (channel-view.tsx).
 */
@Serializable
data class ChannelMessage(
    val id: Long = 0,
    val channel_id: String = "",
    val role: String = "",
    /** `human` / `agent` / `system` (author_kind). */
    val author_kind: String? = null,
    val author_label: String? = null,
    val author_avatar: String? = null,
    val content: String = "",
    val display_kind: String? = null,
    val timestamp: Double = 0.0,
)

@Serializable
data class ChannelMessagesResponse(
    val channel_id: String = "",
    // `order=latest` returns newest-first; the repository reverses to
    // chronological for rendering.
    val messages: List<ChannelMessage> = emptyList(),
)

/** Body for posting the human's line into a room (INSERT + router dispatch). */
@Serializable
data class ChannelPostRequest(
    val content: String,
)
