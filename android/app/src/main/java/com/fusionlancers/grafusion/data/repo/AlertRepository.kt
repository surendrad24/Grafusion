package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.AmAlert
import com.fusionlancers.grafusion.data.api.AmSilence
import com.fusionlancers.grafusion.data.api.GrafanaAnnotation
import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.api.GrafanaRule
import com.fusionlancers.grafusion.data.api.GrafanaRuleGroup
import com.fusionlancers.grafusion.data.model.Alert
import com.fusionlancers.grafusion.data.model.AlertState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.format.DateTimeFormatter

/** UI-side row for the rule inspector: rule + the folder/group it lives in. */
data class AlertRuleRow(val folder: String, val group: String, val rule: GrafanaRule) {
    fun displayName(): String =
        rule.grafanaAlert?.title?.takeIf { it.isNotBlank() }
            ?: rule.alert?.takeIf { it.isNotBlank() }
            ?: rule.record?.takeIf { it.isNotBlank() }
            ?: "Untitled rule"

    fun uid(): String = rule.grafanaAlert?.uid.orEmpty()

    /** True for classic Prometheus/Loki recording rules, which have no alert condition. */
    fun isRecording(): Boolean = rule.record != null && rule.alert == null && rule.grafanaAlert == null
}

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

    /**
     * "Acknowledge" isn't a first-class Alertmanager concept - conventionally it's a short silence
     * with an "Acknowledged" comment so downstream folks see who took ownership. We use 4 hours by
     * default which is Grafana's own web-UI ack window.
     */
    suspend fun acknowledge(alert: Alert, by: String): Result<Unit> =
        silence(alert, durationMinutes = 240, comment = "Acknowledged by $by (Grafusion mobile)", createdBy = by)

    /**
     * Create a silence from a raw matcher list. Used by the standalone Silences screen where the
     * user hand-authors matchers instead of copying them off an alert. Same body shape as
     * [silence] - just decoupled from the [Alert] type.
     */
    suspend fun silenceByMatchers(
        matchers: List<Triple<String, String, Boolean>>,
        durationMinutes: Long,
        comment: String,
        createdBy: String,
    ): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val now = Instant.now()
        val end = now.plusSeconds(durationMinutes * 60)
        val iso = DateTimeFormatter.ISO_INSTANT
        val body = buildJsonObject {
            put("matchers", buildJsonArray {
                matchers.forEach { (name, value, isRegex) ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(name))
                        put("value", JsonPrimitive(value))
                        put("isRegex", JsonPrimitive(isRegex))
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

    /** List active + pending silences so the alert sheet can show them and let the user expire one. */
    suspend fun listSilences(): Result<List<AmSilence>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        api.listSilences(auth).filter { it.status.state != "expired" }
    }

    /**
     * Expire (unmute) an in-flight silence. Grafana returns 200 on success and 404 when the ID has
     * already been expired by another user; we treat both as success so the UI stays consistent.
     */
    suspend fun expireSilence(silenceId: String): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.expireSilence(auth, silenceId)
        if (!resp.isSuccessful && resp.code() != 404) {
            val hint = when (resp.code()) {
                401, 403 -> "your Grafana user lacks alert-silence permission"
                else -> "HTTP ${resp.code()}"
            }
            error(hint)
        }
    }

    /**
     * Match a set of silences against an alert's labels. A silence is "for" the alert when *all*
     * of its matchers pass against the alert's label set.
     */
    fun silencesFor(alert: Alert, silences: List<AmSilence>): List<AmSilence> {
        return silences.filter { s ->
            s.matchers.isNotEmpty() && s.matchers.all { m ->
                val v = alert.labels[m.name] ?: return@all false
                val matches = if (m.isRegex) runCatching { Regex(m.value).matches(v) }.getOrDefault(false)
                              else v == m.value
                if (m.isEqual) matches else !matches
            }
        }
    }

    /**
     * List all Grafana Managed alert rules the current user can see, flattened from the Ruler
     * API's `{namespace: [groups]}` shape into one entry per rule with its folder/group already
     * resolved. Returns an empty list when Grafana Alerting is disabled or the endpoint 404s -
     * the inspector screen renders that as "no rules" rather than an error to avoid alarming
     * users on plain data-source-managed installs.
     */
    suspend fun listAlertRules(): Result<List<AlertRuleRow>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.listGrafanaAlertRules(auth)
        if (!resp.isSuccessful) {
            if (resp.code() == 404) return@runCatching emptyList()
            val hint = when (resp.code()) {
                401, 403 -> "your Grafana user lacks alert-rule read permission"
                else -> "HTTP ${resp.code()}"
            }
            error(hint)
        }
        val body = resp.body().orEmpty()
        buildList {
            body.forEach { (namespace, groups) ->
                groups.forEach { group ->
                    group.rules.forEach { rule ->
                        add(AlertRuleRow(folder = namespace, group = group.name, rule = rule))
                    }
                }
            }
        }.sortedWith(compareBy({ it.folder.lowercase() }, { it.displayName().lowercase() }))
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

    /**
     * Create an annotation on the active Grafana. Scoped by [dashboardUid] when provided;
     * scoping to a single [panelId] on top of that pins the marker to one panel instead of
     * the whole dashboard. [tags] doubles as the way Grafana attaches the annotation to
     * dashboard-level tag queries.
     */
    suspend fun createAnnotation(
        text: String,
        tags: List<String> = emptyList(),
        dashboardUid: String? = null,
        panelId: Long? = null,
        time: Long = System.currentTimeMillis(),
        timeEnd: Long? = null,
    ): Result<Long> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val body = com.fusionlancers.grafusion.data.api.CreateAnnotationBody(
            dashboardUID = dashboardUid,
            panelId = panelId,
            time = time,
            timeEnd = timeEnd,
            tags = tags,
            text = text,
        )
        api.createAnnotation(auth, body).id
    }
}
