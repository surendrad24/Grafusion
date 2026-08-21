package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.Datasource
import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.model.PanelData
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Ad-hoc /api/ds/query runner for the Explore screen.
 *
 * Only datasources whose query shape we can encode are useful here - Grafana's plugin API
 * doesn't expose a generic "query editor" for mobile. We map by DS type:
 *   - prometheus / loki: {"expr": <query>} + range=true (Loki accepts the same shape)
 *   - anything else falls back to {"query": <query>, "rawQuery": true} which most SQL-ish
 *     datasources (postgres/mysql/mssql/influxdb-flux) accept for a raw statement.
 *
 * A 400 back from Grafana usually means the datasource wants a different shape; the error
 * bubbles up verbatim so the user knows to switch datasource or edit the query.
 */
class ExploreRepository(
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    suspend fun listDatasources(): Result<List<Datasource>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        api.listDatasources(auth).sortedWith(compareByDescending<Datasource> { it.isDefault }.thenBy { it.name })
    }

    suspend fun runQuery(
        datasource: Datasource,
        query: String,
        from: String = "now-1h",
        to: String = "now",
    ): Result<PanelData> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)

        val target = buildJsonObject {
            put("refId", "A")
            put("datasource", buildJsonObject {
                put("uid", JsonPrimitive(datasource.uid))
                put("type", JsonPrimitive(datasource.type))
            })
            when (datasource.type.lowercase()) {
                "prometheus", "loki" -> {
                    put("expr", JsonPrimitive(query))
                    put("range", JsonPrimitive(true))
                }
                "tempo" -> {
                    // Tempo accepts either a trace ID lookup or a TraceQL search. We pick
                    // "traceql" when the input looks like a TraceQL expression (starts with `{`
                    // or contains `=`), else treat it as a trace ID.
                    val looksLikeTraceQL = query.trimStart().startsWith("{") || "=" in query
                    put("query", JsonPrimitive(query))
                    put("queryType", JsonPrimitive(if (looksLikeTraceQL) "traceql" else "traceId"))
                    if (looksLikeTraceQL) put("limit", JsonPrimitive(20))
                }
                else -> {
                    put("query", JsonPrimitive(query))
                    put("rawQuery", JsonPrimitive(true))
                }
            }
            put("intervalMs", JsonPrimitive(15000))
            put("maxDataPoints", JsonPrimitive(500))
        }
        val body = buildJsonObject {
            put("from", from)
            put("to", to)
            put("queries", buildJsonArray { add(target) })
        }
        val resp = api.queryDatasource(auth, body)
        PanelParser.parseQueryResponse(resp)
    }
}
