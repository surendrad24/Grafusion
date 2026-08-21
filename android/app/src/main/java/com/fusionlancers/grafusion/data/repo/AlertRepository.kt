package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.AmAlert
import com.fusionlancers.grafusion.data.api.GrafanaAnnotation
import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.model.Alert
import com.fusionlancers.grafusion.data.model.AlertState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.format.DateTimeFormatter

class AlertRepository(
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    /**
     * Silence an alert for [durationMinutes] using its labels as an exact-match matcher set.
     * Grafana's Alertmanager compat endpoint expects ISO-8601 startsAt/endsAt and the standard silence body.
     */
    suspend fun silence(alert: Alert, durationMinutes: Long, comment: String, createdBy: String): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val now = Instant.now()
        val end = now.plusSeconds(durationMinutes * 60)
        val iso = DateTimeFormatter.ISO_INSTANT
        val body = buildJsonObject {
            put("matchers", buildJsonArray {
                alert.labels.forEach { (k, v) ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(k))
                        put("value", JsonPrimitive(v))
                        put("isRegex", JsonPrimitive(false))
                        put("isEqual", JsonPrimitive(true))
                    })
                }
            })
            put("startsAt", JsonPrimitive(iso.format(now)))
            put("endsAt", JsonPrimitive(iso.format(end)))
            put("createdBy", JsonPrimitive(createdBy))
            put("comment", JsonPrimitive(comment))
        }
        val resp = api.createSilence(auth, body)
        if (!resp.isSuccessful) {
            val hint = when (resp.code()) {
                401, 403 -> "your Grafana user lacks alert-silence permission (Editor or Admin required)"
                404 -> "silence endpoint not found - Grafana Alertmanager may be disabled"
                else -> "HTTP ${resp.code()}"
            }
            error(hint)
        }
    }

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

    /**
     * Fetch dashboard annotations. When [dashboardUid] is provided the server only returns
     * annotations tied to that dashboard; otherwise it returns everything the user can see,
     * capped by [limit]. Used by the annolist panel.
     */
    suspend fun annotations(
        dashboardUid: String? = null,
        limit: Int = 100,
        tags: List<String> = emptyList(),
    ): Result<List<GrafanaAnnotation>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        // The Grafana endpoint accepts repeated tags[]= params; we can't easily wire that through
        // Retrofit's @Query for a variable list, so we filter client-side after fetching.
        val all = api.listAnnotations(auth, limit = limit, dashboardUid = dashboardUid)
        if (tags.isEmpty()) all
        else all.filter { ann -> ann.tags.any { it in tags } }
    }
}
