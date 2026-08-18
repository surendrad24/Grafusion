package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.db.DashboardDao
import com.fusionlancers.grafusion.data.db.DashboardEntity
import com.fusionlancers.grafusion.data.model.Dashboard
import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.PanelData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class DashboardRepository(
    private val dashboardDao: DashboardDao,
    private val accountRepository: AccountRepository,
    private val apiFactory: GrafanaApiFactory,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboards: Flow<List<Dashboard>> =
        accountRepository.activeAccount.flatMapLatest { account ->
            if (account == null) flowOf(emptyList())
            else dashboardDao.forAccount(account.id).map { rows ->
                rows.map {
                    Dashboard(
                        uid = it.uid,
                        title = it.title,
                        folderTitle = it.folderTitle,
                        tags = if (it.tags.isBlank()) emptyList() else it.tags.split(","),
                        cachedOffline = it.detailJson != null,
                        dashboardId = it.dashboardId,
                        isStarred = it.isStarred,
                    )
                }
            }
        }

    suspend fun refresh(): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: return@runCatching
        val auth = accountRepository.authHeaderFor(entity) ?: return@runCatching
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)

        val remote = api.searchDashboards(auth)
        val mapped = remote.map {
            DashboardEntity(
                accountId = entity.id,
                uid = it.uid,
                title = it.title,
                folderTitle = it.folderTitle,
                folderUid = it.folderUid,
                tags = it.tags.joinToString(","),
                dashboardId = it.id,
                isStarred = it.isStarred,
            )
        }
        dashboardDao.upsertAll(mapped)
    }

    /** Toggle the starred flag on Grafana; optimistically update the local cache. */
    suspend fun toggleStar(uid: String, dashboardId: Long, newValue: Boolean): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = if (newValue) api.starDashboard(auth, dashboardId) else api.unstarDashboard(auth, dashboardId)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        dashboardDao.updateStar(entity.id, uid, newValue)
    }

    /** Fetch and parse a dashboard's panel list. */
    suspend fun panelsFor(uid: String): List<Panel> {
        val entity = accountRepository.activeEntity() ?: return emptyList()
        val auth = accountRepository.authHeaderFor(entity) ?: return emptyList()
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val detail = api.dashboardByUid(auth, uid)
        val parsed = PanelParser.parsePanels(detail.dashboard)
        android.util.Log.i(
            "GrafusionDetail",
            "uid=$uid dashKeys=${detail.dashboard.keys.take(20)} " +
                "panelsField=${detail.dashboard["panels"]?.let { it::class.simpleName }} " +
                "rawSize=${(detail.dashboard["panels"] as? kotlinx.serialization.json.JsonArray)?.size} " +
                "parsed=${parsed.size}"
        )
        return parsed
    }

    /**
     * Run a single panel's first target through /api/ds/query.
     * from/to are Grafana time expressions (e.g. "now-6h" / "now") or millis-since-epoch strings.
     */
    suspend fun queryPanel(panel: Panel, from: String = "now-6h", to: String = "now"): Result<PanelData> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)

        val body = buildJsonObject {
            put("from", from)
            put("to", to)
            put("queries", buildJsonArray {
                panel.targets.forEachIndexed { idx, target ->
                    val existingRef = (target["refId"] as? JsonPrimitive)?.content ?: refIdFor(idx)
                    add(mergeTarget(target, panel, existingRef))
                }
            })
        }
        val resp = api.queryDatasource(auth, body)
        PanelParser.parseQueryResponse(resp)
    }

    /**
     * Save a new panel ordering by rewriting each panel's gridPos to match [orderedPanelIds],
     * flowing left-to-right in bands of up to 24 columns. Preserves each panel's original width and height.
     * Returns Result.success on 200 from Grafana, else failure with the server error.
     */
    suspend fun savePanelOrder(uid: String, orderedPanelIds: List<Long>): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val detail = api.dashboardByUid(auth, uid)
        val dashboard = detail.dashboard
        val originalPanels = dashboard["panels"]?.jsonArray ?: error("Dashboard has no panels array")

        // Build lookup: panelId -> original panel JSON, preserving gridW/H.
        val byId = mutableMapOf<Long, JsonObject>()
        originalPanels.forEach { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.let { runCatching { it.content.toLong() }.getOrNull() } ?: return@forEach
            byId[id] = obj
        }

        // Compute new gridPos flowing left-to-right.
        val newPositions = mutableMapOf<Long, Triple<Int, Int, Int>>() // id -> (x, y, w) with h from original
        var cursorX = 0
        var cursorY = 0
        var bandH = 0
        for (id in orderedPanelIds) {
            val original = byId[id] ?: continue
            val gp = original["gridPos"]?.jsonObject
            val w = (gp?.get("w")?.jsonPrimitive?.let { runCatching { it.content.toInt() }.getOrNull() } ?: 8).coerceIn(1, 24)
            val h = (gp?.get("h")?.jsonPrimitive?.let { runCatching { it.content.toInt() }.getOrNull() } ?: 8).coerceIn(1, 40)
            if (cursorX + w > 24) {
                cursorX = 0
                cursorY += bandH
                bandH = 0
            }
            newPositions[id] = Triple(cursorX, cursorY, w)
            cursorX += w
            if (h > bandH) bandH = h
        }

        // Rebuild panels array with rewritten gridPos where the panel is in the order set.
        val rewrittenPanels = buildJsonArray {
            originalPanels.forEach { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.let { runCatching { it.content.toLong() }.getOrNull() }
                val newPos = id?.let { newPositions[it] }
                if (newPos == null) {
                    add(obj)
                } else {
                    val (x, y, w) = newPos
                    val origH = obj["gridPos"]?.jsonObject?.get("h")?.jsonPrimitive?.let { runCatching { it.content.toInt() }.getOrNull() } ?: 8
                    add(buildJsonObject {
                        obj.forEach { (k, v) -> if (k != "gridPos") put(k, v) }
                        put("gridPos", buildJsonObject {
                            put("x", JsonPrimitive(x))
                            put("y", JsonPrimitive(y))
                            put("w", JsonPrimitive(w))
                            put("h", JsonPrimitive(origH))
                        })
                    })
                }
            }
        }

        val newDashboard = buildJsonObject {
            dashboard.forEach { (k, v) -> if (k != "panels") put(k, v) }
            put("panels", rewrittenPanels)
        }
        val body = buildJsonObject {
            put("dashboard", newDashboard)
            put("overwrite", JsonPrimitive(true))
            put("message", JsonPrimitive("Reordered from Grafusion mobile"))
        }
        val resp = api.saveDashboard(auth, body)
        if (resp.status != null && resp.status != "success") error(resp.status)
    }

    private fun refIdFor(idx: Int): String = ('A' + idx).toString()

    private fun mergeTarget(target: JsonObject, panel: Panel, refId: String): JsonElement = buildJsonObject {
        // Copy the target's own fields (expr, format, legendFormat, instant, range, hide, etc.).
        target.forEach { (k, v) -> put(k, v) }
        put("refId", refId)
        // Only default range=true when the target didn't specify range/instant.
        // Grafana's Prometheus DS uses instant=true for gauges/tables; overriding it breaks geomaps + label queries.
        if (target["range"] == null && target["instant"] == null) {
            put("range", JsonPrimitive(true))
        }
        put("intervalMs", JsonPrimitive(15000))
        put("maxDataPoints", JsonPrimitive(500))
        // Prefer target's own datasource; fall back to the panel-level datasource.
        if (target["datasource"] == null && panel.datasourceUid != null) {
            put("datasource", buildJsonObject {
                put("uid", panel.datasourceUid)
                panel.datasourceType?.let { put("type", it) }
            })
        }
    }
}

