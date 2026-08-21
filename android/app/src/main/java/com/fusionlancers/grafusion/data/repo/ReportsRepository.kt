package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.api.GrafanaReport

/**
 * Grafana Enterprise reports. Endpoints return 404 on OSS builds - we surface a clear
 * "Enterprise only" hint so users don't think it's a bug.
 */
class ReportsRepository(
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    suspend fun list(): Result<List<GrafanaReport>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.reports(auth)
        when {
            resp.isSuccessful -> resp.body().orEmpty()
            resp.code() == 404 -> error("reports need Grafana Enterprise")
            resp.code() == 401 || resp.code() == 403 -> error("your Grafana role can't view reports")
            else -> error("HTTP ${resp.code()}")
        }
    }

    /** Trigger an ad-hoc send of an existing report to its configured recipients. */
    suspend fun send(id: Long): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.sendReport(auth, id)
        if (!resp.isSuccessful) {
            val hint = when (resp.code()) {
                404 -> "reports need Grafana Enterprise"
                401, 403 -> "you can't trigger this report"
                else -> "HTTP ${resp.code()}"
            }
            error(hint)
        }
    }
}
