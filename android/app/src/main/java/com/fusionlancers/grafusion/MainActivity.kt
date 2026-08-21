package com.fusionlancers.grafusion

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.fusionlancers.grafusion.data.AlertDeepLink
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.prefs.ThemeMode
import com.fusionlancers.grafusion.ui.AppRoot
import com.fusionlancers.grafusion.ui.lock.AppLockGate
import com.fusionlancers.grafusion.ui.theme.GrafusionTheme

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GrafusionApp).container
        publishDeepLink(container, intent)
        setContent {
            val mode by container.themePreferences.flow.collectAsState(initial = ThemeMode.AUTO)
            GrafusionTheme(mode = mode) {
                val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                AppLockGate(container = container) {
                    AppRoot(container = container, windowSizeClass = windowSizeClass)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishDeepLink((application as GrafusionApp).container, intent)
    }

    private fun publishDeepLink(container: AppContainer, intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_ROUTE)?.takeIf { it.isNotBlank() }?.let {
            container.pendingStartRoute.value = it
        }
        // grafana://d/<uid> or grafana://dashboard/<uid> -> jump straight to that dashboard.
        val data = intent?.data
        if (data != null && data.scheme.equals("grafana", ignoreCase = true)) {
            val host = data.host?.lowercase()
            val segments = data.pathSegments.orEmpty()
            if ((host == "d" || host == "dashboard") && segments.isNotEmpty()) {
                val uid = segments[0]
                container.pendingStartRoute.value = "dashboard/$uid?title="
            }
        }
        val fingerprint = intent?.getStringExtra(EXTRA_ALERT_FINGERPRINT)?.takeIf { it.isNotBlank() }
        val name = intent?.getStringExtra(EXTRA_ALERT_NAME)?.takeIf { it.isNotBlank() }
        if (fingerprint == null && name == null) return
        container.pendingAlertDeepLink.value = AlertDeepLink(fingerprint, name)
    }

    companion object {
        const val EXTRA_ALERT_FINGERPRINT = "grafusion.alert.fingerprint"
        const val EXTRA_ALERT_NAME = "grafusion.alert.name"
        const val EXTRA_OPEN_ROUTE = "grafusion.open.route"
    }
}
