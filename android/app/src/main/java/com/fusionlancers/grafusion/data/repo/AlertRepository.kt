package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.AmAlert
import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.model.Alert
import com.fusionlancers.grafusion.data.model.AlertState

class AlertRepository(
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    suspend fun fetchAlerts(): Result<List<Alert>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        api.grafanaAlerts(auth).map { it.toUi() }
    }

    private fun AmAlert.toUi(): Alert {
        val name = labels["alertname"] ?: annotations["summary"]?.take(60) ?: "Alert"
        val severity = (labels["severity"] ?: "info").lowercase()
        val silenced = status.silencedBy.isNotEmpty()
        val inhibited = status.inhibitedBy.isNotEmpty()
        val state = when {
            silenced || inhibited -> AlertState.SUPPRESSED
            status.state.equals("active", ignoreCase = true) -> AlertState.FIRING
            status.state.equals("unprocessed", ignoreCase = true) -> AlertState.PENDING
            else -> AlertState.NORMAL
        }
        return Alert(
            fingerprint = fingerprint.orEmpty(),
            name = name,
            summary = annotations["summary"].orEmpty(),
            description = annotations["description"].orEmpty(),
            severity = severity,
            state = state,
            silenced = silenced,
            startsAt = startsAt,
            labels = labels,
            generatorURL = generatorURL,
        )
    }
}
