package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.prefs.NotificationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Registers this device with the Go relay so Grafana webhook alerts can fan out to it.
 *
 * The relay's /v1/devices endpoint expects {fcm_token, grafana_url, grafana_user}. When
 * google-services.json is present GrafusionMessagingService stores a real FCM token in
 * NotificationPreferences; otherwise we fall back to the stable device_id stub. Either
 * way the relay treats the field as an opaque routing key.
 */
class NotificationsRepository(
    private val accountRepository: AccountRepository,
    private val notificationPreferences: NotificationPreferences,
    private val apiFactory: GrafanaApiFactory,
) {

    /** POST /v1/devices with the current account + device identifier. */
    suspend fun registerCurrentDevice(): Result<Unit> = runCatching {
        val config = notificationPreferences.current()
        if (config.relayUrl.isBlank()) error("Set the relay URL first")
        val entity = accountRepository.activeEntity() ?: error("Sign in to a Grafana account first")

        val body = """{"fcm_token":${q(config.routingToken)},"grafana_url":${q(entity.grafanaUrl)},"grafana_user":${q(entity.login)}}"""
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(config.relayUrl.trimEnd('/') + "/v1/devices")
            .post(body)
            .build()

        withContext(Dispatchers.IO) {
            apiFactory.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            }
        }
    }.also { r ->
        notificationPreferences.recordRegistration(r.isSuccess, r.exceptionOrNull()?.message)
    }

    /** Round-trip a synthetic Grafana webhook through the relay so the user can verify FCM delivery. */
    suspend fun sendTestWebhook(): Result<Unit> = runCatching {
        val config = notificationPreferences.current()
        if (config.relayUrl.isBlank()) error("Set the relay URL first")
        val entity = accountRepository.activeEntity() ?: error("Sign in to a Grafana account first")

        val body = """
            {"receiver":"grafusion-test","status":"firing",
             "groupLabels":{"alertname":"Grafusion test alert"},
             "externalURL":${q(entity.grafanaUrl)},
             "alerts":[{
               "status":"firing",
               "labels":{"alertname":"Grafusion test alert","severity":"info"},
               "annotations":{"summary":"Hello from the Grafusion app"},
               "startsAt":"1970-01-01T00:00:00Z","endsAt":"1970-01-01T00:00:00Z",
               "generatorURL":${q(entity.grafanaUrl)},"fingerprint":"grafusion-test"
             }]}
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(config.relayUrl.trimEnd('/') + "/v1/webhook/grafana")
            .post(body)
            .build()

        withContext(Dispatchers.IO) {
            apiFactory.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            }
        }
    }

    private fun q(s: String): String = buildString {
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
}
