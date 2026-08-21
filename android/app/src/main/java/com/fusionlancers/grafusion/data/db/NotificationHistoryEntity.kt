package com.fusionlancers.grafusion.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per push notification the app has received. We keep this local (never synced to a
 * server) so users can review recent pushes even when they had DND on or missed the toast.
 * Retention is capped to 30 days by NotificationHistoryDao.prune() which runs on service events.
 */
@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis at receipt. Used for both display and pruning. */
    val receivedAt: Long,
    /** Alert / notification title as shown in the tray. */
    val title: String,
    /** Body / summary of the notification. */
    val body: String,
    /** Optional Grafana Alertmanager fingerprint to power deep-links back into the sheet. */
    val fingerprint: String? = null,
    /** Alert rule name (from labels.alertname), used as fallback when fingerprint is missing. */
    val alertName: String? = null,
    /** "regular" (info/warning) vs "important" (critical) - drives channel routing. */
    val severity: String = "regular",
)
