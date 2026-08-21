package com.fusionlancers.grafusion.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Dashboards("dashboards", "Dashboards", Icons.Filled.Dashboard),
    Explore("explore", "Explore", Icons.Filled.Explore),
    Alerts("alerts", "Alerts", Icons.Filled.Notifications),
    OnCall("oncall", "OnCall", Icons.Filled.SupportAgent),
    Accounts("accounts", "Accounts", Icons.Filled.AccountCircle),
}
