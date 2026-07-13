package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionModelCommandTest {
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
