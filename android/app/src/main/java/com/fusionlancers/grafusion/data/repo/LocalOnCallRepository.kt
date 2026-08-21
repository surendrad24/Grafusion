package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.db.LocalOnCallDao
import com.fusionlancers.grafusion.data.db.LocalScheduleEntity
import com.fusionlancers.grafusion.data.db.LocalShiftEntity
import com.fusionlancers.grafusion.data.model.ScheduleSnapshot
import com.fusionlancers.grafusion.data.model.UpcomingShift
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Local, plugin-free on-call rotation. Emits [ScheduleSnapshot]s so the UI can share the same
 * card layout as the Grafana OnCall path - only the source of truth differs.
 *
 * "Current on-call" is any shift whose window contains `now`; "upcoming" is the next
 * few shifts sorted by start time. No rotation math (weekly repeats etc.) yet - the
 * user hand-authors each shift.
 */
class LocalOnCallRepository(
    private val dao: LocalOnCallDao,
) {

    /** Snapshot the UI subscribes to. Combines schedules + shifts and projects the same
     *  [ScheduleSnapshot] type used by the Grafana OnCall path so the screen only has to
     *  render one shape. */
    fun observe(nowMs: () -> Long = { System.currentTimeMillis() }): Flow<List<ScheduleSnapshot>> =
        combine(dao.observeSchedules(), dao.observeAllShifts()) { schedules, shifts ->
            val now = nowMs()
            schedules.map { sched ->
                val mine = shifts.filter { it.scheduleId == sched.id }
                val current = mine
                    .filter { it.startsAtMs <= now && now < it.endsAtMs }
                    .map { it.user }
                val upcoming = mine
                    .filter { it.startsAtMs > now }
                    .sortedBy { it.startsAtMs }
                    .take(6)
                    .map { it.toUpcoming() }
                ScheduleSnapshot(
                    id = "local-${sched.id}",
                    name = sched.name,
                    currentOnCall = current,
                    upcoming = upcoming,
                )
            }
        }

    suspend fun createSchedule(name: String): Long =
        dao.upsertSchedule(LocalScheduleEntity(name = name.ifBlank { "Untitled" }))

    suspend fun deleteSchedule(id: Long) = dao.deleteSchedule(id)

    suspend fun addShift(scheduleId: Long, user: String, startsAtMs: Long, endsAtMs: Long): Long =
        dao.upsertShift(
            LocalShiftEntity(
                scheduleId = scheduleId,
                user = user.ifBlank { "unassigned" },
                startsAtMs = startsAtMs,
                endsAtMs = endsAtMs,
            )
        )

    suspend fun deleteShift(shift: LocalShiftEntity) = dao.deleteShift(shift)

    fun observeShifts(scheduleId: Long): Flow<List<LocalShiftEntity>> = dao.observeShifts(scheduleId)

    fun observeSchedules(): Flow<List<LocalScheduleEntity>> = dao.observeSchedules()

    private fun LocalShiftEntity.toUpcoming(): UpcomingShift = UpcomingShift(
        user = user,
        startsAt = ISO.format(Instant.ofEpochMilli(startsAtMs)),
        endsAt = ISO.format(Instant.ofEpochMilli(endsAtMs)),
        isOverride = false,
    )

    private companion object {
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
    }
}
