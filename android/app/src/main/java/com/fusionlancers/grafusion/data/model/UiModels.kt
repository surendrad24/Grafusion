package com.fusionlancers.grafusion.data.model

/** UI-facing account model (no token, no DB coupling). */
data class Account(
    val id: Long,
    val grafanaUrl: String,
    val login: String,
    val displayName: String,
)

/** UI-facing dashboard summary. */
data class Dashboard(
    val uid: String,
    val title: String,
    val folderTitle: String? = null,
    val tags: List<String> = emptyList(),
    val cachedOffline: Boolean = false,
    val dashboardId: Long? = null,
    val isStarred: Boolean = false,
)
