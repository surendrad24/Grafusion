package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.model.RawFrame
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Applies Grafana `panel.transformations` to raw frames before table/log rendering.
 * We implement the transformers seen in production dashboards; unknown transformers pass through.
 *
 * Supported ids:
 *   - labelsToFields:        promote Prometheus per-field labels into new columns
 *   - organize:              rename/hide/reorder columns
 *   - merge:                 concatenate all frames row-wise, aligning columns by name
 *   - groupingToMatrix:      pivot rows into a 2D matrix keyed by (rowField, columnField, valueField)
 *   - calculateField (reduceRow): append a computed column that reduces a set of numeric columns per row
 *   - filterFieldsByName:    keep only columns whose name matches an include list / regex
 *   - reduce:                collapse each numeric column into a single-row summary
 */
internal object TableTransformer {

    fun apply(frames: List<RawFrame>, transformations: List<JsonObject>): List<RawFrame> {
        if (transformations.isEmpty()) return frames
        var current = frames
        for (t in transformations) {
            val id = t["id"]?.jsonPrimitive?.contentOrNull() ?: continue
            val opts = t["options"] as? JsonObject ?: JsonObject(emptyMap())
            current = when (id) {
                "labelsToFields" -> current.map(::labelsToFields)
                "organize" -> current.map { organize(it, opts) }
                "merge" -> listOf(mergeFrames(current))
                "groupingToMatrix" -> current.map { groupingToMatrix(it, opts) }
                "calculateField" -> current.map { calculateField(it, opts) }
                "filterFieldsByName" -> current.map { filterFieldsByName(it, opts) }
                "reduce" -> current.map { reduce(it, opts) }
                else -> current
            }
        }
        return current
    }

    private fun labelsToFields(frame: RawFrame): RawFrame {
        // Promote each unique label key across fieldLabels into its own column.
        val labelKeys = linkedSetOf<String>()
        frame.fieldLabels.forEach { labelKeys.addAll(it.keys) }
        if (labelKeys.isEmpty()) return frame

        // For each label key, produce a column with per-row values taken from the labels of the
        // *value* field for that row. Prometheus DataFrames typically have one time col + N metric
        // cols, where labels live on the metric cols. In tabular results the frame is already flat
        // and each numeric column carries a distinct label set.
        val newNames = frame.fieldNames.toMutableList()
        val newTypes = frame.fieldTypes.toMutableList()
        val newColumns = frame.columns.toMutableList()
        val rows = frame.rowCount
        for (key in labelKeys) {
            // Use the first non-empty field-label value for this key across all fields.
            val v = frame.fieldLabels.firstNotNullOfOrNull { it[key]?.takeIf { s -> s.isNotEmpty() } } ?: ""
            newNames += key
            newTypes += "string"
            newColumns += List(rows) { v }
        }
        return RawFrame(newNames, newTypes, newColumns, frame.fieldLabels + List(labelKeys.size) { emptyMap() })
    }

    private fun organize(frame: RawFrame, opts: JsonObject): RawFrame {
        val excludeByName = (opts["excludeByName"] as? JsonObject)
            ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.booleanOrNull ?: false }
            ?: emptyMap()
        val renameByName = (opts["renameByName"] as? JsonObject)
            ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull().orEmpty() }
            ?: emptyMap()
        val indexByName = (opts["indexByName"] as? JsonObject)
            ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull()?.toIntOrNull() ?: Int.MAX_VALUE }
            ?: emptyMap()

        val keptIdx = frame.fieldNames.indices.filter { i -> excludeByName[frame.fieldNames[i]] != true }
        // Sort by explicit index if provided; unspecified keep original relative order at the end.
        val ordered = keptIdx.sortedWith(compareBy(
            { indexByName[frame.fieldNames[it]] ?: Int.MAX_VALUE },
            { it },
        ))
        val names = ordered.map { i ->
            val rn = renameByName[frame.fieldNames[i]]
            if (!rn.isNullOrBlank()) rn else frame.fieldNames[i]
        }
        val types = ordered.map { frame.fieldTypes[it] }
        val cols = ordered.map { frame.columns[it] }
        val labels = ordered.map { frame.fieldLabels.getOrElse(it) { emptyMap() } }
        return RawFrame(names, types, cols, labels)
    }

    private fun mergeFrames(frames: List<RawFrame>): RawFrame {
        if (frames.isEmpty()) return RawFrame(emptyList(), emptyList(), emptyList())
        if (frames.size == 1) return frames[0]
        // Union of column names, preserving first-seen order.
        val nameOrder = linkedSetOf<String>()
        for (f in frames) nameOrder.addAll(f.fieldNames)
        val names = nameOrder.toList()
        val types = names.map { n -> frames.firstNotNullOfOrNull { f -> f.fieldTypes.getOrNull(f.fieldNames.indexOf(n)) } ?: "string" }
        val columns = names.map { mutableListOf<Any?>() }.toMutableList()
        for (f in frames) {
            val rows = f.rowCount
            for (r in 0 until rows) {
                for ((ci, n) in names.withIndex()) {
                    val srcIdx = f.fieldNames.indexOf(n)
                    columns[ci] += if (srcIdx >= 0) f.columns[srcIdx].getOrNull(r) else null
                }
            }
        }
        return RawFrame(names, types, columns, List(names.size) { emptyMap() })
    }

    private fun groupingToMatrix(frame: RawFrame, opts: JsonObject): RawFrame {
        val rowField = opts["rowField"]?.jsonPrimitive?.contentOrNull() ?: return frame
        val colField = opts["columnField"]?.jsonPrimitive?.contentOrNull() ?: return frame
        val valField = opts["valueField"]?.jsonPrimitive?.contentOrNull() ?: return frame
        val rowIdx = frame.fieldNames.indexOf(rowField).takeIf { it >= 0 } ?: return frame
        val colIdx = frame.fieldNames.indexOf(colField).takeIf { it >= 0 } ?: return frame
        val valIdx = frame.fieldNames.indexOf(valField).takeIf { it >= 0 } ?: return frame

        val rowKeys = linkedSetOf<String>()
        val colKeys = linkedSetOf<String>()
        val cells = mutableMapOf<Pair<String, String>, Any?>()
        for (r in 0 until frame.rowCount) {
            val rk = frame.columns[rowIdx].getOrNull(r)?.toString() ?: continue
            val ck = frame.columns[colIdx].getOrNull(r)?.toString() ?: continue
            rowKeys += rk
            colKeys += ck
            cells[rk to ck] = frame.columns[valIdx].getOrNull(r)
        }
        val rowList = rowKeys.toList()
        val colList = colKeys.toList()
        val names = listOf(rowField) + colList
        val types = listOf("string") + List(colList.size) { frame.fieldTypes[valIdx] }
        val columns = buildList<List<Any?>> {
            add(rowList)
            for (c in colList) {
                add(rowList.map { r -> cells[r to c] })
            }
        }
        return RawFrame(names, types, columns, List(names.size) { emptyMap() })
    }

    private fun calculateField(frame: RawFrame, opts: JsonObject): RawFrame {
        // Only handle mode=reduceRow which is what production dashboards use.
        val mode = opts["mode"]?.jsonPrimitive?.contentOrNull() ?: "reduceRow"
        if (mode != "reduceRow") return frame
        val reduce = opts["reduce"] as? JsonObject
        val reducer = reduce?.get("reducer")?.jsonPrimitive?.contentOrNull() ?: "sum"
        val includeArr = reduce?.get("include")?.let { runCatching { it.jsonArray }.getOrNull() }
        val include = includeArr?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }?.toSet()
        val newName = opts["alias"]?.jsonPrimitive?.contentOrNull()
            ?: "$reducer"

        val numericIdx = frame.fieldNames.mapIndexedNotNull { i, name ->
            if (frame.fieldTypes[i] == "number" && (include == null || name in include)) i else null
        }
        if (numericIdx.isEmpty()) return frame

        val newCol = (0 until frame.rowCount).map { r ->
            val vals = numericIdx.mapNotNull { (frame.columns[it].getOrNull(r) as? Number)?.toDouble() }
            reduceValues(vals, reducer)
        }
        val replace = opts["replaceFields"]?.jsonPrimitive?.booleanOrNull ?: false
        return if (replace) {
            val timeIdx = frame.fieldTypes.indexOfFirst { it == "time" }
            val names = mutableListOf<String>()
            val types = mutableListOf<String>()
            val cols = mutableListOf<List<Any?>>()
            if (timeIdx >= 0) { names += frame.fieldNames[timeIdx]; types += "time"; cols += frame.columns[timeIdx] }
            names += newName; types += "number"; cols += newCol
            RawFrame(names, types, cols, List(names.size) { emptyMap() })
        } else {
            RawFrame(
                frame.fieldNames + newName,
                frame.fieldTypes + "number",
                frame.columns + listOf<List<Any?>>(newCol),
                frame.fieldLabels + emptyMap(),
            )
        }
    }

    private fun reduceValues(values: List<Double>, reducer: String): Double? {
        if (values.isEmpty()) return null
        return when (reducer) {
            "sum" -> values.sum()
            "mean", "avg" -> values.average()
            "min" -> values.min()
            "max" -> values.max()
            "count" -> values.size.toDouble()
            "last" -> values.last()
            "first" -> values.first()
            else -> values.sum()
        }
    }

    private fun filterFieldsByName(frame: RawFrame, opts: JsonObject): RawFrame {
        val include = opts["include"] as? JsonObject
        val names = (include?.get("names") as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
            ?.toSet()
        val patternStr = include?.get("pattern")?.jsonPrimitive?.contentOrNull()
        val pattern = patternStr?.let { runCatching { Regex(it) }.getOrNull() }
        if (names.isNullOrEmpty() && pattern == null) return frame
        val keepIdx = frame.fieldNames.indices.filter { i ->
            val n = frame.fieldNames[i]
            (names?.contains(n) == true) || (pattern?.containsMatchIn(n) == true)
        }
        if (keepIdx.isEmpty()) return frame
        return RawFrame(
            fieldNames = keepIdx.map { frame.fieldNames[it] },
            fieldTypes = keepIdx.map { frame.fieldTypes[it] },
            columns = keepIdx.map { frame.columns[it] },
            fieldLabels = keepIdx.map { frame.fieldLabels.getOrElse(it) { emptyMap() } },
        )
    }

    private fun reduce(frame: RawFrame, opts: JsonObject): RawFrame {
        val reducers = (opts["reducers"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
            ?: listOf("last")
        val reducer = reducers.firstOrNull() ?: "last"
        val fieldCol = mutableListOf<Any?>()
        val valueCols = reducers.map { mutableListOf<Any?>() }
        for (i in frame.fieldNames.indices) {
            if (frame.fieldTypes[i] != "number") continue
            fieldCol += frame.fieldNames[i]
            val nums = frame.columns[i].mapNotNull { (it as? Number)?.toDouble() }
            for ((ri, r) in reducers.withIndex()) {
                valueCols[ri] += reduceValues(nums, r)
            }
        }
        val names = mutableListOf("Field").apply { addAll(reducers) }
        val types = mutableListOf("string").apply { addAll(reducers.map { "number" }) }
        val cols = mutableListOf<List<Any?>>(fieldCol).apply { addAll(valueCols) }
        return RawFrame(names, types, cols, List(names.size) { emptyMap() })
    }

    private fun JsonPrimitive.contentOrNull(): String? = runCatching { content }.getOrNull()
}
