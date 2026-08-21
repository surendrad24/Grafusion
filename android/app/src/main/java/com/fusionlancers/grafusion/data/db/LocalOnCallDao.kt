package com.fusionlancers.grafusion.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalOnCallDao {

    // ---- Schedules ----

    @Query("SELECT * FROM local_schedule ORDER BY createdAt ASC")
    fun observeSchedules(): Flow<List<LocalScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: LocalScheduleEntity): Long

    @Query("DELETE FROM local_schedule WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    // ---- Shifts ----

    @Query("SELECT * FROM local_shift WHERE scheduleId = :scheduleId ORDER BY startsAtMs ASC")
    fun observeShifts(scheduleId: Long): Flow<List<LocalShiftEntity>>

    /** All shifts, used by [LocalOnCallRepository.observeAll] so the screen renders in one Flow. */
    @Query("SELECT * FROM local_shift ORDER BY startsAtMs ASC")
    fun observeAllShifts(): Flow<List<LocalShiftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShift(shift: LocalShiftEntity): Long

    @Update
    suspend fun updateShift(shift: LocalShiftEntity)

    @Delete
    suspend fun deleteShift(shift: LocalShiftEntity)

    @Query("DELETE FROM local_shift WHERE scheduleId = :scheduleId")
    suspend fun deleteShiftsFor(scheduleId: Long)
}
