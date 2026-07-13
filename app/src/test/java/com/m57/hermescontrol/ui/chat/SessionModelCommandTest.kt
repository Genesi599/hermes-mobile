package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionModelCommandTest {
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
