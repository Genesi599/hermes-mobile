package com.m57.hermescontrol.data.model
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class CronJobRepeat(
    val times: Int? = null,
    val completed: Int = 0,
)

@Serializable
data class CronJob(
    val id: String,
    val name: String,
    val schedule: JsonElement? = null,
    val state: String? = null,
    val last_run_status: String? = null,
    val next_run: String? = null,
    val schedule_display: String? = null,
    val last_status: String? = null,
    val next_run_at: String? = null,
    val last_run_at: String? = null,
    val last_error: String? = null,
    val last_delivery_error: String? = null,
    // Full editor fields — all optional with defaults for backward compat
    val enabled: Boolean? = null,
    val prompt: String? = null,
    val deliver: String? = null,
    val skills: List<String>? = null,
    val model: String? = null,
    val provider: String? = null,
    val base_url: String? = null,
    val script: String? = null,
    val context_from: List<String>? = null,
    val enabled_toolsets: List<String>? = null,
    val workdir: String? = null,
    val no_agent: Boolean? = null,
    val repeat: CronJobRepeat? = null,
    // Producer-label fields for the agent-roster sidebar (2026-09-22 Sync App
    // alignment): when a job's output is delivered into a conversation, these
    // tell the sidebar which chip to draw under the parent session row and
    // where that chip's click should land. Nullable defaults preserve backward
    // compat with older job records on disk.
    val attach_to_session: Boolean? = null,
    val target_session_id: String? = null,
    val agent_label: String? = null,
    val agent_avatar: String? = null,
    val agent_profile: String? = null,
) {
    val scheduleText: String
        get() =
            when (schedule) {
                is JsonPrimitive -> schedule.content
                is JsonObject -> (schedule["display"] as? JsonPrimitive)?.content ?: schedule_display ?: ""
                else -> schedule_display ?: ""
            }

    val lastRunStatus: String
        get() = last_run_status ?: last_status ?: ""

    val nextRunTime: String
        get() = next_run ?: next_run_at ?: ""
}