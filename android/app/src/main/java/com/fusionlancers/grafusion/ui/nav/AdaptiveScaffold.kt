package com.fusionlancers.grafusion.ui.nav

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.ui.accounts.AccountsScreen
import com.fusionlancers.grafusion.ui.admin.AdminScreen
import com.fusionlancers.grafusion.ui.alerts.AlertRulesScreen
import com.fusionlancers.grafusion.ui.alerts.AlertsScreen
import com.fusionlancers.grafusion.ui.alerts.ContactPointsScreen
import com.fusionlancers.grafusion.ui.alerts.MuteTimingsScreen
import com.fusionlancers.grafusion.ui.alerts.NotificationPoliciesScreen
import com.fusionlancers.grafusion.ui.alerts.SilencesScreen
import com.fusionlancers.grafusion.ui.dashboards.DashboardDetailScreen
import com.fusionlancers.grafusion.ui.dashboards.DashboardListScreen
import com.fusionlancers.grafusion.ui.dashboards.DashboardVersionsScreen
import com.fusionlancers.grafusion.ui.dashboards.PublicDashboardsScreen
import com.fusionlancers.grafusion.ui.dashboards.SnapshotsScreen
import com.fusionlancers.grafusion.ui.datasources.DatasourceDetailScreen
import com.fusionlancers.grafusion.ui.datasources.DatasourcesScreen
import com.fusionlancers.grafusion.ui.explore.ExploreScreen
import com.fusionlancers.grafusion.ui.history.NotificationHistoryScreen
import com.fusionlancers.grafusion.ui.library.LibraryScreen
import com.fusionlancers.grafusion.ui.reports.ReportsScreen
import com.fusionlancers.grafusion.ui.oncall.OnCallScreen
import com.fusionlancers.grafusion.ui.permissions.PermissionsScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AdaptiveScaffold(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    container: AppContainer,
    useNavRail: Boolean,
) {
    val current by navController.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route

    // When a notification tap sets a pending alert deep-link, jump to the Alerts tab.
    // AlertsScreen itself consumes the flow to open the matching alert sheet.
    val pendingAlert by container.pendingAlertDeepLink.collectAsState()
    LaunchedEffect(pendingAlert) {
        if (pendingAlert != null && currentRoute != TopDest.Alerts.route) {
            navController.navigateSingleTop(TopDest.Alerts.route)
        }
    }

    // Widget / external launcher / grafana:// deep-link hint. Nested routes (e.g. dashboard/{uid})
    // are pushed onto the back stack; top-level tabs use the single-top helper so they don't stack.
    val pendingRoute by container.pendingStartRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        val target = pendingRoute ?: return@LaunchedEffect
        if (target.startsWith("dashboard/")) {
            navController.navigate(target)
        } else if (currentRoute != target) {
            navController.navigateSingleTop(target)
        }
        container.pendingStartRoute.value = null
    }

    if (useNavRail) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                TopDest.entries.forEach { dest ->
                    NavigationRailItem(
                        selected = currentRoute == dest.route,
                        onClick = { navController.navigateSingleTop(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
            AppNavHost(navController, container, Modifier.fillMaxSize())
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    TopDest.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navController.navigateSingleTop(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = {
                                Text(
                                    dest.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        ) { padding ->
            AppNavHost(navController, container, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopDest.Dashboards.route,
        modifier = modifier,
    ) {
        composable(TopDest.Dashboards.route) {
            DashboardListScreen(
                container = container,
                onOpenDashboard = { uid, title ->
                    val safeTitle = URLEncoder.encode(title, "UTF-8")
                    navController.navigate("dashboard/$uid?title=$safeTitle")
                },
            )
        }
        composable(
            route = "dashboard/{uid}?title={title}",
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val uid = entry.arguments?.getString("uid").orEmpty()
            val title = URLDecoder.decode(entry.arguments?.getString("title").orEmpty(), "UTF-8")
            DashboardDetailScreen(
                container = container,
                uid = uid,
                title = title.ifBlank { "Dashboard" },
                onBack = { navController.popBackStack() },
                onOpenVersions = {
                    val safeTitle = URLEncoder.encode(title.ifBlank { "Dashboard" }, "UTF-8")
                    navController.navigate("dashboard/$uid/versions?title=$safeTitle")
                },
            )
        }
        composable(
            route = "dashboard/{uid}/versions?title={title}",
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val uid = entry.arguments?.getString("uid").orEmpty()
            val title = URLDecoder.decode(entry.arguments?.getString("title").orEmpty(), "UTF-8")
            DashboardVersionsScreen(
                container = container,
                uid = uid,
                title = title,
                onBack = { navController.popBackStack() },
            )
        }
        composable(TopDest.Explore.route) {
            ExploreScreen(container = container)
        }
        composable(TopDest.Alerts.route) {
            AlertsScreen(
                container = container,
                onOpenSilences = { navController.navigate("silences") },
                onOpenRules = { navController.navigate("alert_rules") },
                onOpenContactPoints = { navController.navigate("contact_points") },
                onOpenPolicies = { navController.navigate("notification_policies") },
                onOpenMuteTimings = { navController.navigate("mute_timings") },
            )
        }
        composable("silences") {
            SilencesScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable("alert_rules") {
            AlertRulesScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable("contact_points") {
            ContactPointsScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable("notification_policies") {
            NotificationPoliciesScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable("mute_timings") {
            MuteTimingsScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(TopDest.OnCall.route) {
            OnCallScreen(container = container)
        }
        composable(TopDest.Accounts.route) {
            AccountsScreen(
                container = container,
                onOpenPermissions = { navController.navigate("permissions") },
                onOpenHistory = { navController.navigate("notification_history") },
                onOpenDatasources = { navController.navigate("datasources") },
                onOpenAdmin = { navController.navigate("admin") },
                onOpenLibrary = { navController.navigate("library") },
                onOpenReports = { navController.navigate("reports") },
                onOpenKiosk = { navController.navigate("kiosk") },
                onOpenSnapshots = { navController.navigate("snapshots") },
                onOpenPublicDashboards = { navController.navigate("public_dashboards") },
            )
        }
        composable("snapshots") {
            SnapshotsScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable("public_dashboards") {
            PublicDashboardsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenDashboard = { uid, title ->
                    val safeTitle = URLEncoder.encode(title, "UTF-8")
                    navController.navigate("dashboard/$uid?title=$safeTitle")
                },
            )
        }
        composable("datasources") {
            DatasourcesScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenDetail = { uid -> navController.navigate("datasource/$uid") },
            )
        }
        composable(
            route = "datasource/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.StringType }),
        ) { entry ->
            val uid = entry.arguments?.getString("uid").orEmpty()
            DatasourceDetailScreen(
                container = container,
                uid = uid,
                onBack = { navController.popBackStack() },
            )
        }
        composable("admin") {
            AdminScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable("library") {
            LibraryScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable("reports") {
            ReportsScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable("kiosk") {
            com.fusionlancers.grafusion.ui.kiosk.KioskScreen(
                container = container,
                onOpenDashboard = { uid, title ->
                    val safeTitle = URLEncoder.encode(title, "UTF-8")
                    navController.navigate("dashboard/$uid?title=$safeTitle")
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable("permissions") {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable("notification_history") {
            NotificationHistoryScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenAlerts = { navController.navigateSingleTop(TopDest.Alerts.route) },
            )
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
