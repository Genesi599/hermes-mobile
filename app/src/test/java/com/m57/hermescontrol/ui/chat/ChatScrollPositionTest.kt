package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollPositionTest {
    @Test
    fun longStreamingLastItemAtItsStart_isNotAtBottom() {
        assertFalse(
            isLastItemAtViewportEnd(
                totalItemsCount = 2,
                lastVisibleItemIndex = 1,
                lastItemEndOffset = 1_200,
                viewportEndOffset = 600,
            ),
        )
    }

    @Test
    fun lastItemEndingAtViewportBottom_isAtBottom() {
        assertTrue(
            isLastItemAtViewportEnd(
                totalItemsCount = 2,
                lastVisibleItemIndex = 1,
                lastItemEndOffset = 604,
                viewportEndOffset = 600,
            ),
        )
    }

    @Test
    fun previousItemVisible_isNotAtBottom() {
        assertFalse(
            isLastItemAtViewportEnd(
                totalItemsCount = 3,
                lastVisibleItemIndex = 1,
                lastItemEndOffset = 600,
                viewportEndOffset = 600,
            ),
        )
    }
}
