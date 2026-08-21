package com.fusionlancers.grafusion.data.model

import kotlinx.serialization.json.JsonObject

/** A group of panels - either an ungrouped run (title=null) or a Grafana row (title + collapsed). */
data class PanelGroup(
    val title: String?,
    val collapsed: Boolean,
    val panels: List<Panel>,
)

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
    val min: Double? = null,
    val max: Double? = null,
    val thresholds: List<Threshold> = emptyList(),
    val description: String? = null,
    val mappings: List<ValueMapping> = emptyList(),
    val timeFrom: String? = null,
    val timeShift: String? = null,
    val repeat: String? = null,
    val repeatDirection: String? = null,
    /** Set on cloned panels produced by expandRepeats() - single-value override for [repeat]. */
    val repeatValue: String? = null,
)

/** fieldConfig.defaults.mappings entry. Grafana v9+ shape: { type, options }.
 *  We flatten to a list of (matcher, text, color) tuples that stat/gauge/table can consult. */
sealed class ValueMapping {
    abstract val text: String?
    abstract val color: String?
    /** Exact value match: "1" -> "OK". */
    data class Value(val value: String, override val text: String?, override val color: String?) : ValueMapping()
    /** Numeric range: from..to -> text. Either bound may be null for open-ended. */
    data class Range(val from: Double?, val to: Double?, override val text: String?, override val color: String?) : ValueMapping()
    /** Regex on string values. */
    data class Regex(val pattern: String, override val text: String?, override val color: String?) : ValueMapping()
    /** Special mapping for NaN, null, empty. */
    data class Special(val match: String, override val text: String?, override val color: String?) : ValueMapping()
}

/** One step from Grafana fieldConfig.defaults.thresholds.steps. */
data class Threshold(
    /** Cutoff value; null means "-infinity" (baseline). */
    val value: Double?,
    /** Grafana color token: "green", "red", "#ff8800", "rgba(...)", ... */
    val color: String,
)

/** One time-series returned by /api/ds/query - timestamps aligned with values. */
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
