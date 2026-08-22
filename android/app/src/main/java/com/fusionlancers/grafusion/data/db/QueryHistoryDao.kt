package com.fusionlancers.grafusion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueryHistoryDao {

    @Insert
    suspend fun insert(entity: QueryHistoryEntity): Long

    /**
     * Latest 200 rows, newest first. The Explore history sheet trims further by tab-type,
     * but the cap here prevents unbounded growth if a user runs hundreds of queries a day
     * without ever letting the 30-day prune window elapse.
     */
    @Query("SELECT * FROM query_history ORDER BY ranAt DESC LIMIT 200")
    fun observe(): Flow<List<QueryHistoryEntity>>

    @Query("UPDATE query_history SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("DELETE FROM query_history WHERE id = :id")
    suspend fun delete(id: Long)

    /** Deletes non-starred rows older than the cutoff; starred rows survive. */
    @Query("DELETE FROM query_history WHERE ranAt < :cutoffMillis AND starred = 0")
    suspend fun prune(cutoffMillis: Long)

    /**
     * Dedupe helper: if the user re-runs the same query on the same datasource we prefer to
     * bump the timestamp on the existing row rather than pile up a hundred identical entries.
     * Returns the number of rows touched so the caller can decide whether to insert fresh.
     */
    @Query("UPDATE query_history SET ranAt = :ranAt WHERE datasourceUid IS :dsUid AND query = :query")
    suspend fun bump(dsUid: String?, query: String, ranAt: Long): Int
}
