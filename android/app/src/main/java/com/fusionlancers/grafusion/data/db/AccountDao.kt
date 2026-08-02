package com.fusionlancers.grafusion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun all(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE active = 1 LIMIT 1")
    fun active(): Flow<AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity): Long

    @Query("UPDATE accounts SET active = (id = :id)")
    suspend fun setActive(id: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}
