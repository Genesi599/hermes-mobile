package com.m57.hermescontrol.data.config

fun ServerStoreState.addOrReplaceServer(profile: ConnectionProfile): ServerStoreState {
    val updated = connectionProfiles.filter { it.id != profile.id } + profile
    return copy(connectionProfiles = updated)
}

fun ServerStoreState.removeServer(id: String): ServerStoreState {
    val updated = connectionProfiles.filter { it.id != id }
    val newSelected = if (selectedProfileId == id) null else selectedProfileId
    return copy(connectionProfiles = updated, selectedProfileId = newSelected)
}

fun ServerStoreState.switchToServer(id: String?): ServerStoreState = copy(selectedProfileId = id)

fun ServerStoreState.selfHealed(): ServerStoreState {
    val hasActive = connectionProfiles.any { it.id == selectedProfileId }
    val newSelected = if (selectedProfileId != null && !hasActive) null else selectedProfileId

    val validItems = bottomNavItems.filter { it.isNotBlank() }
    // Migration: the shipped default used to be the 5-tab layout
    // (Chat/Skills/Cron/System/Settings). Users who never customized it get
    // migrated to the 3-tab layout (History/Chat/Settings) so the sidebar
    // (session rooms) becomes the primary surface; custom selections survive.
    val legacyDefault =
        listOf("ChatScreen", "SkillsScreen", "CronJobsScreen", "SystemScreen", "SettingsScreen")
    val finalBottomNavItems =
        when {
            validItems.isEmpty() || validItems == legacyDefault ->
                listOf("HistoryScreen", "ChatScreen", "SettingsScreen")
            else -> validItems
        }

    return copy(
        selectedProfileId = newSelected,
        bottomNavItems = finalBottomNavItems,
    )
}

val ServerStoreState.resolvedHost: String
    get() {
        val selected = connectionProfiles.firstOrNull { it.id == selectedProfileId }
        return selected?.host ?: host
    }

val ServerStoreState.resolvedPort: Int
    get() {
        val selected = connectionProfiles.firstOrNull { it.id == selectedProfileId }
        return selected?.port ?: port
    }

/**
 * Per-profile TLS flag.
 *
 * ConnectionProfile gained an optional useTls flag (defaults false = legacy http).
 * resolvedUseTls keeps old serialized states working: missing flag falls back to false.
 */
val ServerStoreState.resolvedUseTls: Boolean
    get() = connectionProfiles.firstOrNull { it.id == selectedProfileId }?.useTls ?: false
