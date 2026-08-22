package com.fusionlancers.grafusion.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per Explore query the user has run. Kept local (never synced) so the Explore screen
 * can offer a recall-list - typing PromQL/LogQL on a phone keyboard is slow enough that even
 * a modest history saves a lot of retyping. Retention is time-based (30 days) unless the row
 * is starred, in which case it survives pruning.
 */
@Entity(
    tableName = "query_history",
    indices = [Index("ranAt"), Index("datasourceUid")],
)
data class QueryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Datasource UID this query targeted; NULL for the "raw ad-hoc" tab. */
    val datasourceUid: String?,
    /** Grafana datasource type ("prometheus", "loki", "tempo", ...) - drives icon + tab hand-off. */
    val datasourceType: String,
    /** The query text as the user typed it. */
    val query: String,
    /** Epoch millis when the run was submitted. */
    val ranAt: Long,
    /** Starred rows survive time-based pruning so power users can pin canonical queries. */
    val starred: Boolean = false,
)
