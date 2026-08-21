package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.PanelData
import com.fusionlancers.grafusion.data.model.PanelGroup
import com.fusionlancers.grafusion.data.model.RawFrame
import com.fusionlancers.grafusion.data.model.Series
import com.fusionlancers.grafusion.data.model.Threshold
import com.fusionlancers.grafusion.data.model.ValueMapping
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object PanelParser {

    /** Extract flat panel list from a dashboard JSON, walking one level of row containers. */
    fun parsePanels(dashboard: JsonObject): List<Panel> {
        val raw = dashboard["panels"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<Panel>()
        var synthetic = 100000L
        for (element in raw) {
            val obj = element.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNullSafe() == "row") {
                obj["panels"]?.jsonArray?.forEach { child -> parseOne(child.jsonObject, ++synthetic)?.let(out::add) }
            } else {
                parseOne(obj, ++synthetic)?.let(out::add)
            }
        }
        return out
    }

    /** Group the dashboard's panels into rows (Grafana `type=row` containers) + one leading ungrouped run. */
    fun parseGroups(dashboard: JsonObject): List<PanelGroup> {
        val raw = dashboard["panels"]?.jsonArray ?: return emptyList()
        val groups = mutableListOf<PanelGroup>()
        val leading = mutableListOf<Panel>()
        var synthetic = 100000L
        for (element in raw) {
            val obj = element.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNullSafe() == "row") {
                if (leading.isNotEmpty()) {
                    groups += PanelGroup(title = null, collapsed = false, panels = leading.toList())
                    leading.clear()
                }
                val title = obj["title"]?.jsonPrimitive?.contentOrNullSafe().orEmpty().ifBlank { "Row" }
                val collapsed = obj["collapsed"]?.jsonPrimitive?.let { runCatching { it.content.toBooleanStrict() }.getOrNull() } ?: false
                val panels = obj["panels"]?.jsonArray
                    ?.mapNotNull { parseOne(it.jsonObject, ++synthetic) }
                    .orEmpty()
                groups += PanelGroup(title = title, collapsed = collapsed, panels = panels)
            } else {
                parseOne(obj, ++synthetic)?.let(leading::add)
            }
        }
        if (leading.isNotEmpty()) {
            groups += PanelGroup(title = null, collapsed = false, panels = leading.toList())
        }
        return groups
    }

    private fun parseOne(obj: JsonObject, fallbackId: Long): Panel? {
        val type = obj["type"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
        val id = obj["id"]?.jsonPrimitive?.longOrNull() ?: fallbackId
        val title = obj["title"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        val (dsUid, dsType) = obj["datasource"]?.let { ds ->
            when (ds) {
                is JsonObject -> ds["uid"]?.jsonPrimitive?.contentOrNullSafe() to ds["type"]?.jsonPrimitive?.contentOrNullSafe()
                is JsonPrimitive -> ds.contentOrNullSafe() to null
                else -> null to null
            }
        } ?: (null to null)
        val targets = obj["targets"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val gp = obj["gridPos"]?.jsonObject
        val fieldConfig = obj["fieldConfig"]?.jsonObject?.get("defaults")?.jsonObject
        val unit = fieldConfig?.get("unit")?.jsonPrimitive?.contentOrNullSafe()
        val decimals = fieldConfig?.get("decimals")?.jsonPrimitive?.intOrNullSafe()
        val min = fieldConfig?.get("min")?.jsonPrimitive?.doubleOrNullSafe()
        val max = fieldConfig?.get("max")?.jsonPrimitive?.doubleOrNullSafe()
        val thresholds = parseThresholds(fieldConfig)
        val mappings = parseMappings(fieldConfig)
        val description = obj["description"]?.jsonPrimitive?.contentOrNullSafe()
        val timeFrom = obj["timeFrom"]?.jsonPrimitive?.contentOrNullSafe()
        val timeShift = obj["timeShift"]?.jsonPrimitive?.contentOrNullSafe()
        val repeat = obj["repeat"]?.jsonPrimitive?.contentOrNullSafe()
        val repeatDirection = obj["repeatDirection"]?.jsonPrimitive?.contentOrNullSafe()
        return Panel(
            id = id,
            title = title,
            type = type,
            datasourceUid = dsUid,
            datasourceType = dsType,
            targets = targets,
            gridX = gp?.get("x")?.jsonPrimitive?.intOrNullSafe() ?: 0,
            gridY = gp?.get("y")?.jsonPrimitive?.intOrNullSafe() ?: 0,
            gridW = gp?.get("w")?.jsonPrimitive?.intOrNullSafe() ?: 24,
            gridH = gp?.get("h")?.jsonPrimitive?.intOrNullSafe() ?: 8,
            unit = unit,
            decimals = decimals,
            options = obj["options"] as? JsonObject,
            min = min,
            max = max,
            thresholds = thresholds,
            description = description?.takeIf { it.isNotBlank() },
            mappings = mappings,
            timeFrom = timeFrom?.takeIf { it.isNotBlank() },
            timeShift = timeShift?.takeIf { it.isNotBlank() },
            repeat = repeat?.takeIf { it.isNotBlank() },
            repeatDirection = repeatDirection?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseMappings(fieldConfig: JsonObject?): List<ValueMapping> {
        val arr = fieldConfig?.get("mappings")?.jsonArray ?: return emptyList()
        val out = mutableListOf<ValueMapping>()
        for (el in arr) {
            val obj = el as? JsonObject ?: continue
            val type = obj["type"]?.jsonPrimitive?.contentOrNullSafe() ?: continue
            val options = obj["options"] as? JsonObject ?: continue
            when (type) {
                "value" -> {
                    for ((k, v) in options) {
                        val vObj = v as? JsonObject ?: continue
                        out += ValueMapping.Value(
                            value = k,
                            text = vObj["text"]?.jsonPrimitive?.contentOrNullSafe(),
                            color = vObj["color"]?.jsonPrimitive?.contentOrNullSafe(),
                        )
                    }
                }
                "range" -> {
                    val result = options["result"] as? JsonObject
                    out += ValueMapping.Range(
                        from = options["from"]?.jsonPrimitive?.doubleOrNullSafe(),
                        to = options["to"]?.jsonPrimitive?.doubleOrNullSafe(),
                        text = result?.get("text")?.jsonPrimitive?.contentOrNullSafe(),
                        color = result?.get("color")?.jsonPrimitive?.contentOrNullSafe(),
                    )
                }
                "regex" -> {
                    val result = options["result"] as? JsonObject
                    val pattern = options["pattern"]?.jsonPrimitive?.contentOrNullSafe() ?: continue
                    out += ValueMapping.Regex(
                        pattern = pattern,
                        text = result?.get("text")?.jsonPrimitive?.contentOrNullSafe(),
                        color = result?.get("color")?.jsonPrimitive?.contentOrNullSafe(),
                    )
                }
                "special" -> {
                    val result = options["result"] as? JsonObject
                    val match = options["match"]?.jsonPrimitive?.contentOrNullSafe() ?: continue
                    out += ValueMapping.Special(
                        match = match,
                        text = result?.get("text")?.jsonPrimitive?.contentOrNullSafe(),
                        color = result?.get("color")?.jsonPrimitive?.contentOrNullSafe(),
                    )
                }
            }
        }
        return out
    }

    private fun parseThresholds(fieldConfig: JsonObject?): List<Threshold> {
        val steps = fieldConfig?.get("thresholds")?.jsonObject?.get("steps")?.jsonArray ?: return emptyList()
        return steps.mapNotNull { step ->
            val o = step as? JsonObject ?: return@mapNotNull null
            val color = o["color"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            val valueEl = o["value"]
            val value = when {
                valueEl == null || valueEl is JsonNull -> null
                valueEl is JsonPrimitive -> valueEl.doubleOrNullSafe()
                else -> null
            }
            Threshold(value = value, color = color)
        }
    }

    /**
     * Parse /api/ds/query response into flat Series list.
     * Handles the DataFrame v2 shape: results -> refId -> frames -> schema + data.values (time col, then value cols).
     */
    fun parseQueryResponse(response: JsonObject): PanelData {
        val results = response["results"]?.jsonObject ?: return PanelData(series = emptyList())
        val error = firstErrorMessage(results)
        val series = mutableListOf<Series>()
        val rawFrames = mutableListOf<RawFrame>()
        for ((_, refValue) in results) {
            val refObj = refValue.jsonObject
            val frames = refObj["frames"]?.jsonArray ?: continue
            for (frameEl in frames) {
                val frame = frameEl.jsonObject
                val values = frame["data"]?.jsonObject?.get("values")?.jsonArray ?: continue
                val schemaFields = frame["schema"]?.jsonObject?.get("fields")?.jsonArray

                // Build RawFrame preserving all columns and types.
                val fieldNames = mutableListOf<String>()
                val fieldTypes = mutableListOf<String>()
                val columns = mutableListOf<List<Any?>>()
                val fieldLabels = mutableListOf<Map<String, String>>()
                for (i in 0 until values.size) {
                    val meta = schemaFields?.getOrNull(i)?.jsonObject
                    fieldNames += meta?.get("name")?.jsonPrimitive?.contentOrNullSafe() ?: "field$i"
                    val ftype = meta?.get("type")?.jsonPrimitive?.contentOrNullSafe() ?: "unknown"
                    fieldTypes += ftype
                    columns += values[i].jsonArray.map { p -> primitiveToAny(p, ftype) }
                    val labelsObj = meta?.get("labels") as? JsonObject
                    fieldLabels += labelsObj?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNullSafe().orEmpty() } ?: emptyMap()
                }
                rawFrames += RawFrame(fieldNames, fieldTypes, columns, fieldLabels)

                // Build numeric Series when the frame is a time + numeric series shape.
                if (values.size >= 2) {
                    val firstIsTime = fieldTypes.firstOrNull() == "time" ||
                        columns.firstOrNull()?.firstOrNull() is Long
                    if (firstIsTime) {
                        val timeCol = columns[0].map { (it as? Number)?.toLong() ?: 0L }
                        for (i in 1 until columns.size) {
                            if (fieldTypes[i] == "string") continue
                            val valueCol = columns[i].map { v ->
                                when (v) {
                                    null -> null
                                    is Number -> v.toDouble()
                                    is String -> if (v == "NaN") null else v.toDoubleOrNull()
                                    else -> null
                                }
                            }
                            val meta = schemaFields?.getOrNull(i)?.jsonObject
                            val name = meta?.get("labels")?.jsonObject?.toSeriesLabel()
                                ?: fieldNames[i]
                            series += Series(name = name, timestamps = timeCol, values = valueCol)
                        }
                    }
                }
            }
        }
        return PanelData(series = series, frames = rawFrames, error = error)
    }

    private fun primitiveToAny(el: kotlinx.serialization.json.JsonElement, fieldType: String): Any? {
        if (el is JsonNull) return null
        val prim = el as? JsonPrimitive ?: return el.toString()
        val raw = runCatching { prim.content }.getOrNull() ?: return null
        return when (fieldType) {
            "time" -> raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
            "number" -> if (raw == "NaN") null else raw.toDoubleOrNull()
            "boolean" -> raw.toBooleanStrictOrNull()
            "string" -> raw
            else -> raw
        }
    }

    private fun firstErrorMessage(results: JsonObject): String? {
        for ((_, v) in results) {
            val obj = v.jsonObject
            obj["error"]?.jsonPrimitive?.contentOrNullSafe()?.let { return it }
            val status = obj["status"]?.jsonPrimitive?.intOrNullSafe() ?: continue
            if (status >= 400) return "HTTP $status"
        }
        return null
    }

    private fun JsonObject.toSeriesLabel(): String? {
        if (isEmpty()) return null
        return entries.joinToString(", ") { (k, v) -> "$k=${(v as? JsonPrimitive)?.contentOrNullSafe() ?: v}" }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = runCatching { content }.getOrNull()
    private fun JsonPrimitive.longOrNull(): Long? = runCatching { content.toLong() }.getOrNull() ?: runCatching { content.toDouble().toLong() }.getOrNull()
    private fun JsonPrimitive.intOrNullSafe(): Int? = runCatching { content.toInt() }.getOrNull()
    private fun JsonPrimitive.doubleOrNullSafe(): Double? = runCatching { content.toDouble() }.getOrNull()
}
