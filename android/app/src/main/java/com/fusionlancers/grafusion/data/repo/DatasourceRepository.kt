package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.Datasource
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
}
