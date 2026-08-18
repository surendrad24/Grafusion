package com.fusionlancers.grafusion.ui.dashboards

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.PanelData
import com.fusionlancers.grafusion.data.model.RawFrame
import com.fusionlancers.grafusion.ui.theme.DataPurple
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.component.LineComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardDetailScreen(
    container: AppContainer,
    uid: String,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var panels by remember { mutableStateOf<List<Panel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(TimeRange.LAST_6H) }
    var refresh by remember { mutableStateOf(RefreshInterval.OFF) }
    val panelData = remember { mutableStateMapOf<Long, PanelData?>() }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }
    val workingOrder = remember { mutableStateListOf<Long>() }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        loading = true
        error = null
        runCatching { container.dashboardRepository.panelsFor(uid) }
            .onSuccess { list ->
                panels = list
                loading = false
            }
            .onFailure { error = it.message ?: "Failed to load dashboard"; loading = false }
        val entity = container.accountRepository.activeEntity()
        browserUrl = entity?.let { "${it.grafanaUrl}/d/$uid" }
    }

    // Fetch panel data whenever panel list or range changes.
    LaunchedEffect(panels, range) {
        panels.forEach { panel ->
            if (panel.targets.isEmpty()) {
                panelData[panel.id] = PanelData(series = emptyList())
                return@forEach
            }
            panelData[panel.id] = null
            scope.launch {
                container.dashboardRepository
                    .queryPanel(panel, from = range.from, to = range.to)
                    .onSuccess { panelData[panel.id] = it }
                    .onFailure { panelData[panel.id] = PanelData(series = emptyList(), error = it.message) }
            }
        }
    }

    // Auto-refresh loop.
    LaunchedEffect(refresh, panels, range) {
        val ms = refresh.millis ?: return@LaunchedEffect
        while (true) {
            delay(ms)
            panels.forEach { panel ->
                if (panel.targets.isEmpty()) return@forEach
                scope.launch {
                    container.dashboardRepository
                        .queryPanel(panel, from = range.from, to = range.to)
                        .onSuccess { panelData[panel.id] = it }
                        .onFailure { panelData[panel.id] = PanelData(series = emptyList(), error = it.message) }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editMode) {
                        IconButton(
                            enabled = !saving,
                            onClick = {
                                editMode = false
                                workingOrder.clear()
                                saveMessage = null
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel edit")
                        }
                        IconButton(
                            enabled = !saving && workingOrder.isNotEmpty(),
                            onClick = {
                                saving = true
                                saveMessage = null
                                val order = workingOrder.toList()
                                scope.launch {
                                    container.dashboardRepository.savePanelOrder(uid, order)
                                        .onSuccess {
                                            saveMessage = "Layout saved"
                                            editMode = false
                                            workingOrder.clear()
                                            panelData.clear()
                                            runCatching { container.dashboardRepository.panelsFor(uid) }
                                                .onSuccess { panels = it }
                                        }
                                        .onFailure { saveMessage = "Save failed: ${it.message ?: "unknown"}" }
                                    saving = false
                                }
                            },
                        ) {
                            if (saving) CircularProgressIndicator(
                                color = EnergyOrange,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            ) else Icon(Icons.Filled.Check, contentDescription = "Save layout")
                        }
                    } else {
                        RefreshMenu(refresh, onSelect = { refresh = it })
                        IconButton(onClick = {
                            scope.launch {
                                panels.forEach { panel ->
                                    panelData[panel.id] = null
                                    container.dashboardRepository
                                        .queryPanel(panel, from = range.from, to = range.to)
                                        .onSuccess { panelData[panel.id] = it }
                                        .onFailure { panelData[panel.id] = PanelData(series = emptyList(), error = it.message) }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh now")
                        }
                        IconButton(onClick = {
                            editMode = true
                            workingOrder.clear()
                            workingOrder.addAll(
                                panels.sortedWith(compareBy({ it.gridY }, { it.gridX })).map { it.id }
                            )
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit layout")
                        }
                        IconButton(onClick = {
                            browserUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        }) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open in browser")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingBlock("Loading dashboard…")
                error != null -> ErrorBlock(error!!)
                panels.isEmpty() -> EmptyDashboardBlock()
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Use side-by-side grid layout on wider screens; single column on narrow.
                    val useGrid = maxWidth > 600.dp
                    val bands = remember(panels, useGrid) { groupIntoRows(panels, useGrid) }
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (saveMessage != null) {
                            item { SaveBanner(saveMessage!!) }
                        }
                        if (editMode) {
                            item { EditModeBanner() }
                            val orderedPanels = workingOrder.mapNotNull { id -> panels.firstOrNull { it.id == id } }
                            items(orderedPanels, key = { "edit_${it.id}" }) { panel ->
                                val idx = workingOrder.indexOf(panel.id)
                                EditRow(
                                    panel = panel,
                                    data = panelData[panel.id],
                                    isFirst = idx <= 0,
                                    isLast = idx >= workingOrder.lastIndex,
                                    onMoveUp = {
                                        if (idx > 0) {
                                            val tmp = workingOrder[idx - 1]
                                            workingOrder[idx - 1] = workingOrder[idx]
                                            workingOrder[idx] = tmp
                                        }
                                    },
                                    onMoveDown = {
                                        if (idx >= 0 && idx < workingOrder.lastIndex) {
                                            val tmp = workingOrder[idx + 1]
                                            workingOrder[idx + 1] = workingOrder[idx]
                                            workingOrder[idx] = tmp
                                        }
                                    },
                                )
                            }
                        } else {
                            item { TimeRangeBar(range = range, onSelect = { range = it }) }
                            items(bands, key = { it.key }) { band ->
                                PanelRow(band = band, panelData = panelData)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PanelBand(val key: String, val panels: List<Panel>, val heightDp: Int)

/** Group panels into layout rows respecting gridPos when useGrid=true; otherwise one panel per row. */
private fun groupIntoRows(all: List<Panel>, useGrid: Boolean): List<PanelBand> {
    val sorted = all.sortedWith(compareBy({ it.gridY }, { it.gridX }))
    if (!useGrid) {
        return sorted.map { PanelBand(key = "p${it.id}", panels = listOf(it), heightDp = it.gridH * 30) }
    }
    val bands = mutableListOf<PanelBand>()
    var current = mutableListOf<Panel>()
    var currentY = -1
    for (p in sorted) {
        if (current.isEmpty()) {
            current += p
            currentY = p.gridY
        } else if (kotlin.math.abs(p.gridY - currentY) <= 2 && current.sumOf { it.gridW } + p.gridW <= 24) {
            current += p
        } else {
            bands += PanelBand(
                key = current.joinToString("_") { "p${it.id}" },
                panels = current.toList(),
                heightDp = (current.maxOf { it.gridH } * 30),
            )
            current = mutableListOf(p)
            currentY = p.gridY
        }
    }
    if (current.isNotEmpty()) {
        bands += PanelBand(
            key = current.joinToString("_") { "p${it.id}" },
            panels = current.toList(),
            heightDp = (current.maxOf { it.gridH } * 30),
        )
    }
    return bands
}

@Composable
private fun PanelRow(band: PanelBand, panelData: androidx.compose.runtime.snapshots.SnapshotStateMap<Long, PanelData?>) {
    // Let the panel body dictate height — Grafana gridH assumes desktop pixel grid that dwarfs mobile cards.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        band.panels.forEach { panel ->
            Box(Modifier.weight(panel.gridW.toFloat().coerceAtLeast(1f)).fillMaxWidth()) {
                PanelCard(panel = panel, data = panelData[panel.id])
            }
        }
    }
}

private enum class TimeRange(val label: String, val from: String, val to: String) {
    LAST_5M("5m", "now-5m", "now"),
    LAST_1H("1h", "now-1h", "now"),
    LAST_6H("6h", "now-6h", "now"),
    LAST_24H("24h", "now-24h", "now"),
    LAST_7D("7d", "now-7d", "now"),
    LAST_30D("30d", "now-30d", "now"),
}

private enum class RefreshInterval(val label: String, val millis: Long?) {
    OFF("Off", null),
    S5("5s", 5_000L),
    S10("10s", 10_000L),
    S30("30s", 30_000L),
    M1("1m", 60_000L),
    M5("5m", 5 * 60_000L),
    M15("15m", 15 * 60_000L),
}

@Composable
private fun RefreshMenu(current: RefreshInterval, onSelect: (RefreshInterval) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Timer, contentDescription = "Auto refresh: ${current.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RefreshInterval.entries.forEach { r ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "Refresh every ${r.label}",
                            color = if (r == current) EnergyOrange else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (r == current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = { onSelect(r); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun TimeRangeBar(range: TimeRange, onSelect: (TimeRange) -> Unit) {
    val options = TimeRange.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, opt ->
            SegmentedButton(
                selected = range == opt,
                onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                label = { Text(opt.label, fontWeight = FontWeight.Medium) },
            )
        }
    }
}

@Composable
private fun EditModeBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EnergyOrange.copy(alpha = 0.12f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Reorder mode — use arrows to move panels, then tap ✓ to save to Grafana.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SaveBanner(message: String) {
    val isError = message.startsWith("Save failed", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else EnergyOrange.copy(alpha = 0.16f)
        ),
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EditRow(
    panel: Panel,
    data: PanelData?,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f)) {
            PanelCard(panel = panel, data = data)
        }
        Column {
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
            }
        }
    }
}

@Composable
private fun PanelCard(panel: Panel, data: PanelData?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardW = maxWidth
            val padding = if (cardW < 140.dp) 8.dp else 14.dp
            Column(Modifier.padding(padding)) {
                PanelHeader(panel = panel, cardWidth = cardW)
                Spacer(Modifier.height(if (cardW < 140.dp) 6.dp else 12.dp))
                when {
                    data == null -> PanelLoading()
                    data.error != null -> PanelError(data.error)
                    data.series.isEmpty() && data.frames.isEmpty() -> PanelNoData()
                    else -> PanelBody(panel = panel, data = data, cardWidth = cardW)
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(panel: Panel, cardWidth: Dp) {
    val showTypeBadge = cardWidth >= 180.dp
    val titleStyle = when {
        cardWidth < 120.dp -> MaterialTheme.typography.labelMedium
        cardWidth < 180.dp -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.titleMedium
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            panel.title.ifBlank { "Panel ${panel.id}" },
            modifier = Modifier.weight(1f),
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTypeBadge) {
            Text(
                panel.type,
                style = MaterialTheme.typography.labelSmall,
                color = EnergyOrange,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PanelBody(panel: Panel, data: PanelData, cardWidth: Dp) {
    when (panel.type) {
        "timeseries", "graph" -> TimeseriesPanel(data)
        "stat", "gauge", "bargauge" -> StatOrGaugePanel(panel, data, cardWidth)
        "table" -> TablePanel(data)
        "barchart" -> BarChartPanel(data)
        "piechart" -> PieChartPanel(data)
        "heatmap" -> HeatmapPanel(data)
        "state-timeline", "status-history" -> StateTimelinePanel(data)
        "logs" -> LogsPanel(data)
        "text" -> TextPanel(panel)
        "geomap", "worldmap-panel" -> GeomapPanel(data)
        else -> UnsupportedPanel(panel.type, null)
    }
}

@Composable
private fun TimeseriesPanel(data: PanelData) {
    val validSeries = remember(data) {
        data.series.mapNotNull { s ->
            val pairs = s.timestamps.zip(s.values).mapNotNull { (t, v) -> if (v == null) null else t to v }
            if (pairs.isEmpty()) null else s to pairs
        }
    }
    if (validSeries.isEmpty()) { PanelNoData(); return }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(validSeries) {
        producer.runTransaction {
            lineSeries {
                validSeries.forEach { (_, pairs) ->
                    series(x = pairs.map { it.first.toDouble() }, y = pairs.map { it.second })
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(200.dp)) {
        val lineColors = listOf(EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF22D3EE))
        val layer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                lineColors.map { color ->
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(color)),
                    )
                }
            )
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                layer,
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = { _, value, _ ->
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value.toLong()))
                    }
                ),
            ),
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
        )
    }
    if (validSeries.size > 1) {
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth()) {
            val legendColors = listOf(EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF22D3EE))
            validSeries.take(6).forEachIndexed { idx, (s, _) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(10.dp).background(legendColors[idx % legendColors.size], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(s.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BarChartPanel(data: PanelData) {
    // Two shapes:
    // A) Prometheus format=table+instant: one frame per label combo, label in fieldLabels, value is last number.
    // B) Single frame with a label column (string) + value column (number).
    val labeledPairs = remember(data) { extractBarPairs(data) }
    if (labeledPairs.isEmpty()) { PanelNoData(); return }
    val values = labeledPairs.map { it.second }
    val labels = labeledPairs.map { it.first }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        producer.runTransaction {
            columnSeries { series(values) }
        }
    }
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = { _, value, _ ->
                        labels.getOrNull(value.toInt()) ?: value.toInt().toString()
                    }
                ),
            ),
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = true),
        )
    }
}

private fun reduceStat(values: List<Double>, calc: String): Double? {
    if (values.isEmpty()) return null
    return when (calc) {
        "last", "lastNotNull" -> values.last()
        "first", "firstNotNull" -> values.first()
        "mean" -> values.average()
        "min" -> values.min()
        "max" -> values.max()
        "sum", "total" -> values.sum()
        "count" -> values.size.toDouble()
        "range" -> values.max() - values.min()
        else -> values.last()
    }
}

/** Returns (label, value) pairs sorted by descending value, capped at 20. */
private fun extractBarPairs(data: PanelData): List<Pair<String, Double>> {
    val pairs = mutableListOf<Pair<String, Double>>()
    val preferredLabelKeys = listOf("country", "city", "type", "scenario", "name", "instance", "job", "service")
    for (frame in data.frames) {
        val numIdx = frame.fieldTypes.indexOfFirst { it == "number" }
        if (numIdx < 0) continue
        val labels = frame.fieldLabels.getOrNull(numIdx).orEmpty()
        if (labels.isNotEmpty()) {
            // Case A: one frame per label combo (Prometheus).
            val labelKey = preferredLabelKeys.firstOrNull { labels[it]?.isNotBlank() == true }
                ?: labels.keys.firstOrNull { it != "__name__" }
            val label = labelKey?.let { labels[it] }.orEmpty()
            val v = (frame.columns[numIdx].lastOrNull { it != null } as? Number)?.toDouble() ?: continue
            if (label.isNotBlank()) pairs += label to v
            continue
        }
        // Case B: label column + value column in one frame.
        val strIdx = frame.fieldTypes.indexOfFirst { it == "string" }
        if (strIdx >= 0) {
            for (r in 0 until frame.rowCount) {
                val lbl = frame.columns[strIdx].getOrNull(r)?.toString().orEmpty()
                val v = (frame.columns[numIdx].getOrNull(r) as? Number)?.toDouble() ?: continue
                if (lbl.isNotBlank()) pairs += lbl to v
            }
        }
    }
    return pairs.sortedByDescending { it.second }.take(20)
}

@Composable
private fun StatOrGaugePanel(panel: Panel, data: PanelData, cardWidth: Dp) {
    val calc = remember(panel.options) {
        val reduce = panel.options?.get("reduceOptions") as? kotlinx.serialization.json.JsonObject
        val calcs = reduce?.get("calcs") as? kotlinx.serialization.json.JsonArray
        (calcs?.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "lastNotNull"
    }
    val seriesValues = data.series.firstOrNull()?.values?.filterNotNull().orEmpty()
    val frameValues = data.frames.firstOrNull()?.let { frame ->
        frame.columns.getOrNull(frame.fieldTypes.indexOfFirst { it == "number" })
            ?.mapNotNull { (it as? Number)?.toDouble() }
    }.orEmpty()
    val values = if (seriesValues.isNotEmpty()) seriesValues else frameValues
    val latest = reduceStat(values, calc)
    val display = when {
        latest == null -> "—"
        else -> formatValue(latest, panel.unit, panel.decimals)
    }
    // Scale value font to fit narrow cards without letter-wrapping.
    // Rough char-width heuristic: at 24sp, one char ~= 12dp; leave 16dp padding on each side.
    val availableDp = (cardWidth.value - 32f).coerceAtLeast(48f)
    val approxCharDp = availableDp / display.length.coerceAtLeast(1)
    val fontSize = (approxCharDp * 1.9f).coerceIn(16f, 34f)
    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
        Text(
            display,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = EnergyOrange,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TablePanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: return
    val rowCount = frame.rowCount.coerceAtMost(50)
    if (rowCount == 0) { PanelNoData(); return }
    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        // Header row.
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            frame.fieldNames.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.width(140.dp).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EnergyOrange,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
        // Body rows.
        for (r in 0 until rowCount) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (c in frame.columns.indices) {
                    val cell = frame.columns[c].getOrNull(r)
                    val text = when (cell) {
                        null -> "—"
                        is Number -> if (frame.fieldTypes.getOrNull(c) == "time") {
                            SimpleDateFormat("MMM dd HH:mm:ss", Locale.getDefault()).format(Date(cell.toLong()))
                        } else "%.2f".format(cell.toDouble())
                        else -> cell.toString()
                    }
                    Text(
                        text,
                        modifier = Modifier.width(140.dp).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsPanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: return
    val timeIdx = frame.fieldTypes.indexOfFirst { it == "time" }
    val lineIdx = frame.fieldNames.indexOfFirst { it.equals("Line", true) || it.equals("Body", true) || it.equals("Value", true) }
        .takeIf { it >= 0 } ?: frame.fieldTypes.indexOfFirst { it == "string" }
    if (lineIdx < 0) { PanelNoData(); return }
    val rowCount = frame.rowCount.coerceAtMost(50)
    Column(Modifier.fillMaxWidth()) {
        for (r in 0 until rowCount) {
            val ts = if (timeIdx >= 0) (frame.columns[timeIdx].getOrNull(r) as? Number)?.toLong() else null
            val line = frame.columns[lineIdx].getOrNull(r)?.toString().orEmpty()
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                if (ts != null) {
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts)),
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = EnergyOrange.copy(alpha = 0.9f),
                    )
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun HeatmapPanel(data: PanelData) {
    // Grafana heatmap frames are one column per Y-bucket, with a time column and numeric bucket cols.
    val frame = data.frames.firstOrNull() ?: run { PanelNoData(); return }
    val timeIdx = frame.fieldTypes.indexOfFirst { it == "time" }
    val bucketIdxs = frame.fieldTypes.mapIndexedNotNull { i, t -> if (t == "number" && i != timeIdx) i else null }
    if (timeIdx < 0 || bucketIdxs.isEmpty() || frame.rowCount == 0) { PanelNoData(); return }
    val cells = bucketIdxs.map { bi ->
        frame.columns[bi].map { (it as? Number)?.toDouble() ?: 0.0 }
    }
    val maxVal = cells.flatten().maxOrNull()?.takeIf { it > 0 } ?: run { PanelNoData(); return }
    val cols = frame.rowCount
    val rows = bucketIdxs.size
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val cw = size.width / cols
        val ch = size.height / rows
        for (r in 0 until rows) {
            val rowVals = cells[r]
            for (c in 0 until cols) {
                val v = rowVals.getOrNull(c) ?: 0.0
                if (v <= 0) continue
                val t = (v / maxVal).toFloat().coerceIn(0f, 1f)
                val color = Color(
                    red = 1f,
                    green = (1f - t * 0.9f).coerceIn(0f, 1f),
                    blue = (0.25f * (1f - t)).coerceIn(0f, 1f),
                    alpha = (0.35f + 0.55f * t).coerceIn(0f, 1f),
                )
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(c * cw, (rows - 1 - r) * ch),
                    size = androidx.compose.ui.geometry.Size(cw + 0.5f, ch + 0.5f),
                )
            }
        }
    }
}

@Composable
private fun StateTimelinePanel(data: PanelData) {
    // Draw one row per numeric/string series over time; color-code discrete state changes.
    val frame = data.frames.firstOrNull() ?: run { PanelNoData(); return }
    val timeIdx = frame.fieldTypes.indexOfFirst { it == "time" }
    if (timeIdx < 0 || frame.rowCount == 0) { PanelNoData(); return }
    val stateIdxs = frame.fieldTypes.mapIndexedNotNull { i, t -> if (i != timeIdx && (t == "number" || t == "string" || t == "boolean")) i else null }
    if (stateIdxs.isEmpty()) { PanelNoData(); return }
    val times = frame.columns[timeIdx].mapNotNull { (it as? Number)?.toLong() }
    if (times.size < 2) { PanelNoData(); return }
    val tMin = times.first().toDouble(); val tMax = times.last().toDouble()
    val span = (tMax - tMin).takeIf { it > 0 } ?: run { PanelNoData(); return }
    val palette = listOf(EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFEF4444), Color(0xFFFACC15), Color(0xFF22D3EE))
    val rowH = 24.dp
    Column(Modifier.fillMaxWidth()) {
        stateIdxs.forEach { idx ->
            val name = frame.fieldNames.getOrNull(idx).orEmpty()
            val col = frame.columns[idx]
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Canvas(Modifier.fillMaxWidth().height(rowH)) {
                var runStart = 0
                for (i in 1..times.size) {
                    val curr = col.getOrNull(i.coerceAtMost(col.size - 1))
                    val prev = col.getOrNull(runStart)
                    val boundary = i == times.size || curr != prev
                    if (boundary) {
                        val x0 = ((times[runStart].toDouble() - tMin) / span * size.width).toFloat()
                        val x1 = ((times[(i - 1).coerceIn(0, times.size - 1)].toDouble() - tMin) / span * size.width).toFloat()
                        val colorIdx = kotlin.math.abs(prev?.hashCode() ?: 0) % palette.size
                        val color = if (prev == null) Color.Transparent else palette[colorIdx]
                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x0, 0f),
                            size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(1f), size.height),
                        )
                        runStart = i
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PieChartPanel(data: PanelData) {
    val pairs = remember(data) { extractBarPairs(data) }
    if (pairs.isEmpty()) { PanelNoData(); return }
    val slices = pairs.take(8)
    val total = slices.sumOf { it.second }.takeIf { it > 0.0 } ?: run { PanelNoData(); return }
    val palette = listOf(EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF22D3EE), Color(0xFFEF4444))
    Row(Modifier.fillMaxWidth().height(220.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(180.dp).padding(16.dp)) {
            var start = -90f
            slices.forEachIndexed { idx, (_, v) ->
                val sweep = (v / total * 360.0).toFloat()
                drawArc(
                    color = palette[idx % palette.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth()) {
            slices.forEachIndexed { idx, (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(10.dp).background(palette[idx % palette.size], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$label · ${"%.0f%%".format(v / total * 100)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextPanel(panel: Panel) {
    val options = panel.options
    val content = options?.get("content")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
    val mode = options?.get("mode")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
    if (content.isBlank()) { PanelNoData(); return }
    val rendered = remember(content, mode) { renderTextPanel(content, mode) }
    Text(
        rendered,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

/** Best-effort strip of markdown/html to plain text — mobile card is too narrow for real rendering. */
private fun renderTextPanel(content: String, mode: String): String {
    var s = content
    if (mode.equals("html", true)) {
        s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    } else {
        // Markdown: keep bullets and headers as plain text.
        s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\[(.+?)]\\(.+?\\)"), "$1")
    }
    return s.trim()
}

@Composable
private fun UnsupportedPanel(type: String, message: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = EnergyOrange.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            message ?: "Panel type '$type' is not natively rendered yet — open in browser to view.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PanelLoading() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EnergyOrange, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PanelError(message: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PanelNoData() {
    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
        Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun LoadingBlock(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = EnergyOrange, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun ErrorBlock(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Couldn't load dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun EmptyDashboardBlock() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("This dashboard has no panels.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

private fun formatValue(v: Double, unit: String?, decimals: Int?): String {
    val d = decimals ?: 2
    val num = "%.${d}f".format(v)
    return when (unit) {
        null, "none", "short" -> shortNumber(v, d)
        "percent" -> "$num%"
        "percentunit" -> "%.${d}f%%".format(v * 100)
        "bytes", "decbytes", "bytesIEC" -> humanBytes(v, d, base = 1024)
        "binbytes" -> humanBytes(v, d, base = 1024)
        "decbytesSI" -> humanBytes(v, d, base = 1000)
        "Bps", "binBps", "bytespersecond" -> humanBytes(v, d, base = 1024) + "/s"
        "bps", "bits" -> humanBits(v, d)
        "s", "seconds" -> humanDuration(v, d)
        "ms", "millisecond" -> humanDuration(v / 1000.0, d)
        "dtdurationms" -> humanDuration(v / 1000.0, d)
        "dtdurations" -> humanDuration(v, d)
        "µs", "us", "microsecond" -> humanDuration(v / 1_000_000.0, d)
        "ns", "nanosecond" -> humanDuration(v / 1_000_000_000.0, d)
        "hertz", "hz" -> "$num Hz"
        "celsius" -> "$num °C"
        "fahrenheit" -> "$num °F"
        "currencyUSD" -> "$$num"
        "currencyEUR" -> "€$num"
        "currencyGBP" -> "£$num"
        "currencyINR" -> "₹$num"
        else -> "$num $unit"
    }
}

private fun shortNumber(v: Double, decimals: Int): String {
    val abs = kotlin.math.abs(v)
    return when {
        abs >= 1_000_000_000_000 -> "%.${decimals}f T".format(v / 1_000_000_000_000.0)
        abs >= 1_000_000_000 -> "%.${decimals}f B".format(v / 1_000_000_000.0)
        abs >= 1_000_000 -> "%.${decimals}f M".format(v / 1_000_000.0)
        abs >= 10_000 -> "%.${decimals}f K".format(v / 1_000.0)
        else -> "%.${decimals}f".format(v)
    }
}

private fun humanBytes(v: Double, decimals: Int, base: Int): String {
    val units = if (base == 1024)
        arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    else arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = v
    var idx = 0
    while (kotlin.math.abs(value) >= base && idx < units.size - 1) { value /= base; idx++ }
    return "%.${decimals}f %s".format(value, units[idx])
}

private fun humanBits(v: Double, decimals: Int): String {
    val units = arrayOf("bit", "Kbit", "Mbit", "Gbit", "Tbit")
    var value = v
    var idx = 0
    while (kotlin.math.abs(value) >= 1000 && idx < units.size - 1) { value /= 1000; idx++ }
    return "%.${decimals}f %s".format(value, units[idx])
}

/** Format seconds as a duration Grafana-style: 32s, 5.2 min, 3.4 h, 28.6 d, 4.1 w, 1.3 y. */
private fun humanDuration(sec: Double, decimals: Int): String {
    val abs = kotlin.math.abs(sec)
    return when {
        abs < 1 -> "%.${decimals}f ms".format(sec * 1000)
        abs < 60 -> if (sec == sec.toLong().toDouble()) "${sec.toLong()}s" else "%.${decimals}f s".format(sec)
        abs < 3600 -> "%.1f min".format(sec / 60.0)
        abs < 86400 -> "%.1f h".format(sec / 3600.0)
        abs < 604800 -> "%.1f d".format(sec / 86400.0)
        abs < 31_536_000 -> "%.1f w".format(sec / 604800.0)
        else -> "%.1f y".format(sec / 31_536_000.0)
    }
}
