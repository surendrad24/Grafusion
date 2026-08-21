package com.fusionlancers.grafusion.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    // 5 tabs on a phone means each label gets ~2 inch of width. "Dashboards" wrapped
    // to two lines on 1080p devices, so we shorten to "Home" to match how most
    // mobile ops apps (Grafana Cloud Mobile, PagerDuty, Datadog) label the landing tab.
    Dashboards("dashboards", "Home", Icons.Filled.Dashboard),
    Explore("explore", "Explore", Icons.Filled.Explore),
    Alerts("alerts", "Alerts", Icons.Filled.Notifications),
    OnCall("oncall", "OnCall", Icons.Filled.SupportAgent),
    Accounts("accounts", "Accounts", Icons.Filled.AccountCircle),
}
