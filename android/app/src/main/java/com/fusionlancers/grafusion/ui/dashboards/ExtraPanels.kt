package com.fusionlancers.grafusion.ui.dashboards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.api.GrafanaAnnotation
import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.PanelData
import com.fusionlancers.grafusion.data.model.RawFrame
import com.fusionlancers.grafusion.ui.theme.DataPurple
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Histogram: Grafana v9+ frames come back as either
 *   [BucketMin, BucketMax, count, count, ...] (server-side histogram transform), or
 *   [x-numeric, count-numeric] (client transform).
 * We treat the first numeric column as the bucket label and remaining numeric columns as
 * separate histogram bars per series.
 */
@Composable
internal fun HistogramPanel(panel: Panel, data: PanelData) {
    val frame = data.frames.firstOrNull() ?: run { PanelNoDataMsg(); return }
    val numIdxs = frame.fieldTypes.mapIndexedNotNull { i, t -> if (t == "number") i else null }
    if (numIdxs.size < 2 || frame.rowCount == 0) { PanelNoDataMsg(); return }
    val bucketIdx = numIdxs.first()
    val countIdxs = numIdxs.drop(1)
    val bucketLabels = frame.columns[bucketIdx].map { formatBucket(it, panel.unit) }
    val series = countIdxs.map { i ->
        frame.fieldNames.getOrNull(i).orEmpty() to frame.columns[i].map { (it as? Number)?.toDouble() ?: 0.0 }
    }
    val maxVal = series.flatMap { it.second }.maxOrNull()?.takeIf { it > 0 } ?: run { PanelNoDataMsg(); return }
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val groupCount = frame.rowCount
            val groupW = size.width / groupCount
            val gap = groupW * 0.15f
            val barW = (groupW - gap) / series.size.coerceAtLeast(1)
            for (g in 0 until groupCount) {
                for ((sIdx, s) in series.withIndex()) {
                    val v = s.second.getOrNull(g) ?: 0.0
                    val h = (v / maxVal * size.height).toFloat().coerceAtLeast(1f)
                    val x = g * groupW + gap / 2f + sIdx * barW
                    drawRect(
                        color = histogramColor(sIdx),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW - 1f, h),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(bucketLabels.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(bucketLabels.lastOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

private fun histogramColor(idx: Int): Color = listOf(
    EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF22D3EE),
)[idx % 7]

private fun formatBucket(v: Any?, unit: String?): String {
    val d = (v as? Number)?.toDouble() ?: return v?.toString().orEmpty()
    return when {
        kotlin.math.abs(d) >= 1000 -> String.format(Locale.US, "%.1fk", d / 1000)
        d == d.toLong().toDouble() -> d.toLong().toString()
        else -> String.format(Locale.US, "%.2f", d)
    }
}

/**
 * Candlestick: frame is [time, open, high, low, close]. Draws a classic OHLC bar with wick
 * spanning high..low and a filled body between open..close. Green when close > open, red
 * otherwise - identical to Grafana's default candlestick colors.
 */
@Composable
internal fun CandlestickPanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: run { PanelNoDataMsg(); return }
    val timeIdx = frame.fieldTypes.indexOfFirst { it == "time" }
    if (timeIdx < 0 || frame.rowCount < 2) { PanelNoDataMsg(); return }
    val ohlc = findOhlcColumns(frame) ?: run { PanelNoDataMsg(); return }
    val times = frame.columns[timeIdx].mapNotNull { (it as? Number)?.toLong() }
    val opens = frame.columns[ohlc.open].map { (it as? Number)?.toDouble() ?: 0.0 }
    val highs = frame.columns[ohlc.high].map { (it as? Number)?.toDouble() ?: 0.0 }
    val lows = frame.columns[ohlc.low].map { (it as? Number)?.toDouble() ?: 0.0 }
    val closes = frame.columns[ohlc.close].map { (it as? Number)?.toDouble() ?: 0.0 }
    val globalMin = lows.min()
    val globalMax = highs.max()
    val span = (globalMax - globalMin).takeIf { it > 0 } ?: run { PanelNoDataMsg(); return }
    val up = Color(0xFF10B981)
    val down = Color(0xFFEF4444)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).toArgb()
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val n = closes.size
            val bandW = size.width / n
            val bodyW = bandW * 0.6f
            for (i in 0 until n) {
                val cx = bandW * i + bandW / 2f
                val hy = ((globalMax - highs[i]) / span).toFloat() * size.height
                val ly = ((globalMax - lows[i]) / span).toFloat() * size.height
                val oy = ((globalMax - opens[i]) / span).toFloat() * size.height
                val cy = ((globalMax - closes[i]) / span).toFloat() * size.height
                val color = if (closes[i] >= opens[i]) up else down
                // Wick.
                drawLine(
                    color = color,
                    start = Offset(cx, hy),
                    end = Offset(cx, ly),
                    strokeWidth = 1.5f,
                )
                // Body.
                val top = min(oy, cy)
                val bot = max(oy, cy)
                drawRect(
                    color = color,
                    topLeft = Offset(cx - bodyW / 2f, top),
                    size = Size(bodyW, (bot - top).coerceAtLeast(1f)),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            Text(fmt.format(Date(times.first())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(fmt.format(Date(times.last())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

private data class OhlcIdx(val open: Int, val high: Int, val low: Int, val close: Int)

private fun findOhlcColumns(frame: RawFrame): OhlcIdx? {
    fun find(vararg names: String): Int = frame.fieldNames.indexOfFirst { fn ->
        names.any { it.equals(fn, ignoreCase = true) }
    }
    val o = find("open"); val h = find("high"); val l = find("low"); val c = find("close")
    if (o < 0 || h < 0 || l < 0 || c < 0) return null
    return OhlcIdx(o, h, l, c)
}

/**
 * Trend: like a timeseries but X is any numeric column (order matters, no time). Renders
 * as a smooth line plot with dots so a small trend reads as a mini-chart.
 */
@Composable
internal fun TrendPanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: run { PanelNoDataMsg(); return }
    val numIdxs = frame.fieldTypes.mapIndexedNotNull { i, t -> if (t == "number") i else null }
    if (numIdxs.size < 2 || frame.rowCount < 2) { PanelNoDataMsg(); return }
    val xIdx = numIdxs.first()
    val xs = frame.columns[xIdx].mapNotNull { (it as? Number)?.toDouble() }
    if (xs.size < 2) { PanelNoDataMsg(); return }
    val yIdx = numIdxs[1]
    val ys = frame.columns[yIdx].mapNotNull { (it as? Number)?.toDouble() }
    val minX = xs.min(); val maxX = xs.max(); val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
    val minY = ys.min(); val maxY = ys.max(); val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val n = min(xs.size, ys.size)
        var prev: Offset? = null
        for (i in 0 until n) {
            val px = ((xs[i] - minX) / spanX).toFloat() * size.width
            val py = size.height - ((ys[i] - minY) / spanY).toFloat() * size.height
            val here = Offset(px, py)
            if (prev != null) drawLine(EnergyOrange, prev!!, here, strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawCircle(EnergyOrange, radius = 3f, center = here)
            prev = here
        }
    }
}

/**
 * XY chart / scatter: two numeric columns become (x, y) dots. Handy for correlation views
 * (latency vs. throughput, etc.). Grafana's own xychart panel maps to the same shape.
 */
@Composable
internal fun XyChartPanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: run { PanelNoDataMsg(); return }
    val numIdxs = frame.fieldTypes.mapIndexedNotNull { i, t -> if (t == "number") i else null }
    if (numIdxs.size < 2 || frame.rowCount == 0) { PanelNoDataMsg(); return }
    val xs = frame.columns[numIdxs[0]].mapNotNull { (it as? Number)?.toDouble() }
    val ys = frame.columns[numIdxs[1]].mapNotNull { (it as? Number)?.toDouble() }
    if (xs.isEmpty() || ys.isEmpty()) { PanelNoDataMsg(); return }
    val minX = xs.min(); val maxX = xs.max(); val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
    val minY = ys.min(); val maxY = ys.max(); val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            // subtle axis lines
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            drawLine(axisColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            val n = min(xs.size, ys.size)
            for (i in 0 until n) {
                val px = ((xs[i] - minX) / spanX).toFloat() * size.width
                val py = size.height - ((ys[i] - minY) / spanY).toFloat() * size.height
                drawCircle(EnergyOrange.copy(alpha = 0.75f), radius = 4f, center = Offset(px, py))
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                frame.fieldNames.getOrNull(numIdxs[0]).orEmpty() + " -> " + frame.fieldNames.getOrNull(numIdxs[1]).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Annotations list (Grafana "annolist"): shows recent dashboard annotations - deploy markers,
 * alerts, manual notes. Options honored: onlyFromThisDashboard, limit, tags.
 */
@Composable
internal fun AnnotationsListPanel(panel: Panel) {
    val container = LocalAppContainer.current
    val dashboardUid = LocalDashboardUid.current
    val options = panel.options
    val onlyThis = ((options?.get("onlyFromThisDashboard") as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toBooleanStrictOrNull()) ?: false
    val limit = ((options?.get("limit") as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toIntOrNull()) ?: 10
    val tags = remember(options) {
        (options?.get("tags") as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .orEmpty()
    }
    var annotations by remember { mutableStateOf<List<GrafanaAnnotation>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(panel.id, container, onlyThis, dashboardUid) {
        if (container == null) return@LaunchedEffect
        container.alertRepository.annotations(
            dashboardUid = if (onlyThis) dashboardUid else null,
            limit = limit.coerceIn(1, 500),
            tags = tags,
        ).onSuccess { annotations = it }.onFailure { error = it.message ?: "Failed to load annotations" }
    }
    when {
        container == null -> PanelNoDataMsg()
        error != null -> Text(
            error!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        annotations == null -> Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
            Text("Loading annotations...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        annotations!!.isEmpty() -> PanelNoDataMsg()
        else -> {
            val fmt = SimpleDateFormat("MMM d HH:mm", Locale.getDefault())
            Column(Modifier.fillMaxWidth()) {
                annotations!!.take(limit).forEach { ann ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .background(annotationDotColor(ann), RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ann.text.ifBlank { ann.alertName.orEmpty().ifBlank { "(no text)" } },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    fmt.format(Date(ann.time)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            if (ann.tags.isNotEmpty()) {
                                Text(
                                    ann.tags.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun annotationDotColor(ann: GrafanaAnnotation): Color = when {
    ann.alertId > 0 && ann.newState.equals("alerting", true) -> Color(0xFFEF4444)
    ann.alertId > 0 && ann.newState.equals("ok", true) -> Color(0xFF10B981)
    ann.alertId > 0 -> Color(0xFFF59E0B)
    "deploy" in ann.tags.map { it.lowercase() } -> Color(0xFF8B5CF6)
    else -> EnergyOrange
}

@Composable
private fun PanelNoDataMsg() {
    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
        Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
