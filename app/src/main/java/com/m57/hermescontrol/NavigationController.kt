package com.m57.hermescontrol

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Central navigation controller with deduplication guard.
 *
 * Primary screens (bottom nav tabs) clear the back stack and become the new
 * root — this matches Material's bottom-nav pattern where each tab has its own
 * back stack history (simplified: we just clear).
 *
 * B7 (Jun 18 2026): Never call `backStack.add()` directly from UI callbacks.
 * Always route through [navigateTo] to prevent stacking duplicate screen
 * entries that compete for touch events.
 */
object NavigationController {
    var backStack: NavBackStack<NavKey>? = null
    var pendingSessionId: String? = null

    /**
     * Owning profile of [pendingSessionId] when the navigation came from the
     * all-profiles sidebar (agent-roster chip). Consumed by ChatScreen's
     * LaunchedEffect together with the session id — cross-profile sessions
     * 404 on the messages endpoint without `?profile=`.
     */
    var pendingSessionProfile: String? = null

    /**
     * Display title hint for [pendingSessionId] (from the sidebar's
     * all-profiles row). ChatViewModel's session list is single-profile
     * (default), so cross-profile titles can't be resolved there — the
     * hint keeps the chat header correct until a better source exists.
     */
    var pendingSessionTitle: String? = null

    /**
     * Last session the user actually opened, kept after the chat consumes
     * [pendingSessionId]. The sidebar reads this so the "selected" room/chip
     * highlight survives the chat screen clearing the pending id (same role
     * as the desktop's `$focusedStoredSessionId`).
     */
    var lastOpenedSessionId: String? = null

    /**
     * Channel id when the navigation target is a project ROOM (the group
     * chat), not an agent conversation. ChatScreen consumes this and calls
     * [com.m57.hermescontrol.ui.chat.ChatViewModel.openRoom] — the room owns
     * its message store on the backend, so nothing about the session path
     * applies.
     */
    var pendingRoomId: String? = null

    /** Display title for [pendingRoomId] (the project name). */
    var pendingRoomTitle: String? = null

    // Bottom-nav primary screens — dynamic, updated by Navigation.kt via
    // updatePrimaryScreens() when the user customises the bottom nav bar.
    // Default matches the default 5 bottom-nav items.
    private val primaryScreens: MutableSet<NavKey> =
        mutableSetOf(
            ChatScreen,
            SkillsScreen,
            CronJobsScreen,
            SystemScreen,
            SettingsScreen,
        )

    /** Returns whether the given key is a primary (bottom-nav) screen. */
    fun isPrimaryScreen(key: NavKey): Boolean = key in primaryScreens

    /** Replace the primary screen set. Called by Navigation.kt when the
     *  bottom-nav item config changes. */
    fun updatePrimaryScreens(keys: Set<NavKey>) {
        primaryScreens.clear()
        primaryScreens.addAll(keys)
    }

    fun navigateTo(key: NavKey) {
        val stack = backStack ?: return
        if (stack.lastOrNull() == key) return

        if (isPrimaryScreen(key)) {
            stack.clear()
        }
        stack.add(key)
    }

    /** Clear the stack and navigate to the given screen atomically. */
    fun resetTo(screen: NavKey) {
        val stack = backStack ?: return
        stack.clear()
        stack.add(screen)
    }

    /**
     * Navigate back one step, or fall back to [fallback] when the stack has only one item.
     * Never leaves the stack empty.
     */
    fun goBack(fallback: NavKey = ChatScreen) {
        val stack = backStack ?: return
        if (stack.size > 1) {
            stack.removeLastOrNull()
        } else if (stack.size == 1) {
            stack.clear()
            stack.add(fallback)
        }
    }
}
