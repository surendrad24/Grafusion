package com.fusionlancers.grafusion.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-defined on-call rotation stored locally. This is the fallback when the Grafana OnCall
 * plugin isn't installed on the target instance: the mobile app is the source of truth for
 * "who is on-call now" and the operator can hand-craft rotations without any server support.
 */
@Entity(tableName = "local_schedule")
data class LocalScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Epoch millis at creation for stable ordering. */
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "local_shift",
    foreignKeys = [
        ForeignKey(
            entity = LocalScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduleId")],
)
data class LocalShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    /** Display name shown in the shift row - free text so it works for both single humans and squads. */
    val user: String,
    /** Epoch millis when this person goes on call. */
    val startsAtMs: Long,
    /** Epoch millis when this person goes off call. */
    val endsAtMs: Long,
)
