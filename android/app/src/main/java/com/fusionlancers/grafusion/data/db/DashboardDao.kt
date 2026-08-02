package com.fusionlancers.grafusion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboards WHERE accountId = :accountId ORDER BY folderTitle, title")
    fun forAccount(accountId: Long): Flow<List<DashboardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(dashboards: List<DashboardEntity>)

    @Query("UPDATE dashboards SET detailJson = :json, updatedAt = :ts WHERE accountId = :accountId AND uid = :uid")
    suspend fun updateDetail(accountId: Long, uid: String, json: String, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM dashboards WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: Long)
}
