package com.fusionlancers.grafusion.ui.alerts

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.AmMuteTiming
import com.fusionlancers.grafusion.data.api.AmTimeInterval
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

/**
 * Read-only viewer for Alertmanager mute-time intervals. Each named timing is a bag of
 * time_intervals, and each interval is an AND across weekdays / months / etc. We render one
 * card per timing, showing which routes reference it (from the flattened alertmanager config)
 * and a human-readable one-line summary per interval so users don't have to decode
 * "monday:friday" + "09:00-17:00" mentally.
 *
 * Deliberately read-only for now - authoring mute timings is error-prone and best done from
 * the desktop wizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuteTimingsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var timings by remember { mutableStateOf<List<TimingRow>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            refreshing = true
            error = null
            container.alertRepository.alertmanagerConfig()
                .onSuccess { cfg ->
                    // Aggregate references: walk the routing tree and record which routes name each timing.
                    val refs = mutableMapOf<String, MutableList<String>>()
                    fun walk(r: com.fusionlancers.grafusion.data.api.AmRoute, path: String) {
                        val label = r.receiver?.takeIf { it.isNotBlank() } ?: "(inherits)"
                        r.muteTimeIntervals.forEach { name ->
                            refs.getOrPut(name) { mutableListOf() } += if (path.isBlank()) label else "$path / $label"
                        }
                        r.routes.forEachIndexed { idx, child -> walk(child, if (path.isBlank()) label else "$path / $label") }
                    }
                    cfg.route?.let { walk(it, "") }

                    val all = (cfg.muteTimeIntervals + cfg.timeIntervals).distinctBy { it.name }
                    timings = all
                        .sortedBy { it.name.lowercase() }
                        .map { t -> TimingRow(t, refs[t.name].orEmpty()) }
                }
                .onFailure { error = it.message ?: "Failed to load mute timings" }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mute timings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                error != null -> ErrorState(error!!)
                refreshing && timings.isEmpty() -> LoadingState()
                timings.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(timings, key = { it.timing.name }) { row -> TimingCard(row) }
                }
            }
        }
    }
}

private data class TimingRow(val timing: AmMuteTiming, val references: List<String>)

@Composable
private fun TimingCard(row: TimingRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(26.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = EnergyOrange.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.NotificationsOff,
                            null,
                            tint = EnergyOrange,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.timing.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${row.timing.intervals.size} interval${if (row.timing.intervals.size == 1) "" else "s"} - used by ${row.references.size} route${if (row.references.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            if (row.timing.intervals.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "(no time_intervals defined - matches nothing)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            } else {
                Spacer(Modifier.height(6.dp))
                row.timing.intervals.forEachIndexed { idx, interval ->
                    Text(
                        "• ${describeInterval(interval)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                    interval.location?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "   tz: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
            if (row.references.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Routes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                row.references.take(6).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
                if (row.references.size > 6) {
                    Text(
                        "+ ${row.references.size - 6} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

/**
 * Fold an AmTimeInterval into one readable line. Empty axes mean "any", so we only mention
 * the ones the operator actually constrained. Time ranges render as HH:MM-HH:MM lists.
 */
private fun describeInterval(iv: AmTimeInterval): String {
    val parts = mutableListOf<String>()
    if (iv.times.isNotEmpty()) {
        parts += iv.times.joinToString(", ") { "${it.startTime}-${it.endTime}" }
    }
    if (iv.weekdays.isNotEmpty()) parts += iv.weekdays.joinToString(", ")
    if (iv.daysOfMonth.isNotEmpty()) parts += "day ${iv.daysOfMonth.joinToString(", ")}"
    if (iv.months.isNotEmpty()) parts += iv.months.joinToString(", ")
    if (iv.years.isNotEmpty()) parts += iv.years.joinToString(", ")
    return if (parts.isEmpty()) "any time (unconstrained)" else parts.joinToString(" • ")
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.NotificationsOff, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("No mute timings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Alertmanager has no time_intervals or mute_time_intervals defined.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EnergyOrange)
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Mute timings unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
