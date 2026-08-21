package com.fusionlancers.grafusion.data.prefs

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

data class NotificationConfig(
    val relayUrl: String,
    val deviceId: String,
    val fcmToken: String?,
    val lastRegisteredAt: Long,
    val lastRegisterOk: Boolean,
    val lastRegisterError: String?,
) {
    /** The routing key sent to the relay: real FCM token when present, device ID stub otherwise. */
    val routingToken: String get() = fcmToken?.takeIf { it.isNotBlank() } ?: deviceId
}

class NotificationPreferences(context: Context) {

    private val app = context.applicationContext
    private val prefs: SharedPreferences =
        app.getSharedPreferences("grafusion-notifications", Context.MODE_PRIVATE)

    fun current(): NotificationConfig = NotificationConfig(
        relayUrl = prefs.getString(KEY_RELAY, "").orEmpty(),
        deviceId = deviceId(),
        fcmToken = prefs.getString(KEY_FCM_TOKEN, null),
        lastRegisteredAt = prefs.getLong(KEY_LAST_AT, 0L),
        lastRegisterOk = prefs.getBoolean(KEY_LAST_OK, false),
        lastRegisterError = prefs.getString(KEY_LAST_ERR, null),
    )

    fun setRelayUrl(url: String) {
        prefs.edit().putString(KEY_RELAY, url.trim().trimEnd('/')).apply()
    }

    fun setFcmToken(token: String?) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun recordRegistration(ok: Boolean, error: String?) {
        prefs.edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putBoolean(KEY_LAST_OK, ok)
            .putString(KEY_LAST_ERR, error)
            .apply()
    }

    /**
     * Stable per-install identifier used as the "FCM token" placeholder until Firebase is provisioned.
     * Derived from ANDROID_ID + a persisted UUID so it survives app updates but rotates on install.
     */
    @SuppressLint("HardwareIds")
    private fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val android = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val id = "grafusion-${android.take(12)}-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    val flow: Flow<NotificationConfig> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(current()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val KEY_RELAY = "relay_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_LAST_AT = "last_register_at"
        private const val KEY_LAST_OK = "last_register_ok"
        private const val KEY_LAST_ERR = "last_register_err"
    }
}
