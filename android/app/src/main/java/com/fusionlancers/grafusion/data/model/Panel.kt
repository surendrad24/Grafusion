package com.fusionlancers.grafusion.data.model

import kotlinx.serialization.json.JsonObject

/** A single panel definition parsed from a Grafana dashboard JSON. */
data class Panel(
    val id: Long,
    val title: String,
    val type: String,
    val datasourceUid: String?,
    val datasourceType: String?,
    val targets: List<JsonObject>,
    val gridX: Int,
    val gridY: Int,
    val gridW: Int,
    val gridH: Int,
    val unit: String?,
    val decimals: Int?,
    val options: JsonObject? = null,
)

/** One time-series returned by /api/ds/query — timestamps aligned with values. */
data class Series(
    val name: String,
    val timestamps: List<Long>,
    val values: List<Double?>,
)

data class PanelData(
    val series: List<Series>,
    val frames: List<RawFrame> = emptyList(),
    val error: String? = null,
)

/** Raw DataFrame preserving all columns (numeric + string) for tables and logs.
 *  [fieldLabels] captures per-field label maps (Prometheus attaches labels like {city, country, latitude, longitude}
 *  to a metric value field rather than emitting them as columns). Empty maps for non-Prometheus sources. */
data class RawFrame(
    val fieldNames: List<String>,
    val fieldTypes: List<String>,
    val columns: List<List<Any?>>,
    val fieldLabels: List<Map<String, String>> = emptyList(),
) {
    val rowCount: Int get() = columns.firstOrNull()?.size ?: 0
}
