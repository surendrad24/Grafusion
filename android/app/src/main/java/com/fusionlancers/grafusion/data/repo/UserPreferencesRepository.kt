package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.api.UserPreferences
import com.fusionlancers.grafusion.data.prefs.ThemeMode
import com.fusionlancers.grafusion.data.prefs.ThemePreferences

/**
 * Pulls the active account's Grafana user preferences (theme + home dashboard)
 * and mirrors them into local SharedPreferences so the app UI (theme mode, initial
 * dashboard) matches what the user configured in Grafana web.
 */
class UserPreferencesRepository(
    private val accountRepository: AccountRepository,
    private val themePreferences: ThemePreferences,
    private val apiFactory: GrafanaApiFactory,
) {

    suspend fun sync(): Result<UserPreferences> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val prefs = api.userPreferences(auth)

        // Grafana returns "light" / "dark" / "" (blank = follow org default / system).
        val mode = when (prefs.theme?.lowercase()) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
        themePreferences.set(mode)
        themePreferences.setHomeDashboardUid(prefs.homeDashboardUID)
        prefs
    }
}
