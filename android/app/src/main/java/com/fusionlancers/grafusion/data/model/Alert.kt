package com.fusionlancers.grafusion.data.model

data class Alert(
    val fingerprint: String,
    val name: String,
    val summary: String,
    val description: String,
    val severity: String,
    val state: AlertState,
    val silenced: Boolean,
    val startsAt: String?,
    val labels: Map<String, String>,
    val generatorURL: String?,
)

enum class AlertState { FIRING, PENDING, NORMAL, SUPPRESSED }
