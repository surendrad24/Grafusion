package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.db.QueryHistoryDao
import com.fusionlancers.grafusion.data.db.QueryHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Local Explore query history. Never leaves the device. Dedupes back-to-back runs of the same
 * query on the same datasource by bumping timestamp; prunes non-starred rows older than 30
 * days on each save so we don't need a background job to keep the table small.
 */
class QueryHistoryRepository(private val dao: QueryHistoryDao) {

    fun observe(): Flow<List<QueryHistoryEntity>> = dao.observe()

    suspend fun record(
        datasourceUid: String?,
        datasourceType: String,
        query: String,
    ) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val touched = dao.bump(dsUid = datasourceUid, query = trimmed, ranAt = now)
        if (touched == 0) {
            dao.insert(
                QueryHistoryEntity(
                    datasourceUid = datasourceUid,
                    datasourceType = datasourceType,
                    query = trimmed,
                    ranAt = now,
                )
            )
        }
        // Cheap opportunistic prune - runs alongside the insert so the cost stays with the
        // action that would grow the table.
        dao.prune(cutoffMillis = now - RETENTION_MILLIS)
    }

    suspend fun toggleStar(entity: QueryHistoryEntity) {
        dao.setStarred(entity.id, !entity.starred)
    }

    suspend fun delete(entity: QueryHistoryEntity) {
        dao.delete(entity.id)
    }

    companion object {
        private val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30)
    }
}
