package com.fusionlancers.grafusion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fusionlancers.grafusion.data.prefs.ThemeMode
import com.fusionlancers.grafusion.ui.AppRoot
import com.fusionlancers.grafusion.ui.theme.GrafusionTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GrafusionApp).container
        setContent {
            val mode by container.themePreferences.flow.collectAsState(initial = ThemeMode.AUTO)
            GrafusionTheme(mode = mode) {
                val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                AppRoot(container = container, windowSizeClass = windowSizeClass)
            }
        }
    }
}
