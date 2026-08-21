package com.fusionlancers.grafusion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {

    @Insert
    suspend fun insert(entity: NotificationHistoryEntity): Long

    @Query("SELECT * FROM notification_history ORDER BY receivedAt DESC LIMIT 500")
    fun observe(): Flow<List<NotificationHistoryEntity>>

    /** Delete rows older than the given epoch millis. Called opportunistically from the service. */
    @Query("DELETE FROM notification_history WHERE receivedAt < :cutoffMillis")
    suspend fun prune(cutoffMillis: Long)

    @Query("DELETE FROM notification_history")
    suspend fun clear()
}
