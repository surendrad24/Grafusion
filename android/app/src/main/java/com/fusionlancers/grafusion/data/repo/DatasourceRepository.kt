package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.Correlation
import com.fusionlancers.grafusion.data.api.Datasource
import com.fusionlancers.grafusion.data.api.DatasourceDetail
import com.fusionlancers.grafusion.data.api.DatasourceHealth
import com.fusionlancers.grafusion.data.api.GrafanaApiFactory

/**
 * Lists datasources and checks each one's health via Grafana's built-in probe endpoint
 * (`GET /api/datasources/uid/{uid}/health`). Grafana returns OK / ERROR / UNKNOWN with
 * a human-readable message that we surface verbatim.
 *
 * The health endpoint is a proxied call to the plugin: some plugins don't implement it
 * (returns 404) and some require Editor+ (returns 403). We map those to UNKNOWN so the
 * row still renders instead of blanking out the whole list.
 */
class DatasourceRepository(
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    data class HealthResult(val datasource: Datasource, val health: DatasourceHealth)

    suspend fun listWithHealth(): Result<List<HealthResult>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)

        val datasources = api.listDatasources(auth)
            .sortedWith(compareByDescending<Datasource> { it.isDefault }.thenBy { it.name })

        datasources.map { ds ->
            val health = runCatching {
                val resp = api.datasourceHealth(auth, ds.uid)
                when {
                    resp.isSuccessful -> resp.body() ?: DatasourceHealth("UNKNOWN", "empty response")
                    resp.code() == 404 -> DatasourceHealth("UNKNOWN", "plugin has no health check")
                    resp.code() == 403 || resp.code() == 401 -> DatasourceHealth("UNKNOWN", "no permission to probe")
                    else -> DatasourceHealth("ERROR", "HTTP ${resp.code()}")
                }
            }.getOrElse { DatasourceHealth("ERROR", it.message.orEmpty()) }
            HealthResult(ds, health)
        }
    }

    suspend fun detail(uid: String): Result<DatasourceDetail> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.datasourceDetail(auth, uid)
        if (!resp.isSuccessful) {
            val hint = when (resp.code()) {
                401, 403 -> "your Grafana user lacks datasource read permission"
                404 -> "datasource not found"
                else -> "HTTP ${resp.code()}"
            }
            error(hint)
        }
        resp.body() ?: error("empty response")
    }

    /** UI row: the raw correlation plus resolved display names for both ends. When the source
     *  or target datasource has been deleted we fall back to the raw UID so users can still
     *  see that the correlation exists (and is broken). */
    data class CorrelationRow(
        val correlation: Correlation,
        val sourceName: String,
        val sourceType: String,
        val targetName: String,
        val targetType: String,
    )

    /**
     * Pulls the correlations page and joins each row against the datasource list so the UI can
     * show human names ("Prom prod → Loki prod") rather than UIDs. 404 = correlations disabled
     * (< Grafana 10) → we surface an empty list rather than raising, matching the rest of the
     * feature-gated repos.
     */
    suspend fun listCorrelations(): Result<List<CorrelationRow>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)

        val resp = api.listCorrelations(auth)
        if (!resp.isSuccessful) {
            if (resp.code() == 404) return@runCatching emptyList<CorrelationRow>()
            val hint = when (resp.code()) {
                401, 403 -> "your Grafana user lacks datasource read permission"
                else -> "HTTP ${resp.code()}"
            }
            error(hint)
        }
        val page = resp.body() ?: return@runCatching emptyList()

        val dsByUid = api.listDatasources(auth).associateBy { it.uid }
        page.correlations
            .sortedWith(compareBy({ dsByUid[it.sourceUID]?.name ?: it.sourceUID }, { it.label }))
            .map { c ->
                val src = dsByUid[c.sourceUID]
                val tgt = dsByUid[c.targetUID]
                CorrelationRow(
                    correlation = c,
                    sourceName = src?.name ?: c.sourceUID,
                    sourceType = src?.type.orEmpty(),
                    targetName = tgt?.name ?: c.targetUID,
                    targetType = tgt?.type.orEmpty(),
                )
            }
    }

    suspend fun probe(uid: String): Result<DatasourceHealth> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.datasourceHealth(auth, uid)
        when {
            resp.isSuccessful -> resp.body() ?: DatasourceHealth("UNKNOWN", "empty response")
            resp.code() == 404 -> DatasourceHealth("UNKNOWN", "plugin has no health check")
            resp.code() == 403 || resp.code() == 401 -> DatasourceHealth("UNKNOWN", "no permission to probe")
            else -> DatasourceHealth("ERROR", "HTTP ${resp.code()}")
        }
    }
}
