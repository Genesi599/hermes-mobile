package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Channel — the group chat as a first-class entity (server model, mirrors
 * `apps/desktop/src/lib/channels.ts:Channel` on the desktop).
 *
 * A channel is where agents and the human exchange information; it owns its
 * own messages and is NOT a session: posting a line is an INSERT on the
 * backend, not a model turn, so the room stays a place rather than becoming
 * somebody's conversation. Agents post through their own delivery path;
 * only the human's lines come through this client.
 *
 * The Sync App sidebar uses this to derive the room layer: a channel whose
 * `session_id` matches a session row is that session's room, and the
 * roster's maintainer + cast are projected from the same delivery wiring
 * the desktop builds `agentsForSession` from.
 */
@Serializable
data class Channel(
    val id: String,
    val project: String,
    val title: String,
    // The backend emits epoch floats (`1789614562.539754`), not integer
    // seconds — a `Long` declaration makes kotlinx.serialization throw on
    // decode, which silently zeroed the whole channel list (the sidebar then
    // rendered "No project rooms yet" despite a 200 response).
    val created_at: Double = 0.0,
    val updated_at: Double = 0.0,
    val message_count: Int = 0,
    /**
     * Bound conversation. The desktop treats this as the room's "host" session
     * — opening the room navigates to this session, and the agent chips hang
     * underneath it. Null when the channel exists but no session has been
     * bound yet (a freshly created project before any model turn in it).
     */
    val session_id: String? = null,
    val last_routed_message_id: Long? = null,
    /**
     * Agent labels that have spoken in the room. The desktop reads this as
     * `null | string | string[]` because the server emits a JSON-encoded
     * string in some transport modes; we accept both shapes and normalize
     * via [participants].
     */
    val participants: JsonElement? = null,
)

@Serializable
data class ChannelListResponse(
    val channels: List<Channel> = emptyList(),
)

/**
 * The room's participants as a flat list of agent labels, normalizing the
 * three shapes the backend can emit (array / JSON-string-of-array / null).
 *
 * Mirrors `apps/desktop/src/lib/channels.ts:channelParticipants`. Without
 * this normalization the sidebar would render an empty roster on rooms whose
 * participants came back as a quoted JSON string.
 */
fun Channel.participantsAsList(): List<String> {
    val raw = participants ?: return emptyList()

    val arr: JsonArray? =
        when {
            raw is JsonArray -> raw
            raw is JsonPrimitive && raw.isString -> {
                val text = raw.contentOrNull ?: return emptyList()
                runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(text).jsonArray
                }.getOrNull()
            }
            else -> null
        }

    return arr?.mapNotNull { element ->
        (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }.orEmpty()
}

/**
 * Whether this channel has a real room (a `session_id` is bound to it).
 * The sidebar uses this to decide whether to render the row as a project
 * room with chips, or to skip channels that exist on the server but have
 * not yet been promoted to a conversation.
 */
fun Channel.hasBoundSession(): Boolean = !session_id.isNullOrBlank()