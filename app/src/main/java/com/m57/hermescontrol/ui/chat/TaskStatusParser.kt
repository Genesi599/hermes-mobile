package com.m57.hermescontrol.ui.chat

import kotlinx.serialization.json.Json

/**
 * Parses the trailing [HERMES_TASK_STATUS]{...}[/HERMES_TASK_STATUS] block the model
 * appends to each assistant turn (same contract as the desktop's
 * apps/desktop/src/lib/task-status.ts). Mobile keeps it simple: one data class,
 * no per-field validation beyond "JSON object with string fields".
 */
data class TaskStatus(
    val background: String,
    val progress: String,
    val next: String,
    val skip: Boolean = false,
)

object TaskStatusParser {
    private const val OPEN = "[HERMES_TASK_STATUS]"
    private const val CLOSE = "[/HERMES_TASK_STATUS]"

    private val json = Json { ignoreUnknownKeys = true }

    /** Extract the first task-status block from a message body, or null. */
    fun parse(content: String): TaskStatus? {
        if (!content.contains(OPEN)) return null
        val start = content.indexOf(OPEN) + OPEN.length
        val end = content.indexOf(CLOSE, start)
        if (end < 0) return null
        val raw = content.substring(start, end).trim()
        return try {
            val obj = json.parseToJsonElement(raw).let { it as? kotlinx.serialization.json.JsonObject } ?: return null
            val background = (obj["background"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
            val progress = (obj["progress"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
            val next = (obj["next"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
            if (background.isEmpty() && progress.isEmpty() && next.isEmpty()) return null
            TaskStatus(
                background = background,
                progress = progress,
                next = next,
                skip = (obj["skip"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true",
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Remove every task-status block from a message body (used before rendering). */
    fun strip(content: String): String {
        if (!content.contains(OPEN)) return content
        var out = content
        while (true) {
            val s = out.indexOf(OPEN)
            if (s < 0) break
            val e = out.indexOf(CLOSE, s + OPEN.length)
            if (e < 0) break
            out = out.removeRange(s, e + CLOSE.length)
        }
        return out.trim()
    }
}
