package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.api.GrafanaOrg
import com.fusionlancers.grafusion.data.api.GrafanaServiceAccount
import com.fusionlancers.grafusion.data.api.GrafanaTeam
import com.fusionlancers.grafusion.data.api.OrgUser

/**
 * Read-only listings from Grafana's admin/org endpoints. Each call requires progressively
 * more permission:
 *   - orgUsers: Admin of the current org (or Editor with the "org.users:read" fine-grained perm)
 *   - teams: any signed-in user (returns only the teams they can see)
 *   - serviceAccounts: Editor+ for view, Admin to manage
 *   - orgs: Grafana server admin only (superadmin)
 *
 * We surface HTTP 401/403 as friendly errors ("your Grafana role can't see this") instead
 * of raw stack traces, so a Viewer opening the Admin screen sees a helpful message rather
 * than a crash.
 */
class AdminRepository(
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    private suspend fun <T> call(block: suspend (auth: String, api: com.fusionlancers.grafusion.data.api.GrafanaApi) -> retrofit2.Response<T>): Result<T> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = block(auth, api)
        if (!resp.isSuccessful) {
            val hint = when (resp.code()) {
                401, 403 -> "your Grafana role can't view this list"
                404 -> "endpoint not found on this Grafana"
                else -> "HTTP ${resp.code()}"
            }
            error(hint)
        }
        resp.body() ?: error("empty response")
    }

    suspend fun orgUsers(): Result<List<OrgUser>> =
        call { auth, api -> api.orgUsers(auth) }

    suspend fun teams(): Result<List<GrafanaTeam>> =
        call { auth, api -> api.teams(auth) }.map { it.teams }

    suspend fun serviceAccounts(): Result<List<GrafanaServiceAccount>> =
        call { auth, api -> api.serviceAccounts(auth) }.map { it.serviceAccounts }

    suspend fun orgs(): Result<List<GrafanaOrg>> =
        call { auth, api -> api.orgs(auth) }
}
