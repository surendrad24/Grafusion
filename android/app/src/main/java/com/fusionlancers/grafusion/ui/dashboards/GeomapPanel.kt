package com.fusionlancers.grafusion.ui.dashboards

import android.graphics.Paint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fusionlancers.grafusion.data.model.PanelData
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay
import android.graphics.Canvas as AndroidCanvas
import org.osmdroid.views.Projection
import android.graphics.Color as AndroidColor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun GeomapPanel(data: PanelData) {
    val ctx = LocalContext.current
    // osmdroid one-time config: set a user agent (required by OSM tile policy).
    remember(ctx.applicationContext) {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = ctx.cacheDir
            osmdroidTileCache = ctx.cacheDir.resolve("osmdroid-tiles")
        }
        Unit
    }

    val points = remember(data) { extractPoints(data) }
    if (points.isEmpty()) { PanelNoDataInline(); return }

    // Read the theme in composition, then push it into the AndroidView via the update block
    // so tiles re-tint when the user toggles Light/Dark without recreating the MapView.
    val dark = isSystemInDarkTheme()

    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp),
    ) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    isTilesScaledToDpi = true
                    minZoomLevel = 1.0
                    maxZoomLevel = 12.0
                    overlays.add(HeatDotsOverlay(points))
                    val bbox = boundsFor(points)
                    if (bbox != null) {
                        post { zoomToBoundingBox(bbox, false, 32) }
                    } else {
                        controller.setZoom(2.0)
                        controller.setCenter(GeoPoint(20.0, 0.0))
                    }
                }
            },
            update = { map ->
                // osmdroid ships a hue-inverting ColorMatrix that turns light Mapnik tiles
                // into a passable dark basemap. Reset when Light so we don't invert twice.
                map.overlayManager.tilesOverlay.setColorFilter(
                    if (dark) TilesOverlay.INVERT_COLORS else null
                )
                map.overlays.removeAll { it is HeatDotsOverlay }
                map.overlays.add(HeatDotsOverlay(points, darkTheme = dark))
                boundsFor(points)?.let { map.post { map.zoomToBoundingBox(it, false, 32) } }
                map.invalidate()
            },
        )
    }
}

private data class AttackPoint(val lat: Double, val lon: Double, val weight: Double, val label: String)

private fun extractPoints(data: PanelData): List<AttackPoint> {
    val out = mutableListOf<AttackPoint>()
    for (frame in data.frames) {
        // Case A: Prometheus format=table + instant=true - one frame per label combo.
        // Labels are on the value field's metadata, values in the number column.
        val valueIdx = frame.fieldTypes.indexOfFirst { it == "number" }
        if (valueIdx >= 0 && frame.fieldLabels.getOrNull(valueIdx)?.isNotEmpty() == true) {
            val labels = frame.fieldLabels[valueIdx]
            val lat = labels["latitude"]?.toDoubleOrNull() ?: labels["lat"]?.toDoubleOrNull()
            val lon = labels["longitude"]?.toDoubleOrNull() ?: labels["lon"]?.toDoubleOrNull() ?: labels["lng"]?.toDoubleOrNull()
            if (lat != null && lon != null) {
                val v = (frame.columns[valueIdx].lastOrNull { it != null } as? Number)?.toDouble() ?: 1.0
                val city = labels["city"].orEmpty()
                val country = labels["country"].orEmpty()
                val label = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
                out += AttackPoint(lat, lon, v, label)
                continue
            }
        }
        // Case B: fields include latitude/longitude columns directly (e.g. Elasticsearch).
        val latIdx = frame.fieldNames.indexOfFirst { it.equals("latitude", true) || it.equals("lat", true) }
        val lonIdx = frame.fieldNames.indexOfFirst { it.equals("longitude", true) || it.equals("lon", true) || it.equals("lng", true) }
        if (latIdx < 0 || lonIdx < 0) continue
        val valColIdx = if (valueIdx >= 0) valueIdx else -1
        val cityIdx = frame.fieldNames.indexOfFirst { it.equals("city", true) }
        val countryIdx = frame.fieldNames.indexOfFirst { it.equals("country", true) }
        for (r in 0 until frame.rowCount) {
            val lat = (frame.columns[latIdx].getOrNull(r) as? Number)?.toDouble()
                ?: (frame.columns[latIdx].getOrNull(r) as? String)?.toDoubleOrNull() ?: continue
            val lon = (frame.columns[lonIdx].getOrNull(r) as? Number)?.toDouble()
                ?: (frame.columns[lonIdx].getOrNull(r) as? String)?.toDoubleOrNull() ?: continue
            val w = if (valColIdx >= 0) {
                (frame.columns[valColIdx].getOrNull(r) as? Number)?.toDouble() ?: 1.0
            } else 1.0
            val city = cityIdx.takeIf { it >= 0 }?.let { frame.columns[it].getOrNull(r)?.toString().orEmpty() }.orEmpty()
            val country = countryIdx.takeIf { it >= 0 }?.let { frame.columns[it].getOrNull(r)?.toString().orEmpty() }.orEmpty()
            val label = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
            out += AttackPoint(lat, lon, w, label)
        }
    }
    return out
}

private fun boundsFor(points: List<AttackPoint>): BoundingBox? {
    if (points.isEmpty()) return null
    var north = -90.0; var south = 90.0; var east = -180.0; var west = 180.0
    for (p in points) {
        north = max(north, p.lat); south = min(south, p.lat)
        east = max(east, p.lon); west = min(west, p.lon)
    }
    // Pad so markers aren't at the very edge.
    val padLat = (north - south).coerceAtLeast(1.0) * 0.1
    val padLon = (east - west).coerceAtLeast(1.0) * 0.1
    return BoundingBox(
        (north + padLat).coerceAtMost(85.0),
        (east + padLon).coerceAtMost(180.0),
        (south - padLat).coerceAtLeast(-85.0),
        (west - padLon).coerceAtLeast(-180.0),
    )
}

/** Custom overlay draws a color/size-scaled dot per point, cheaper than osmdroid Markers for 200+ items. */
private class HeatDotsOverlay(private val points: List<AttackPoint>, private val darkTheme: Boolean = false) : Overlay() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // Light stroke on dark tiles, dark stroke on light tiles - dots stay legible either way.
        color = if (darkTheme) AndroidColor.argb(200, 235, 235, 245)
                else AndroidColor.argb(180, 20, 20, 30)
        strokeWidth = 1.2f
    }

    private val maxWeight = points.maxOfOrNull { it.weight } ?: 1.0

    override fun draw(canvas: AndroidCanvas, projection: Projection) {
        val out = android.graphics.Point()
        for (p in points) {
            projection.toPixels(GeoPoint(p.lat, p.lon), out)
            val logW = ln((p.weight + 1.0)) / ln((maxWeight + 1.0)).coerceAtLeast(0.001)
            val radius = (4f + (16f * logW.toFloat())).coerceIn(4f, 22f)
            fill.color = heatColor(logW.toFloat())
            canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), radius, fill)
            canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), radius, stroke)
        }
    }

    private fun heatColor(t: Float): Int {
        // t 0..1 → yellow → orange → red, matching Grafana continuous-YlRd.
        val a = 220
        val tt = t.coerceIn(0f, 1f)
        val r = 255
        val g = (255 * (1f - tt * 0.9f)).toInt().coerceIn(0, 255)
        val b = (60 * (1f - tt)).toInt().coerceIn(0, 255)
        return AndroidColor.argb(a, r, g, b)
    }
}

@Composable
private fun PanelNoDataInline() {
    androidx.compose.material3.Text(
        text = "No geo data",
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
    )
}
