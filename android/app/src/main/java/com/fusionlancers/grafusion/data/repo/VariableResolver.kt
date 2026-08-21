package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.model.Variable
import com.fusionlancers.grafusion.data.model.VariableOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves `type=query` template variables by hitting the datasource proxy for label-values.
 * Supports `label_values(SELECTOR, LABEL)` and `label_values(LABEL)` against Prometheus + Loki.
 *
 * We skip other query shapes (e.g. `metrics(...)`, `query_result(...)`) since they're unusual and
 * the pre-resolved snapshot in the dashboard JSON is a reasonable fallback for those. Chained
 * variables (var A references var B) are resolved by two-pass interpolation: earlier passes fill
 * standalone vars first, then dependents get their selectors interpolated.
 */
internal object VariableResolver {

    private val LABEL_VALUES_WITH_SEL = Regex("""^\s*label_values\s*\(\s*(.+?)\s*,\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)\s*$""")
    private val LABEL_VALUES_BARE = Regex("""^\s*label_values\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)\s*$""")

    suspend fun resolve(
        variables: List<Variable>,
        client: OkHttpClient,
        grafanaBase: String,
        authHeader: String,
    ): List<Variable> = withContext(Dispatchers.IO) {
        if (variables.none { it.type == "query" && !it.queryExpr.isNullOrBlank() }) return@withContext variables

        val resolved = variables.associateBy { it.name }.toMutableMap()
        // Two passes: the first resolves standalone vars, the second lets dependents interpolate.
        repeat(2) {
            for (v in variables) {
                if (v.type != "query") continue
                val expr = v.queryExpr ?: continue
                val ds = v.datasourceType ?: continue
                val uid = v.datasourceUid ?: continue
                val interpolated = VariableInterpolator.interpolate(expr, resolved)
                val values = runCatching {
                    fetchLabelValues(client, grafanaBase, authHeader, ds, uid, interpolated)
                }.getOrNull() ?: continue
                if (values.isEmpty()) continue
                val filtered = applyRegex(values, v.regex).let { sortValues(it, v.sort) }
                val opts = filtered.map { VariableOption(it, it) }
                // Preserve user's current selection if still valid; else default to $__all or first.
                val stillValid = v.current.filter { it == "\$__all" || it in filtered }
                val newCurrent = when {
                    stillValid.isNotEmpty() -> stillValid
                    v.includeAll -> listOf("\$__all")
                    filtered.isNotEmpty() -> listOf(filtered.first())
                    else -> emptyList()
                }
                resolved[v.name] = v.copy(options = opts, current = newCurrent)
            }
        }
        variables.map { resolved[it.name] ?: it }
    }

    private fun fetchLabelValues(
        client: OkHttpClient,
        grafanaBase: String,
        authHeader: String,
        dsType: String,
        dsUid: String,
        expr: String,
    ): List<String> {
        val withSel = LABEL_VALUES_WITH_SEL.matchEntire(expr)
        val bare = LABEL_VALUES_BARE.matchEntire(expr)
        val (selector, label) = when {
            withSel != null -> withSel.groupValues[1] to withSel.groupValues[2]
            bare != null -> null to bare.groupValues[1]
            else -> return emptyList()
        }

        val basePath = when (dsType) {
            "prometheus" -> "api/datasources/proxy/uid/$dsUid/api/v1/label/$label/values"
            "loki" -> "api/datasources/proxy/uid/$dsUid/loki/api/v1/label/$label/values"
            else -> return emptyList()
        }
        val urlBuilder = (grafanaBase.trimEnd('/') + "/" + basePath).toHttpUrl().newBuilder()
        if (selector != null) {
            // Prometheus uses `match[]=`, Loki uses `query=` (a stream selector).
            when (dsType) {
                "prometheus" -> urlBuilder.addQueryParameter("match[]", selector)
                "loki" -> urlBuilder.addQueryParameter("query", selector)
            }
        }
        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            val root = Json.parseToJsonElement(body).jsonObject
            val data = root["data"] as? JsonArray ?: return emptyList()
            return data.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
        }
    }

    private fun applyRegex(values: List<String>, regex: String?): List<String> {
        if (regex.isNullOrBlank()) return values
        // Grafana regex may be wrapped in slashes: "/pattern/flags". Strip them if present.
        val (pattern, ignoreCase) = parseRegex(regex)
        val re = runCatching { Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }.getOrNull()
            ?: return values
        // If the pattern has a capture group, keep the first group; else keep matching lines verbatim.
        val hasGroup = pattern.contains(Regex("""(?<!\\)\(""")) && !pattern.contains("(?:")
        return values.mapNotNull { v ->
            val m = re.find(v) ?: return@mapNotNull null
            if (hasGroup && m.groupValues.size > 1) m.groupValues[1] else v
        }
    }

    private fun parseRegex(raw: String): Pair<String, Boolean> {
        val m = Regex("""^/(.+)/([a-z]*)$""").matchEntire(raw.trim())
        return if (m != null) m.groupValues[1] to m.groupValues[2].contains('i')
        else raw to false
    }

    private fun sortValues(values: List<String>, sort: Int): List<String> = when (sort) {
        1 -> values.sorted()
        2 -> values.sortedDescending()
        3 -> values.sortedBy { it.toDoubleOrNull() ?: Double.MAX_VALUE }
        4 -> values.sortedByDescending { it.toDoubleOrNull() ?: -Double.MAX_VALUE }
        5 -> values.sortedBy { it.lowercase() }
        6 -> values.sortedByDescending { it.lowercase() }
        else -> values
    }

    private fun JsonPrimitive.contentOrNull(): String? = runCatching { content }.getOrNull()
}
