package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.db.NotificationHistoryDao
import com.fusionlancers.grafusion.data.db.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around NotificationHistoryDao so the messaging service and UI don't need to know
 * about Room. Holds the 30-day retention rule so callers stay ignorant of the policy.
 */
class NotificationHistoryRepository(private val dao: NotificationHistoryDao) {

    private val retentionMillis = 30L * 24 * 60 * 60 * 1000

    fun observe(): Flow<List<NotificationHistoryEntity>> = dao.observe()

    suspend fun record(
        title: String,
        body: String,
        fingerprint: String?,
        alertName: String?,
        severity: String,
    ) {
        val now = System.currentTimeMillis()
        dao.insert(
            NotificationHistoryEntity(
                receivedAt = now,
                title = title,
                body = body,
                fingerprint = fingerprint,
                alertName = alertName,
                severity = severity,
            )
        )
        // Opportunistic prune - keeps the table bounded without a periodic worker.
        dao.prune(now - retentionMillis)
    }

    suspend fun clear() = dao.clear()
}
