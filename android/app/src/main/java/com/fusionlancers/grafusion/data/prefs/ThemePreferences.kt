package com.fusionlancers.grafusion.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class ThemeMode { LIGHT, DARK, AUTO }

class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("grafusion-prefs", Context.MODE_PRIVATE)

    fun current(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name) }
            .getOrDefault(ThemeMode.AUTO)

    fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    /** UID of the home dashboard synced from Grafana user preferences (nullable when unset). */
    fun homeDashboardUid(): String? = prefs.getString(KEY_HOME, null)?.takeIf { it.isNotBlank() }
    fun setHomeDashboardUid(uid: String?) {
        prefs.edit().apply {
            if (uid.isNullOrBlank()) remove(KEY_HOME) else putString(KEY_HOME, uid)
        }.apply()
    }

    val flow: Flow<ThemeMode> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == KEY) trySend(current())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val KEY = "theme_mode"
        private const val KEY_HOME = "home_dashboard_uid"
    }
}
