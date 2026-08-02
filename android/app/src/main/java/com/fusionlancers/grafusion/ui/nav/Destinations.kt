package com.fusionlancers.grafusion.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Dashboards("dashboards", "Dashboards", Icons.Filled.Dashboard),
    Alerts("alerts", "Alerts", Icons.Filled.Notifications),
    Accounts("accounts", "Accounts", Icons.Filled.AccountCircle),
}
