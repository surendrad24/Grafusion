package com.fusionlancers.grafusion.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.Datasource
import com.fusionlancers.grafusion.data.model.PanelData
import com.fusionlancers.grafusion.data.model.RawFrame
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

/**
 * Explore is the ad-hoc query workbench. We don't try to replicate Grafana's full editor:
 * pick a datasource, type PromQL / LogQL / TraceQL / raw SQL, hit Run, see the result. The
 * ExploreRepository maps the query shape by datasource type (see its docstring for the full
 * matrix); this screen just picks the right renderer for the returned frames.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var datasources by remember { mutableStateOf<List<Datasource>>(emptyList()) }
    var selectedDs by remember { mutableStateOf<Datasource?>(null) }
    var dsMenuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var timeRange by remember { mutableStateOf("now-1h") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PanelData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.exploreRepository.listDatasources()
            .onSuccess {
                datasources = it
                // Prefer default DS; else first prometheus/loki if present.
                selectedDs = it.firstOrNull { d -> d.isDefault }
                    ?: it.firstOrNull { d -> d.type.lowercase() in setOf("prometheus", "loki") }
                    ?: it.firstOrNull()
            }
            .onFailure { loadError = it.message }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Datasource picker
            Box {
                OutlinedTextField(
                    value = selectedDs?.let { "${it.name}  •  ${it.type}" } ?: "Select datasource",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Datasource") },
                    leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dsMenuOpen = true },
                    enabled = false,
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        disabledLeadingIconColor = EnergyOrange,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                DropdownMenu(
                    expanded = dsMenuOpen,
                    onDismissRequest = { dsMenuOpen = false },
                ) {
                    datasources.forEach { d ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(d.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        d.type + if (d.isDefault) " (default)" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            },
                            onClick = {
                                selectedDs = d
                                dsMenuOpen = false
                            },
                        )
                    }
                    if (datasources.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(loadError ?: "Loading…", color = MaterialTheme.colorScheme.error) },
                            onClick = { dsMenuOpen = false },
                        )
                    }
                }
            }

            // Query editor
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(placeholderFor(selectedDs?.type)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = EnergyOrange,
                    cursorColor = EnergyOrange,
                ),
            )

            // Time range chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("now-15m", "now-1h", "now-6h", "now-24h", "now-7d").forEach { range ->
                    FilterChip(
                        selected = timeRange == range,
                        onClick = { timeRange = range },
                        label = { Text(rangeLabel(range)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EnergyOrange.copy(alpha = 0.2f),
                            selectedLabelColor = EnergyOrange,
                        ),
                    )
                }
            }

            Button(
                onClick = {
                    val ds = selectedDs ?: return@Button
                    if (query.isBlank()) return@Button
                    scope.launch {
                        running = true
                        error = null
                        container.exploreRepository.runQuery(ds, query, from = timeRange, to = "now")
                            .onSuccess { result = it; error = it.error }
                            .onFailure { error = it.message ?: "Query failed"; result = null }
                        running = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                enabled = selectedDs != null && query.isNotBlank() && !running,
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Running…")
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Run query")
                }
            }

            error?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE74C3C).copy(alpha = 0.12f)),
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFE74C3C),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            result?.let { data ->
                ResultsPane(data = data, datasourceType = selectedDs?.type.orEmpty())
            }
        }
    }
}

private fun placeholderFor(type: String?): String = when (type?.lowercase()) {
    "prometheus" -> "PromQL, e.g. sum(rate(http_requests_total[5m]))"
    "loki" -> "LogQL, e.g. {job=\"varlogs\"} |= \"error\""
    "tempo" -> "TraceQL, e.g. { duration > 100ms } or a trace ID"
    null -> "Query"
    else -> "$type query"
}

private fun rangeLabel(r: String): String = when (r) {
    "now-15m" -> "15m"
    "now-1h" -> "1h"
    "now-6h" -> "6h"
    "now-24h" -> "24h"
    "now-7d" -> "7d"
    else -> r
}

/**
 * Results renderer. We pick the layout by datasource type rather than by frame shape so the
 * user gets the mental model they expect: PromQL -> series list, LogQL -> log lines, TraceQL
 * -> trace summaries, everything else -> table. When frames are empty we still show the
 * "no rows" panel instead of collapsing silently.
 */
@Composable
private fun ResultsPane(data: PanelData, datasourceType: String) {
    val typeLower = datasourceType.lowercase()
    Card(
        modifier = Modifier.fillMaxWidth().fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timeline, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    "Results",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "${data.series.size} series • ${data.frames.sumOf { it.rowCount }} rows",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                data.series.isEmpty() && data.frames.all { it.rowCount == 0 } -> {
                    Text(
                        "No data returned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                typeLower == "prometheus" -> SeriesList(data)
                typeLower == "loki" -> LogLinesList(data)
                typeLower == "tempo" -> TraceList(data)
                else -> FrameTable(data.frames.firstOrNull())
            }
        }
    }
}

@Composable
private fun SeriesList(data: PanelData) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(data.series, key = { it.name.hashCode() }) { s ->
            val last = s.values.lastOrNull { it != null }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    last?.let { formatValue(it) } ?: "-",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = EnergyOrange,
                )
            }
        }
    }
}

@Composable
private fun LogLinesList(data: PanelData) {
    // Loki responses put lines in a "Line" or "line" column; grab the first string column.
    val frame = data.frames.firstOrNull() ?: return
    val lineIdx = frame.fieldNames.indexOfFirst { it.equals("Line", ignoreCase = true) }
        .takeIf { it >= 0 }
        ?: frame.fieldTypes.indexOfFirst { it == "string" }
        .takeIf { it >= 0 }
        ?: return
    val tsIdx = frame.fieldTypes.indexOfFirst { it == "time" }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(frame.rowCount) { row ->
            val line = frame.columns[lineIdx].getOrNull(row)?.toString().orEmpty()
            val ts = if (tsIdx >= 0) (frame.columns[tsIdx].getOrNull(row) as? Long) else null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                if (ts != null) {
                    Text(
                        formatTime(ts),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun TraceList(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: return
    val idIdx = frame.fieldNames.indexOfFirst { it.equals("traceID", ignoreCase = true) }
        .let { if (it >= 0) it else frame.fieldNames.indexOfFirst { n -> n.contains("trace", ignoreCase = true) } }
    val serviceIdx = frame.fieldNames.indexOfFirst { it.contains("service", ignoreCase = true) }
    val nameIdx = frame.fieldNames.indexOfFirst { it.equals("traceName", ignoreCase = true) || it.contains("name", ignoreCase = true) }
    val durIdx = frame.fieldNames.indexOfFirst { it.contains("duration", ignoreCase = true) }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(frame.rowCount) { row ->
            val id = idIdx.takeIf { it >= 0 }?.let { frame.columns[it].getOrNull(row)?.toString() }
            val service = serviceIdx.takeIf { it >= 0 }?.let { frame.columns[it].getOrNull(row)?.toString() }
            val name = nameIdx.takeIf { it >= 0 }?.let { frame.columns[it].getOrNull(row)?.toString() }
            val dur = durIdx.takeIf { it >= 0 }?.let { frame.columns[it].getOrNull(row) as? Double ?: (frame.columns[it].getOrNull(row) as? Long)?.toDouble() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (service ?: "trace") + (name?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    dur?.let {
                        Text(
                            "${it.toLong()}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = EnergyOrange,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                id?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/**
 * Generic table renderer for datasource types we don't have a bespoke pane for (SQL, InfluxDB,
 * TestData, ...). Horizontally scrollable so wide result sets stay useful on a phone.
 */
@Composable
private fun FrameTable(frame: RawFrame?) {
    if (frame == null || frame.rowCount == 0) {
        Text(
            "No rows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 6.dp, horizontal = 8.dp),
        ) {
            frame.fieldNames.forEach { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        (0 until frame.rowCount).take(200).forEach { r ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
            ) {
                frame.columns.forEach { col ->
                    Text(
                        col.getOrNull(r)?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun formatValue(v: Double): String {
    val abs = kotlin.math.abs(v)
    return when {
        abs == 0.0 -> "0"
        abs >= 1000_000_000 -> "%.2fG".format(v / 1_000_000_000)
        abs >= 1000_000 -> "%.2fM".format(v / 1_000_000)
        abs >= 1000 -> "%.2fk".format(v / 1_000)
        abs < 0.01 -> "%.4f".format(v)
        else -> "%.3f".format(v)
    }
}

private fun formatTime(ms: Long): String {
    val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ms))
}
