package com.fusionlancers.grafusion.data.model

/** UI-facing snapshot of a Grafana OnCall schedule + who is currently paged. */
data class ScheduleSnapshot(
    val id: String,
    val name: String,
    val currentOnCall: List<String>,
    val upcoming: List<UpcomingShift>,
)

data class UpcomingShift(
    val user: String,
    val startsAt: String?,
    val endsAt: String?,
    val isOverride: Boolean,
)

enum class IncidentState { FIRING, ACKNOWLEDGED, RESOLVED, SILENCED }

data class Incident(
    val id: String,
    val title: String,
    val integration: String?,
    val alertsCount: Int,
    val state: IncidentState,
    val createdAt: String?,
    val webUrl: String?,
)
