package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.RawFrame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loki normally applies `| json`, `| logfmt`, and `| line_format "..."` server-side and returns
 * the reshaped Line column. But in a couple of dashboard shapes Grafana asks Loki for the raw
 * stream and expects the client to render extracted fields itself. This helper detects those
 * cases and produces a reshaped line per row.
 *
 * Approach:
 *   1. Pick the first target's LogQL expression from the panel.
 *   2. If it contains `| line_format "TEMPLATE"`, use TEMPLATE. Otherwise fall through.
 *   3. If the frame has a "Line" column but the values look like raw JSON (or the query has
 *      `| json` and there are matching extracted-field columns), reformat with the template,
 *      substituting `{{.field}}` from either extracted string columns or by parsing the JSON.
 */
internal object LogQLPipeline {

    private val LINE_FORMAT = Regex("""\|\s*line_format\s+"([^"]*)"""")
    private val TEMPLATE_VAR = Regex("""\{\{\s*\.([A-Za-z_][A-Za-z0-9_]*)\s*}}""")
    private val HAS_JSON = Regex("""\|\s*json\b""")
    private val HAS_LOGFMT = Regex("""\|\s*logfmt\b""")

    /** Returns a possibly-reformatted view of [frame] using pipeline hints from [panel] targets. */
    fun reshape(panel: Panel, frame: RawFrame): RawFrame {
        val expr = firstLogQL(panel) ?: return frame
        val template = LINE_FORMAT.find(expr)?.groupValues?.get(1)
        val hasJson = HAS_JSON.containsMatchIn(expr)
        val hasLogfmt = HAS_LOGFMT.containsMatchIn(expr)
        if (template == null && !hasJson && !hasLogfmt) return frame

        val lineIdx = frame.fieldNames.indexOfFirst { it.equals("Line", true) || it.equals("body", true) }
        if (lineIdx < 0) return frame

        // If the Line column already looks reshaped (does not start with '{' or 'key='), server did the work.
        val sample = (frame.columns[lineIdx].firstOrNull() as? String).orEmpty()
        val looksRaw = sample.startsWith("{") || (hasLogfmt && sample.contains("="))
        if (template == null || !looksRaw) return frame

        val newLine = (0 until frame.rowCount).map { r ->
            val raw = frame.columns[lineIdx].getOrNull(r)?.toString().orEmpty()
            val parsed = when {
                hasJson -> parseJsonFields(raw)
                hasLogfmt -> parseLogfmt(raw)
                else -> emptyMap()
            }
            substitute(template, r, frame, parsed)
        }
        val newColumns = frame.columns.toMutableList()
        newColumns[lineIdx] = newLine
        return frame.copy(columns = newColumns)
    }

    private fun firstLogQL(panel: Panel): String? {
        for (t in panel.targets) {
            val expr = t["expr"]?.jsonPrimitive?.contentOrNull()
                ?: (t["query"] as? JsonPrimitive)?.contentOrNull()
                ?: (t["query"] as? JsonObject)?.get("expr")?.jsonPrimitive?.contentOrNull()
            if (!expr.isNullOrBlank()) return expr
        }
        return null
    }

    private fun substitute(template: String, row: Int, frame: RawFrame, extracted: Map<String, String>): String {
        return TEMPLATE_VAR.replace(template) { m ->
            val key = m.groupValues[1]
            // Prefer extracted, then a same-named string column, then fall back to empty.
            extracted[key]
                ?: run {
                    val i = frame.fieldNames.indexOfFirst { it.equals(key, true) }
                    if (i >= 0) frame.columns[i].getOrNull(row)?.toString().orEmpty() else ""
                }
        }
    }

    private fun parseJsonFields(raw: String): Map<String, String> {
        return runCatching {
            val root = Json.parseToJsonElement(raw).jsonObject
            root.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> runCatching { v.content }.getOrDefault("")
                    else -> v.toString()
                }
            }
        }.getOrDefault(emptyMap())
    }

    /** Bare-bones logfmt parser: key=value pairs, values may be quoted. */
    private fun parseLogfmt(raw: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        var i = 0
        while (i < raw.length) {
            while (i < raw.length && raw[i].isWhitespace()) i++
            val keyStart = i
            while (i < raw.length && raw[i] != '=' && !raw[i].isWhitespace()) i++
            if (i >= raw.length || raw[i] != '=') { i++; continue }
            val key = raw.substring(keyStart, i)
            i++ // skip '='
            val value = if (i < raw.length && raw[i] == '"') {
                val start = ++i
                while (i < raw.length && raw[i] != '"') { if (raw[i] == '\\') i++; i++ }
                val v = raw.substring(start, i.coerceAtMost(raw.length))
                if (i < raw.length) i++
                v
            } else {
                val start = i
                while (i < raw.length && !raw[i].isWhitespace()) i++
                raw.substring(start, i)
            }
            if (key.isNotEmpty()) out[key] = value
        }
        return out
    }

    private fun JsonPrimitive.contentOrNull(): String? = runCatching { content }.getOrNull()
}
