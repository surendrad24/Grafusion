package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.db.AccountDao
import com.fusionlancers.grafusion.data.db.AccountEntity
import com.fusionlancers.grafusion.data.db.AuthType
import com.fusionlancers.grafusion.data.model.Account
import com.fusionlancers.grafusion.data.security.TokenVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class AccountRepository(
    private val accountDao: AccountDao,
    private val tokenVault: TokenVault,
    private val apiFactory: GrafanaApiFactory,
) {

    val accounts: Flow<List<Account>> =
        accountDao.all().map { list -> list.map { it.toUi() } }

    val activeAccount: Flow<Account?> =
        accountDao.active().map { it?.toUi() }

    /**
     * Verify credentials against Grafana with HTTP Basic auth by fetching /api/user,
     * then persist the credentials encrypted so future requests can reuse them.
     *
     * Works with every Grafana version and every role (Viewer/Editor/Admin) -
     * unlike POST /api/auth/keys which is Admin-only and removed in Grafana 11.
     */
    suspend fun loginWithPassword(baseUrl: String, username: String, password: String): Result<Unit> = runCatching {
        val normalized = normalizeUrl(baseUrl)
        val api = apiFactory.forBaseUrl(normalized)
        val basic = Credentials.basic(username, password)

        val user = api.currentUser(basic)

        val vaultKey = "acct-${UUID.randomUUID()}"
        // Store the raw Basic header value so we can re-emit it on every request.
        tokenVault.put(vaultKey, basic)

        val id = accountDao.upsert(
            AccountEntity(
                grafanaUrl = normalized,
                userId = user.id,
                login = user.login,
                displayName = user.name ?: user.login,
                active = true,
                authType = AuthType.BASIC.name,
                tokenVaultKey = vaultKey,
            )
        )
        accountDao.setActive(id)
    }

    /**
     * Alternative: user pastes a Grafana Service Account token (recommended for
     * production). Verified against /api/user, then stored as-is.
     */
    suspend fun loginWithToken(baseUrl: String, token: String): Result<Unit> = runCatching {
        val normalized = normalizeUrl(baseUrl)
        val api = apiFactory.forBaseUrl(normalized)
        val bearer = "Bearer $token"

        val user = api.currentUser(bearer)

        val vaultKey = "acct-${UUID.randomUUID()}"
        tokenVault.put(vaultKey, token)

        val id = accountDao.upsert(
            AccountEntity(
                grafanaUrl = normalized,
                userId = user.id,
                login = user.login,
                displayName = user.name ?: user.login,
                active = true,
                authType = AuthType.BEARER.name,
                tokenVaultKey = vaultKey,
            )
        )
        accountDao.setActive(id)
    }

    suspend fun logout(accountId: Long) {
        accountDao.delete(accountId)
    }

    /** Returns the raw Authorization header value for an account, or null if the token/creds are missing. */
    fun authHeaderFor(entity: AccountEntity): String? {
        val secret = tokenVault.get(entity.tokenVaultKey) ?: return null
        return when (AuthType.valueOf(entity.authType)) {
            AuthType.BASIC -> secret // already "Basic <b64>"
            AuthType.BEARER -> "Bearer $secret"
        }
    }

    suspend fun activeEntity(): AccountEntity? = accountDao.active().first()

    /**
     * Establish a browser session with Grafana so a WebView can render dashboards without re-login.
     * For BASIC accounts: POST /login with the decoded credentials, capture grafana_session cookie.
     * For BEARER accounts: not supported (service tokens don't create browser sessions).
     */
    suspend fun sessionCookieFor(entity: AccountEntity): String? = withContext(Dispatchers.IO) {
        val secret = tokenVault.get(entity.tokenVaultKey) ?: return@withContext null
        if (AuthType.valueOf(entity.authType) != AuthType.BASIC) return@withContext null

        val basicPayload = secret.removePrefix("Basic ").trim()
        val decoded = runCatching {
            String(android.util.Base64.decode(basicPayload, android.util.Base64.NO_WRAP))
        }.getOrNull() ?: return@withContext null
        val colon = decoded.indexOf(':')
        if (colon <= 0) return@withContext null
        val user = decoded.substring(0, colon)
        val pass = decoded.substring(colon + 1)

        val body = """{"user":${jsonString(user)},"password":${jsonString(pass)}}"""
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(entity.grafanaUrl.trimEnd('/') + "/login")
            .post(body)
            .build()

        apiFactory.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.headers("Set-Cookie")
                .firstOrNull { it.startsWith("grafana_session=") }
                ?.substringBefore(';')
        }
    }

    private fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
        append('"')
    }

    private fun AccountEntity.toUi() = Account(
        id = id,
        grafanaUrl = grafanaUrl,
        login = login,
        displayName = displayName,
    )

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }
}
