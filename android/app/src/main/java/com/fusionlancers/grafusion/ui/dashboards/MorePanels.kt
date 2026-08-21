package com.fusionlancers.grafusion.ui.dashboards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.PanelData
import com.fusionlancers.grafusion.ui.theme.DataPurple
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

// ---------------------------------------------------------------------------
// News (RSS/Atom)
// ---------------------------------------------------------------------------

data class RssItem(val title: String, val link: String?, val pubDate: String?, val description: String?)

/**
 * Grafana's news panel options: { feedUrl: "https://...", showImage: true }.
 * We fetch the XML directly (server-side proxy is Grafana Enterprise), parse RSS 2.0
 * or Atom, and show a compact list. Titles link out to a browser.
 */
@Composable
fun NewsPanel(panel: Panel) {
    val feedUrl = remember(panel.options) {
        (panel.options?.get("feedUrl") as? JsonPrimitive)?.content
    }
    if (feedUrl.isNullOrBlank()) {
        UnsupportedPanelBanner("news", "No feedUrl configured")
        return
    }
    var items by remember(feedUrl) { mutableStateOf<List<RssItem>?>(null) }
    var error by remember(feedUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(feedUrl) {
        runCatching { withContext(Dispatchers.IO) { fetchRss(feedUrl) } }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: "Feed fetch failed" }
    }
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
        when {
            error != null -> ErrorLine(error!!)
            items == null -> LoadingLine()
            items!!.isEmpty() -> EmptyLine("Feed is empty")
            else -> items!!.take(20).forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(item.link?.let { Modifier.clickable { uriHandler.openUri(it) } } ?: Modifier),
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.link != null) EnergyOrange else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!item.pubDate.isNullOrBlank()) {
                        Text(
                            item.pubDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

private fun fetchRss(url: String): List<RssItem> {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10000
    conn.readTimeout = 15000
    conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.5")
    conn.setRequestProperty("User-Agent", "Grafusion/1.0")
    conn.inputStream.use { input ->
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder().parse(input)
        doc.documentElement.normalize()
        val itemNodes = doc.getElementsByTagName("item")
        if (itemNodes.length > 0) {
            return (0 until itemNodes.length).map { i ->
                val n = itemNodes.item(i)
                val map = childText(n)
                RssItem(
                    title = map["title"].orEmpty(),
                    link = map["link"],
                    pubDate = map["pubDate"],
                    description = map["description"],
                )
            }.filter { it.title.isNotBlank() }
        }
        // Atom fallback: <entry> with <title>, <link href=..>, <updated>
        val entries = doc.getElementsByTagName("entry")
        return (0 until entries.length).map { i ->
            val n = entries.item(i)
            val map = childText(n)
            RssItem(
                title = map["title"].orEmpty(),
                link = map["linkHref"] ?: map["link"],
                pubDate = map["updated"] ?: map["published"],
                description = map["summary"] ?: map["content"],
            )
        }.filter { it.title.isNotBlank() }
    }
}

private fun childText(n: org.w3c.dom.Node): Map<String, String> {
    val out = mutableMapOf<String, String>()
    val kids = n.childNodes
    for (i in 0 until kids.length) {
        val c = kids.item(i) ?: continue
        val name = c.nodeName ?: continue
        if (name == "link" && c.attributes?.getNamedItem("href") != null) {
            out["linkHref"] = c.attributes.getNamedItem("href").nodeValue.orEmpty()
        }
        val txt = c.textContent?.trim().orEmpty()
        if (txt.isNotEmpty() && name !in out) out[name] = txt
    }
    return out
}

// ---------------------------------------------------------------------------
// Node graph
// ---------------------------------------------------------------------------

/**
 * Node graph frames: Grafana returns two frames, one with node columns (id, title,
 * mainStat, ...) and one with edge columns (id, source, target). We don't do a
 * force-directed layout; we render a summary + node/edge lists so users get the
 * shape of the graph without a heavy solver.
 */
@Composable
fun NodeGraphPanel(data: PanelData) {
    if (data.frames.isEmpty()) { PanelNoDataLine(); return }
    val nodesFrame = data.frames.firstOrNull { f -> f.fieldNames.any { it.equals("id", true) } && f.fieldNames.any { it.contains("title", true) || it.contains("main", true) } }
    val edgesFrame = data.frames.firstOrNull { f -> f.fieldNames.any { it.equals("source", true) } && f.fieldNames.any { it.equals("target", true) } }
    val nodeCount = nodesFrame?.columns?.firstOrNull()?.size ?: 0
    val edgeCount = edgesFrame?.columns?.firstOrNull()?.size ?: 0
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SummaryChip("$nodeCount nodes", EnergyOrange)
            Spacer(Modifier.width(8.dp))
            SummaryChip("$edgeCount edges", DataPurple)
        }
        if (nodesFrame != null) {
            val idIdx = nodesFrame.fieldNames.indexOfFirst { it.equals("id", true) }
            val titleIdx = nodesFrame.fieldNames.indexOfFirst { it.contains("title", true) }.takeIf { it >= 0 }
                ?: nodesFrame.fieldNames.indexOfFirst { it.contains("main", true) }
            Spacer(Modifier.height(8.dp))
            Text("Nodes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            val rows = min(20, nodeCount)
            for (r in 0 until rows) {
                val id = nodesFrame.columns.getOrNull(idIdx)?.getOrNull(r)?.toString().orEmpty()
                val title = titleIdx.takeIf { it >= 0 }?.let { nodesFrame.columns.getOrNull(it)?.getOrNull(r)?.toString() }.orEmpty()
                Text(
                    if (title.isBlank()) id else "$id  $title",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (nodeCount > rows) Text("+${nodeCount - rows} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

// ---------------------------------------------------------------------------
// Flamegraph
// ---------------------------------------------------------------------------

/**
 * Flamegraph frames (grafana-flamegraph-panel / pyroscope): columns are
 * level (int), value (long), self (long), label (string). We render each level
 * as a horizontal row of bars proportional to value, tinted by depth.
 */
@Composable
fun FlamegraphPanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: run { PanelNoDataLine(); return }
    val levelIdx = frame.fieldNames.indexOfFirst { it.equals("level", true) }
    val valueIdx = frame.fieldNames.indexOfFirst { it.equals("value", true) }
    val labelIdx = frame.fieldNames.indexOfFirst { it.equals("label", true) }
    if (levelIdx < 0 || valueIdx < 0 || labelIdx < 0) { UnsupportedPanelBanner("flamegraph", "Missing level/value/label columns"); return }
    val rowCount = frame.columns[levelIdx].size
    val levels = (0 until rowCount).map { (frame.columns[levelIdx][it] as? Number)?.toInt() ?: 0 }
    val values = (0 until rowCount).map { (frame.columns[valueIdx][it] as? Number)?.toDouble() ?: 0.0 }
    val labels = (0 until rowCount).map { frame.columns[labelIdx][it]?.toString().orEmpty() }
    val maxLevel = (levels.maxOrNull() ?: 0).coerceAtLeast(0)
    val root = values.firstOrNull() ?: return
    if (root <= 0.0) { PanelNoDataLine(); return }
    Column(Modifier.fillMaxWidth()) {
        for (lvl in 0..maxLevel) {
            val rowValues = mutableListOf<Pair<String, Double>>()
            for (i in 0 until rowCount) if (levels[i] == lvl) rowValues += labels[i] to values[i]
            if (rowValues.isEmpty()) continue
            val sum = rowValues.sumOf { it.second }.coerceAtLeast(0.0001)
            Row(Modifier.fillMaxWidth().height(18.dp).padding(vertical = 1.dp)) {
                rowValues.forEach { (label, v) ->
                    Box(
                        modifier = Modifier
                            .weight((v / sum).toFloat().coerceAtLeast(0.001f))
                            .background(flameColor(lvl, maxLevel), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun flameColor(level: Int, maxLevel: Int): Color {
    val t = if (maxLevel == 0) 0f else level.toFloat() / maxLevel
    val r = 1f
    val g = (0.55f - t * 0.4f).coerceIn(0.1f, 1f)
    val b = (0.2f + t * 0.1f).coerceIn(0f, 1f)
    return Color(r, g, b, 0.9f)
}

// ---------------------------------------------------------------------------
// Traces (Tempo/Jaeger)
// ---------------------------------------------------------------------------

/**
 * Traces frames come from Tempo with columns like traceID, spanID, parentSpanID,
 * operationName, serviceName, startTime, duration. We show span rows sorted by
 * startTime with a small duration bar - no waterfall relationships yet.
 */
@Composable
fun TracesPanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: run { PanelNoDataLine(); return }
    val opIdx = frame.fieldNames.indexOfFirst { it.contains("operation", true) || it.equals("name", true) }
    val svcIdx = frame.fieldNames.indexOfFirst { it.contains("service", true) }
    val durIdx = frame.fieldNames.indexOfFirst { it.contains("duration", true) }
    if (opIdx < 0 && svcIdx < 0) { UnsupportedPanelBanner("traces", "No span columns detected"); return }
    val n = frame.columns.firstOrNull()?.size ?: 0
    val durations = (0 until n).map { i -> (frame.columns.getOrNull(durIdx)?.getOrNull(i) as? Number)?.toDouble() ?: 0.0 }
    val maxDur = (durations.maxOrNull() ?: 1.0).coerceAtLeast(0.001)
    Column(Modifier.fillMaxWidth()) {
        val rows = min(n, 20)
        for (i in 0 until rows) {
            val svc = svcIdx.takeIf { it >= 0 }?.let { frame.columns[it].getOrNull(i)?.toString() }.orEmpty()
            val op = opIdx.takeIf { it >= 0 }?.let { frame.columns[it].getOrNull(i)?.toString() }.orEmpty()
            val dur = durations[i]
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (svc.isBlank()) op else "$svc/$op",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .weight((dur / maxDur).toFloat().coerceAtLeast(0.02f))
                        .height(8.dp)
                        .background(DataPurple, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(6.dp))
                Text(formatDurationMs(dur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        if (n > rows) Text("+${n - rows} more spans", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

private fun formatDurationMs(v: Double): String = when {
    v >= 1000 -> "${(v / 1000).toInt()} s"
    v >= 1 -> "${v.toInt()} ms"
    else -> "%.2f ms".format(Locale.US, v)
}

// ---------------------------------------------------------------------------
// Canvas
// ---------------------------------------------------------------------------

/**
 * Canvas panels store elements in options.root.elements as an array with per-
 * element x/y/width/height + type ("rectangle", "text", "icon", ...). We render
 * rectangles and text so simple status boards work; complex elements fall through.
 */
@Composable
fun CanvasPanel(panel: Panel) {
    val root = panel.options?.get("root") as? JsonObject
    val elements = (root?.get("elements") as? JsonArray) ?: run {
        UnsupportedPanelBanner("canvas", "No elements"); return
    }
    Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            for (el in elements) {
                val o = el as? JsonObject ?: continue
                val type = (o["type"] as? JsonPrimitive)?.content ?: continue
                val place = o["placement"] as? JsonObject
                val x = ((place?.get("left") as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f)
                val y = ((place?.get("top") as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f)
                val ew = ((place?.get("width") as? JsonPrimitive)?.content?.toFloatOrNull() ?: 40f)
                val eh = ((place?.get("height") as? JsonPrimitive)?.content?.toFloatOrNull() ?: 20f)
                val bg = (((o["background"] as? JsonObject)?.get("color") as? JsonObject)?.get("fixed") as? JsonPrimitive)?.content
                val color = bg?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: EnergyOrange
                when (type) {
                    "rectangle", "metric-value" -> drawRect(color = color.copy(alpha = 0.8f), topLeft = Offset(x, y), size = Size(ew, eh))
                    "ellipse" -> drawArc(color = color.copy(alpha = 0.8f), startAngle = 0f, sweepAngle = 360f, useCenter = true, topLeft = Offset(x, y), size = Size(ew, eh))
                    else -> drawRect(color = color.copy(alpha = 0.3f), topLeft = Offset(x, y), size = Size(ew, eh), style = Stroke(width = 1.5f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared UI helpers
// ---------------------------------------------------------------------------

@Composable
private fun SummaryChip(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun UnsupportedPanelBanner(type: String, msg: String) {
    Text("$type: $msg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}

@Composable private fun PanelNoDataLine() = Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
@Composable private fun LoadingLine() = Row(verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = EnergyOrange)
    Spacer(Modifier.width(8.dp))
    Text("Loading...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}
@Composable private fun ErrorLine(msg: String) = Text("Error: $msg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
@Composable private fun EmptyLine(msg: String) = Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

private fun min(a: Int, b: Int) = if (a < b) a else b
