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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
            )
        }
        dashboardDao.upsertAll(mapped)
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

    private fun refIdFor(idx: Int): String = ('A' + idx).toString()

    private fun mergeTarget(target: JsonObject, panel: Panel, refId: String): JsonElement = buildJsonObject {
        // Copy the target's own fields (expr, format, legendFormat, hide, etc.).
        target.forEach { (k, v) -> put(k, v) }
        put("refId", refId)
        put("range", JsonPrimitive(true))
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

