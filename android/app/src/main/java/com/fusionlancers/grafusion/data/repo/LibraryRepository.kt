package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.api.LibraryElement
import com.fusionlancers.grafusion.data.api.PlaylistDetail
import com.fusionlancers.grafusion.data.api.PlaylistSummary

/**
 * Grafana library panels and playlists. Both endpoints are OSS (not Enterprise) but
 * `api/library-elements` was renamed in Grafana 9 - older instances 404. We surface the
 * 404 with a clearer message so a legacy user knows what's wrong.
 */
class LibraryRepository(
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    suspend fun libraryPanels(): Result<List<LibraryElement>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.libraryElements(auth, kind = 1)
        when {
            resp.isSuccessful -> resp.body()?.result?.elements.orEmpty()
                .sortedBy { it.name.lowercase() }
            resp.code() == 404 -> error("library panels require Grafana 9 or newer")
            resp.code() == 401 || resp.code() == 403 -> error("your Grafana role can't view library panels")
            else -> error("HTTP ${resp.code()}")
        }
    }

    suspend fun playlists(): Result<List<PlaylistSummary>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.playlists(auth)
        when {
            resp.isSuccessful -> resp.body().orEmpty().sortedBy { it.name.lowercase() }
            resp.code() == 401 || resp.code() == 403 -> error("your Grafana role can't view playlists")
            else -> error("HTTP ${resp.code()}")
        }
    }

    suspend fun playlist(uid: String): Result<PlaylistDetail> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.playlist(auth, uid)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("empty response")
    }
}
