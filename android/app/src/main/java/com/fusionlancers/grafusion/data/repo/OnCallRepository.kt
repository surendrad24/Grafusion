package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.api.OnCallAlertGroup
import com.fusionlancers.grafusion.data.api.OnCallSchedule
import com.fusionlancers.grafusion.data.api.OnCallShift
import com.fusionlancers.grafusion.data.model.Incident
import com.fusionlancers.grafusion.data.model.IncidentState
import com.fusionlancers.grafusion.data.model.ScheduleSnapshot
import com.fusionlancers.grafusion.data.model.UpcomingShift
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Wraps the Grafana OnCall plugin API. OnCall is an optional plugin so every call
 * returns Result<> and translates 404 into a friendly "not installed" error rather
 * than crashing the screen. All endpoints go through the same account auth header
 * as the rest of the app - no separate OnCall token needed.
 */
class OnCallRepository(
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    suspend fun fetchSchedules(): Result<List<ScheduleSnapshot>> = runCatching {
        val (auth, api) = authAndApi()
        val schedulesResp = api.onCallSchedules(auth)
        translateOnCallFailure(schedulesResp)
        val schedules = schedulesResp.body()?.results.orEmpty()

        val today = LocalDate.now(ZoneOffset.UTC)
        val start = today.atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = start.plusSeconds(7L * 24 * 3600)
        val df = DateTimeFormatter.ISO_INSTANT

        schedules.map { schedule ->
            val shiftsResp = runCatching {
                api.onCallFinalShifts(auth, schedule.id, df.format(start), df.format(end))
            }.getOrNull()
            val shifts = shiftsResp?.body().orEmpty()
            schedule.toSnapshot(shifts)
        }
    }

    suspend fun fetchIncidents(): Result<List<Incident>> = runCatching {
        val (auth, api) = authAndApi()
        val resp = api.onCallAlertGroups(auth, state = "firing")
        translateOnCallFailure(resp)
        resp.body()?.results.orEmpty().map { it.toIncident() }
    }

    suspend fun acknowledge(incidentId: String): Result<Unit> = runCatching {
        val (auth, api) = authAndApi()
        val resp = api.onCallAcknowledge(auth, incidentId)
        translateOnCallFailure(resp)
    }

    suspend fun resolve(incidentId: String): Result<Unit> = runCatching {
        val (auth, api) = authAndApi()
        val resp = api.onCallResolve(auth, incidentId)
        translateOnCallFailure(resp)
    }

    private suspend fun authAndApi(): Pair<String, com.fusionlancers.grafusion.data.api.GrafanaApi> {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        return auth to apiFactory.forBaseUrl(entity.grafanaUrl)
    }

    private fun translateOnCallFailure(resp: Response<*>) {
        if (resp.isSuccessful) return
        when (resp.code()) {
            404 -> error("Grafana OnCall plugin is not installed on this instance")
            401, 403 -> error("Your Grafana user lacks OnCall permissions")
            else -> error("HTTP ${resp.code()}: ${resp.message()}")
        }
    }

    private fun OnCallSchedule.toSnapshot(shifts: List<OnCallShift>): ScheduleSnapshot {
        val nowStr = onCallNow.mapNotNull { it.username ?: it.email }
        val nowInstant = Instant.now()
        val upcoming = shifts
            .asSequence()
            .filter { !it.isGap }
            .mapNotNull { shift ->
                val end = shift.shiftEnd?.let { runCatching { Instant.parse(it) }.getOrNull() }
                if (end != null && end.isBefore(nowInstant)) return@mapNotNull null
                val user = shift.users.firstOrNull()?.let { it.username ?: it.email } ?: return@mapNotNull null
                UpcomingShift(
                    user = user,
                    startsAt = shift.shiftStart,
                    endsAt = shift.shiftEnd,
                    isOverride = shift.isOverride,
                )
            }
            .take(6)
            .toList()
        return ScheduleSnapshot(
            id = id,
            name = name.ifBlank { "Schedule $id" },
            currentOnCall = nowStr,
            upcoming = upcoming,
        )
    }

    private fun OnCallAlertGroup.toIncident(): Incident {
        val state = when {
            resolved -> IncidentState.RESOLVED
            acknowledged -> IncidentState.ACKNOWLEDGED
            silenced -> IncidentState.SILENCED
            else -> IncidentState.FIRING
        }
        val title = title
            ?: renderForWeb?.get("title")?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
            ?: "Incident $pk"
        return Incident(
            id = pk,
            title = title,
            integration = integrationName,
            alertsCount = alertsCount,
            state = state,
            createdAt = createdAt,
            webUrl = permalinks?.web,
        )
    }
}
