package com.fusionlancers.grafusion.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.AmSilence
import com.fusionlancers.grafusion.data.model.Alert
import com.fusionlancers.grafusion.data.model.AlertState
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

private sealed class AlertFilter {
    object All : AlertFilter()
    object Firing : AlertFilter()
    object Pending : AlertFilter()
    object Suppressed : AlertFilter()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    container: AppContainer,
    onOpenSilences: () -> Unit = {},
    onOpenRules: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
    var silences by remember { mutableStateOf<List<AmSilence>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf<AlertFilter>(AlertFilter.All) }
    var selected by remember { mutableStateOf<Alert?>(null) }
    var showInsights by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val insightsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appContext = LocalContext.current.applicationContext
    val notificationHistory by container.notificationHistoryRepository.observe()
        .collectAsState(initial = emptyList())

    fun reload() {
        scope.launch {
            refreshing = true
            error = null
            container.alertRepository.fetchAlerts()
                .onSuccess {
                    alerts = it
                    // Mirror the current firing count to any paired Wear tile.
                    com.fusionlancers.grafusion.wear.WearAlertsPublisher.publish(appContext, it)
                }
                .onFailure { error = it.message ?: "Failed to load alerts" }
            // Silences are non-fatal: if the user can't see them we just hide the section.
            container.alertRepository.listSilences()
                .onSuccess { silences = it }
                .onFailure { silences = emptyList() }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    // Notification tap deep-link: open the sheet for the matching alert once alerts have loaded.
    // We match on fingerprint first (exact), then fall back to alert name (relay may omit the fp).
    val pendingDeepLink by container.pendingAlertDeepLink.collectAsState()
    LaunchedEffect(pendingDeepLink, alerts) {
        val dl = pendingDeepLink ?: return@LaunchedEffect
        if (alerts.isEmpty()) return@LaunchedEffect
        val match = alerts.firstOrNull { dl.fingerprint != null && it.fingerprint == dl.fingerprint }
            ?: alerts.firstOrNull { dl.name != null && it.name.equals(dl.name, ignoreCase = true) }
        if (match != null) {
            selected = match
            container.pendingAlertDeepLink.value = null
        } else if (!refreshing) {
            // Alerts loaded but no match - drop the link so we don't keep re-opening the tab.
            container.pendingAlertDeepLink.value = null
        }
    }

    val firingCount = alerts.count { it.state == AlertState.FIRING }
    val pendingCount = alerts.count { it.state == AlertState.PENDING }
    val suppressedCount = alerts.count { it.state == AlertState.SUPPRESSED }

    val filtered = when (filter) {
        AlertFilter.All -> alerts
        AlertFilter.Firing -> alerts.filter { it.state == AlertState.FIRING }
        AlertFilter.Pending -> alerts.filter { it.state == AlertState.PENDING }
        AlertFilter.Suppressed -> alerts.filter { it.state == AlertState.SUPPRESSED }
    }.sortedWith(compareBy({ severityRank(it.severity) }, { it.name }))

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Alerts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (firingCount > 0) {
                SeverityPill("$firingCount firing", EnergyOrange)
                Spacer(Modifier.size(6.dp))
            }
            IconButton(onClick = onOpenRules) {
                Icon(
                    Icons.Filled.Rule,
                    contentDescription = "Alert rules",
                    tint = EnergyOrange,
                )
            }
            IconButton(onClick = onOpenSilences) {
                Icon(
                    Icons.Filled.NotificationsPaused,
                    contentDescription = "Silences",
                    tint = EnergyOrange,
                )
            }
            IconButton(onClick = { showInsights = true }) {
                Icon(
                    Icons.Filled.Insights,
                    contentDescription = "Alert insights",
                    tint = EnergyOrange,
                )
            }
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill("All ${alerts.size}", filter is AlertFilter.All) { filter = AlertFilter.All }
            FilterPill("Firing $firingCount", filter is AlertFilter.Firing) { filter = AlertFilter.Firing }
            FilterPill("Pending $pendingCount", filter is AlertFilter.Pending) { filter = AlertFilter.Pending }
            FilterPill("Suppressed $suppressedCount", filter is AlertFilter.Suppressed) { filter = AlertFilter.Suppressed }
        }

        Spacer(Modifier.height(12.dp))

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::reload,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                error != null -> ErrorState(error!!)
                refreshing && alerts.isEmpty() -> LoadingState()
                filtered.isEmpty() -> EmptyState(filter)
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.fingerprint }) { alert ->
                        AlertCard(alert, onClick = { selected = alert })
                    }
                }
            }
        }
    }

    SnackbarHost(hostState = snackbar)

    if (showInsights) {
        ModalBottomSheet(
            onDismissRequest = { showInsights = false },
            sheetState = insightsSheetState,
        ) {
            AlertInsightsSheet(history = notificationHistory)
        }
    }

    selected?.let { alert ->
        val activeSilences = remember(alert, silences) {
            container.alertRepository.silencesFor(alert, silences)
        }
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
        ) {
            AlertSheet(
                alert = alert,
                activeSilences = activeSilences,
                onAcknowledge = {
                    scope.launch {
                        val who = container.accountRepository.activeEntity()?.login ?: "grafusion"
                        container.alertRepository.acknowledge(alert, who)
                            .onSuccess {
                                snackbar.showSnackbar("Acknowledged for 4h")
                                selected = null
                                reload()
                            }
                            .onFailure { snackbar.showSnackbar("Ack failed: ${it.message}") }
                    }
                },
                onSilence = { minutes ->
                    scope.launch {
                        val who = container.accountRepository.activeEntity()?.login ?: "grafusion"
                        container.alertRepository
                            .silence(alert, minutes, "Silenced from Grafusion mobile", who)
                            .onSuccess {
                                snackbar.showSnackbar("Silenced for ${humanDuration(minutes)}")
                                selected = null
                                reload()
                            }
                            .onFailure { snackbar.showSnackbar("Silence failed: ${it.message}") }
                    }
                },
                onExpireSilence = { silenceId ->
                    scope.launch {
                        container.alertRepository.expireSilence(silenceId)
                            .onSuccess {
                                snackbar.showSnackbar("Silence lifted")
                                reload()
                            }
                            .onFailure { snackbar.showSnackbar("Failed to lift silence: ${it.message}") }
                    }
                },
                onDismiss = { selected = null },
            )
        }
    }
}

@Composable
private fun AlertSheet(
    alert: Alert,
    activeSilences: List<AmSilence>,
    onAcknowledge: () -> Unit,
    onSilence: (Long) -> Unit,
    onExpireSilence: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                alert.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SeverityPill(alert.severity.uppercase(), severityColor(alert.severity))
        }
        if (alert.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(alert.description, style = MaterialTheme.typography.bodyMedium)
        }
        if (alert.labels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Labels", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            alert.labels.entries.sortedBy { it.key }.forEach { (k, v) ->
                Text("$k = $v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }
        }
        if (activeSilences.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Active silences", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            activeSilences.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.comment.orEmpty().ifBlank { "Silenced" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "until ${s.endsAt?.take(19) ?: "?"} - by ${s.createdBy.orEmpty().ifBlank { "?" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    OutlinedButton(onClick = { onExpireSilence(s.id) }) { Text("Unmute") }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Actions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3498DB)),
            ) {
                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Ack 4h")
            }
            SilenceButton("30m", onSilence, 30)
            SilenceButton("2h", onSilence, 120)
            SilenceButton("24h", onSilence, 1440)
        }
        if (!alert.generatorURL.isNullOrBlank()) {
            val context = LocalContext.current
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(alert.generatorURL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open in Grafana")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SilenceButton(label: String, onSilence: (Long) -> Unit, minutes: Long) {
    Button(
        onClick = { onSilence(minutes) },
        colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
    ) {
        Icon(Icons.Filled.NotificationsOff, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(label)
    }
}

private fun humanDuration(minutes: Long): String = when {
    minutes >= 60 * 24 -> "${minutes / (60 * 24)}d"
    minutes >= 60 -> "${minutes / 60}h"
    else -> "${minutes}m"
}

@Composable
private fun AlertCard(alert: Alert, onClick: () -> Unit) {
    val (icon, tint) = iconFor(alert)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = tint.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alert.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SeverityPill(alert.severity.uppercase(), severityColor(alert.severity))
                }
                if (alert.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        alert.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val instance = alert.labels["instance"] ?: alert.labels["job"]
                if (instance != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        instance,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EnergyOrange.copy(alpha = 0.18f),
            selectedLabelColor = EnergyOrange,
        ),
    )
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EnergyOrange)
    }
}

@Composable
private fun EmptyState(filter: AlertFilter) {
    val label = when (filter) {
        AlertFilter.All -> "No alerts - everything looks healthy."
        AlertFilter.Firing -> "No firing alerts right now."
        AlertFilter.Pending -> "No pending alerts."
        AlertFilter.Suppressed -> "No suppressed alerts."
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2ECC71), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(label, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Error, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "Could not load alerts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun iconFor(alert: Alert): Pair<ImageVector, Color> = when (alert.state) {
    AlertState.FIRING -> Icons.Filled.NotificationsActive to severityColor(alert.severity)
    AlertState.PENDING -> Icons.Filled.Schedule to Color(0xFFF1C40F)
    AlertState.SUPPRESSED -> Icons.Filled.NotificationsPaused to Color(0xFF95A5A6)
    AlertState.NORMAL -> Icons.Filled.CheckCircle to Color(0xFF2ECC71)
}

private fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "critical" -> Color(0xFFE74C3C)
    "warning" -> EnergyOrange
    "info" -> Color(0xFF3498DB)
    else -> Color(0xFF7F8C8D)
}

private fun severityRank(severity: String): Int = when (severity.lowercase()) {
    "critical" -> 0
    "warning" -> 1
    "info" -> 2
    else -> 3
}
