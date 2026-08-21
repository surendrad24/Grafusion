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
        val fingerprint = intent?.getStringExtra(EXTRA_ALERT_FINGERPRINT)?.takeIf { it.isNotBlank() }
        val name = intent?.getStringExtra(EXTRA_ALERT_NAME)?.takeIf { it.isNotBlank() }
        if (fingerprint == null && name == null) return
        container.pendingAlertDeepLink.value = AlertDeepLink(fingerprint, name)
    }

    companion object {
        const val EXTRA_ALERT_FINGERPRINT = "grafusion.alert.fingerprint"
        const val EXTRA_ALERT_NAME = "grafusion.alert.name"
    }
}
