package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.model.Variable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Replace Grafana variable references in [text] with their current values.
 *
 * Supported syntaxes:
 *   $varname             (must be followed by a non-word char or end of string)
 *   ${varname}
 *   ${varname:formatFn}  (formatter is honored for a small common set; unknown formatters
 *                         fall back to plain join-with-comma)
 *   [[varname]]          (legacy)
 *
 * Multi-value handling: default joiner is "," which matches Prometheus. When formatter
 * is "regex" or "pipe" we join with "|" and wrap for Prometheus label matching.
 */
object VariableInterpolator {

    private val bracesRe = Regex("""\$\{([a-zA-Z_][a-zA-Z0-9_]*)(?::([a-zA-Z0-9_]+))?\}""")
    private val dollarRe = Regex("""\$([a-zA-Z_][a-zA-Z0-9_]*)""")
    private val legacyRe = Regex("""\[\[([a-zA-Z_][a-zA-Z0-9_]*)(?::([a-zA-Z0-9_]+))?\]\]""")

    fun interpolate(text: String, vars: Map<String, Variable>): String {
        if (vars.isEmpty()) return text
        var out = text
        out = bracesRe.replace(out) { m -> resolve(vars[m.groupValues[1]], m.groupValues[2]) ?: m.value }
        out = legacyRe.replace(out) { m -> resolve(vars[m.groupValues[1]], m.groupValues[2]) ?: m.value }
        out = dollarRe.replace(out) { m -> resolve(vars[m.groupValues[1]], null) ?: m.value }
        return out
    }

    private fun resolve(v: Variable?, formatter: String?): String? {
        if (v == null) return null
        val values = when {
            v.current.isEmpty() -> return ""
            v.current.size == 1 && v.current.first() == "\$__all" -> {
                v.allValue?.let { return it }
                v.options.filter { it.value != "\$__all" }.map { it.value }
            }
            else -> v.current
        }
        return when (formatter) {
            "raw" -> values.joinToString(",")
            "pipe" -> values.joinToString("|")
            "regex" -> if (values.size == 1) escapeRegex(values.first()) else values.joinToString("|") { escapeRegex(it) }
            "csv", null, "" -> values.joinToString(",")
            "singlequote" -> values.joinToString(",") { "'$it'" }
            "doublequote" -> values.joinToString(",") { "\"$it\"" }
            "percentencode" -> values.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
            "json" -> values.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
            else -> values.joinToString(",")
        }
    }

    private fun escapeRegex(s: String): String =
        s.replace(Regex("""([.\\+*?\[\]{}()|^$])"""), "\\\\$1")

    /**
     * Walk a target JsonObject and interpolate variables in all string fields recursively.
     * Numeric/boolean values are left untouched.
     */
    fun interpolateElement(el: JsonElement, vars: Map<String, Variable>): JsonElement = when (el) {
        is JsonPrimitive -> if (el.isString) JsonPrimitive(interpolate(el.content, vars)) else el
        is JsonObject -> buildJsonObject { el.forEach { (k, v) -> put(k, interpolateElement(v, vars)) } }
        is JsonArray -> buildJsonArray { el.forEach { add(interpolateElement(it, vars)) } }
        else -> el
    }
}
