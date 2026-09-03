package com.m57.hermescontrol.data.model
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class SessionMessagesResponse(
    val messages: List<SessionMessage>,
    val pagination: SessionMessagesPagination? = null,
)

@Serializable
data class SessionMessagesPagination(
    val limit: Int? = null,
    val offset: Int? = null,
    val order: String? = null,
    val returned: Int? = null,
    /** Derived by callers when absent — dashboard omits it today. */
    val hasMore: Boolean? = null,
)

@Serializable
data class SessionMessage(
    val role: String? = null,
    val content: String? = null,
    val timestamp: JsonElement? = null,
    val type: String? = null,
    val reasoning: String? = null,
    val reasoning_content: String? = null,
    val tool_name: String? = null,
    val tool_call_id: String? = null,
    val tool_calls: JsonElement? = null,
) {
    val timestampText: String?
        get() = (timestamp as? JsonPrimitive)?.content

    /** First non-empty reasoning payload (structured field wins over content-embedded). */
    val reasoningText: String?
        get() = reasoning?.takeIf { it.isNotBlank() }
            ?: reasoning_content?.takeIf { it.isNotBlank() }
}
