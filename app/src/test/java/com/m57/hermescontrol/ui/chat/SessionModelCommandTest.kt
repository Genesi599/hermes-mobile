package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionModelCommandTest {
    @Test
    fun shouldQueuePrompt_queuesRegularTextDuringActiveTurnOrApproval() {
        assertTrue(shouldQueuePrompt("next task", isAgentTyping = true, hasPendingApproval = false))
        assertTrue(shouldQueuePrompt("next task", isAgentTyping = false, hasPendingApproval = true))
    }

    @Test
    fun shouldQueuePrompt_keepsSlashCommandsImmediateAndIdlePromptsDirect() {
        assertFalse(shouldQueuePrompt("/stop", isAgentTyping = true, hasPendingApproval = true))
        assertFalse(shouldQueuePrompt("next task", isAgentTyping = false, hasPendingApproval = false))
    }

    @Test
    fun queueCommand_defersPromptWithoutInterruptingCurrentTurn() {
        assertEquals(
            "/queue continue after the approval",
            queueCommand("continue after the approval"),
        )
    }

    @Test
    fun sessionModelCommand_keepsSwitchScopedToCurrentSession() {
        assertEquals(
            "/model gpt-5.6-terra --provider openai-codex --session",
            sessionModelCommand(
                provider = "openai-codex",
                model = "gpt-5.6-terra",
            ),
        )
    }
}
