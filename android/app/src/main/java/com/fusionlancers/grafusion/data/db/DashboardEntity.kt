package com.fusionlancers.grafusion.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboards", primaryKeys = ["accountId", "uid"])
data class DashboardEntity(
    val accountId: Long,
    val uid: String,
    val title: String,
    val folderTitle: String? = null,
    val folderUid: String? = null,
    val tags: String = "",
    /** Grafana numeric id — required by /api/user/stars/dashboard/{id}. */
    val dashboardId: Long? = null,
    val isStarred: Boolean = false,
    /** Cached raw JSON of the full dashboard for offline viewing. */
    val detailJson: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
