package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.db.DashboardDao
import com.fusionlancers.grafusion.data.db.DashboardEntity
import com.fusionlancers.grafusion.data.model.Dashboard
import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.PanelData
import com.fusionlancers.grafusion.data.model.PanelGroup
import com.fusionlancers.grafusion.data.model.Variable
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
        // Preserve any cached detailJson so the search refresh (which doesn't return the full dashboard)
        // doesn't wipe our offline copies.
        val cachedJson = dashboardDao.cachedDetailPairs(entity.id).associate { it.uid to it.detailJson }
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
                detailJson = cachedJson[it.uid],
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

    /**
     * Fetch and parse a dashboard's panel list. Caches the raw dashboard JSON so we can serve
     * it offline; on network failure, tries to hydrate from the cache before rethrowing.
     */
    suspend fun panelsFor(uid: String): PanelsResult {
        val entity = accountRepository.activeEntity() ?: return PanelsResult(emptyList(), emptyList(), emptyList(), false)
        val auth = accountRepository.authHeaderFor(entity) ?: return PanelsResult(emptyList(), emptyList(), emptyList(), false)
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)

        return try {
            val detail = api.dashboardByUid(auth, uid)
            val parsed = PanelParser.parsePanels(detail.dashboard)
            val groups = PanelParser.parseGroups(detail.dashboard)
            val parsedVars = VariableParser.parse(detail.dashboard)
            // Resolve type=query variables against Prometheus/Loki label-values before returning.
            // Failure is non-fatal - we fall back to the snapshot in the dashboard JSON.
            val variables = runCatching {
                VariableResolver.resolve(parsedVars, apiFactory.client, entity.grafanaUrl, auth)
            }.getOrDefault(parsedVars)
            // Persist the raw dashboard JSON so we can render offline next time.
            runCatching {
                val json = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    detail.dashboard,
                )
                dashboardDao.updateDetail(entity.id, uid, json)
            }
            PanelsResult(parsed, groups, variables, fromCache = false)
        } catch (t: Throwable) {
            val cached = dashboardDao.detailJsonFor(entity.id, uid)
                ?: throw t
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(cached).jsonObject
            PanelsResult(PanelParser.parsePanels(obj), PanelParser.parseGroups(obj), VariableParser.parse(obj), fromCache = true)
        }
    }

    data class PanelsResult(
        val panels: List<Panel>,
        val groups: List<PanelGroup>,
        val variables: List<Variable> = emptyList(),
        val fromCache: Boolean,
    )

    /**
     * Run a single panel's first target through /api/ds/query.
     * from/to are Grafana time expressions (e.g. "now-6h" / "now") or millis-since-epoch strings.
     */
    suspend fun queryPanel(
        panel: Panel,
        from: String = "now-6h",
        to: String = "now",
        variables: List<Variable> = emptyList(),
    ): Result<PanelData> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)

        val pinnedVars = if (panel.repeat != null && panel.repeatValue != null) {
            variables.map { v -> if (v.name == panel.repeat) v.copy(current = listOf(panel.repeatValue)) else v }
        } else variables
        val varMap = pinnedVars.associateBy { it.name }
        val (effectiveFrom, effectiveTo) = applyPanelTimeOverrides(from, to, panel.timeFrom, panel.timeShift)
        val body = buildJsonObject {
            put("from", effectiveFrom)
            put("to", effectiveTo)
            put("queries", buildJsonArray {
                panel.targets.forEachIndexed { idx, target ->
                    val existingRef = (target["refId"] as? JsonPrimitive)?.content ?: refIdFor(idx)
                    val interpolated = VariableInterpolator.interpolateElement(target, varMap) as JsonObject
                    add(mergeTarget(interpolated, panel, existingRef))
                }
            })
        }
        val resp = api.queryDatasource(auth, body)
        PanelParser.parseQueryResponse(resp)
    }

    /**
     * A single entry in the saved layout. finalId is the resulting panel's id.
     *
     * - sourceId non-null + newType null: existing panel (possibly renamed/resized).
     *   For duplicates, finalId is a fresh id while sourceId points at the original.
     * - sourceId null + newType non-null: newly added blank panel.
     *
     * Original top-level panels absent from the ops list are treated as deleted.
     * Row containers (type=row) and any panels nested inside them are preserved as-is.
     */
    data class LayoutOp(
        val finalId: Long,
        val sourceId: Long?,
        val newType: String?,
        val newTitle: String?,
        val w: Int,
        val h: Int,
    )

    /** Full-layout save covering reorder, resize, rename, duplicate, delete, and add. */
    suspend fun saveDashboardLayout(uid: String, ops: List<LayoutOp>, message: String = "Layout edited from Grafusion mobile"): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val detail = api.dashboardByUid(auth, uid)
        val dashboard = detail.dashboard
        val originalPanels = dashboard["panels"]?.jsonArray ?: error("Dashboard has no panels array")

        // Split original panels into row containers (kept verbatim) and top-level panels indexed by id.
        val rowContainers = mutableListOf<JsonObject>()
        val topLevelById = mutableMapOf<Long, JsonObject>()
        originalPanels.forEach { el ->
            val obj = el.jsonObject
            if (obj["type"]?.jsonPrimitive?.let { runCatching { it.content }.getOrNull() } == "row") {
                rowContainers += obj
                return@forEach
            }
            val id = obj["id"]?.jsonPrimitive?.let { runCatching { it.content.toLong() }.getOrNull() }
            if (id != null) topLevelById[id] = obj
        }

        // Flow ops left-to-right in 24-col bands, computing gridPos for each op.
        var cursorX = 0
        var cursorY = 0
        var bandH = 0
        data class Placed(val op: LayoutOp, val x: Int, val y: Int)
        val placements = mutableListOf<Placed>()
        for (op in ops) {
            val w = op.w.coerceIn(1, 24)
            val h = op.h.coerceIn(1, 40)
            if (cursorX + w > 24) { cursorX = 0; cursorY += bandH; bandH = 0 }
            placements += Placed(op.copy(w = w, h = h), cursorX, cursorY)
            cursorX += w
            if (h > bandH) bandH = h
        }

        val rewrittenPanels = buildJsonArray {
            // Preserve row containers first - savePanelOrder never touched their nested panels either.
            rowContainers.forEach { add(it) }
            placements.forEach { placed ->
                val op = placed.op
                val base: JsonObject = op.sourceId?.let { topLevelById[it] } ?: emptyBlankPanel(op)
                add(buildJsonObject {
                    base.forEach { (k, v) ->
                        if (k == "gridPos" || k == "id") return@forEach
                        if (k == "title" && op.newTitle != null) return@forEach
                        put(k, v)
                    }
                    put("id", JsonPrimitive(op.finalId))
                    op.newTitle?.let { put("title", JsonPrimitive(it)) }
                    put("gridPos", buildJsonObject {
                        put("x", JsonPrimitive(placed.x))
                        put("y", JsonPrimitive(placed.y))
                        put("w", JsonPrimitive(op.w))
                        put("h", JsonPrimitive(op.h))
                    })
                })
            }
        }

        val newDashboard = buildJsonObject {
            dashboard.forEach { (k, v) -> if (k != "panels") put(k, v) }
            put("panels", rewrittenPanels)
        }
        val body = buildJsonObject {
            put("dashboard", newDashboard)
            put("overwrite", JsonPrimitive(true))
            put("message", JsonPrimitive(message))
        }
        val resp = api.saveDashboard(auth, body)
        if (resp.status != null && resp.status != "success") error(resp.status)
    }

    private fun emptyBlankPanel(op: LayoutOp): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(op.newType ?: "text"))
        put("title", JsonPrimitive(op.newTitle ?: "New panel"))
        put("targets", buildJsonArray { })
        put("options", buildJsonObject {
            if (op.newType == "text") {
                put("mode", JsonPrimitive("markdown"))
                put("content", JsonPrimitive("_New text panel - edit in Grafana._"))
            }
        })
        put("fieldConfig", buildJsonObject {
            put("defaults", buildJsonObject { })
            put("overrides", buildJsonArray { })
        })
    }

    /**
     * Save a new panel ordering by rewriting each panel's gridPos to match [orderedPanelIds],
     * flowing left-to-right in bands of up to 24 columns. Uses [sizeOverrides] (id -> w,h) when provided,
     * falling back to each panel's original width and height.
     */
    suspend fun savePanelOrder(
        uid: String,
        orderedPanelIds: List<Long>,
        sizeOverrides: Map<Long, Pair<Int, Int>> = emptyMap(),
    ): Result<Unit> = runCatching {
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
        val newHeights = mutableMapOf<Long, Int>()
        for (id in orderedPanelIds) {
            val original = byId[id] ?: continue
            val gp = original["gridPos"]?.jsonObject
            val origW = (gp?.get("w")?.jsonPrimitive?.let { runCatching { it.content.toInt() }.getOrNull() } ?: 8).coerceIn(1, 24)
            val origH = (gp?.get("h")?.jsonPrimitive?.let { runCatching { it.content.toInt() }.getOrNull() } ?: 8).coerceIn(1, 40)
            val (w, h) = sizeOverrides[id]?.let { (ow, oh) -> ow.coerceIn(1, 24) to oh.coerceIn(1, 40) } ?: (origW to origH)
            if (cursorX + w > 24) {
                cursorX = 0
                cursorY += bandH
                bandH = 0
            }
            newPositions[id] = Triple(cursorX, cursorY, w)
            newHeights[id] = h
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
                    val h = newHeights[id] ?: obj["gridPos"]?.jsonObject?.get("h")?.jsonPrimitive
                        ?.let { runCatching { it.content.toInt() }.getOrNull() } ?: 8
                    add(buildJsonObject {
                        obj.forEach { (k, v) -> if (k != "gridPos") put(k, v) }
                        put("gridPos", buildJsonObject {
                            put("x", JsonPrimitive(x))
                            put("y", JsonPrimitive(y))
                            put("w", JsonPrimitive(w))
                            put("h", JsonPrimitive(h))
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

    /**
     * Replace a panel's query expressions in-place and save the dashboard back to Grafana.
     *
     * [newExprs] is refId -> new expr string. Only targets whose refId matches are touched;
     * we rewrite the field Grafana expects for that datasource (`expr` for prometheus/loki,
     * `query` otherwise). Callers can also change other target fields if the model grows;
     * for now the sheet only exposes the primary expression, which is 90% of what phone users
     * want to tweak (threshold %, label filter, time window).
     */
    suspend fun updatePanelQueries(
        uid: String,
        panelId: Long,
        newExprs: Map<String, String>,
    ): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val detail = api.dashboardByUid(auth, uid)
        val dashboard = detail.dashboard
        val originalPanels = dashboard["panels"]?.jsonArray ?: error("Dashboard has no panels array")

        var touched = false
        val rewritten = buildJsonArray {
            originalPanels.forEach { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.let { runCatching { it.content.toLong() }.getOrNull() }
                if (id != panelId) { add(obj); return@forEach }
                touched = true
                val existingTargets = obj["targets"]?.jsonArray ?: JsonArray(emptyList())
                val panelDsType = obj["datasource"]?.jsonObject?.get("type")?.jsonPrimitive
                    ?.let { runCatching { it.content }.getOrNull() }.orEmpty().lowercase()
                val newTargets = buildJsonArray {
                    existingTargets.forEachIndexed { idx, t ->
                        val tObj = t.jsonObject
                        val refId = tObj["refId"]?.jsonPrimitive?.let { runCatching { it.content }.getOrNull() }
                            ?: refIdFor(idx)
                        val replacement = newExprs[refId]
                        if (replacement == null) { add(tObj); return@forEachIndexed }
                        val targetDsType = tObj["datasource"]?.jsonObject?.get("type")?.jsonPrimitive
                            ?.let { runCatching { it.content }.getOrNull() }?.lowercase() ?: panelDsType
                        add(buildJsonObject {
                            tObj.forEach { (k, v) ->
                                if (k == "expr" || k == "query") return@forEach
                                put(k, v)
                            }
                            when (targetDsType) {
                                "prometheus", "loki" -> put("expr", JsonPrimitive(replacement))
                                else -> put("query", JsonPrimitive(replacement))
                            }
                        })
                    }
                }
                add(buildJsonObject {
                    obj.forEach { (k, v) -> if (k != "targets") put(k, v) }
                    put("targets", newTargets)
                })
            }
        }
        if (!touched) error("Panel $panelId not found in dashboard $uid")

        val newDashboard = buildJsonObject {
            dashboard.forEach { (k, v) -> if (k != "panels") put(k, v) }
            put("panels", rewritten)
        }
        val body = buildJsonObject {
            put("dashboard", newDashboard)
            put("overwrite", JsonPrimitive(true))
            put("message", JsonPrimitive("Query edited from Grafusion mobile"))
        }
        val resp = api.saveDashboard(auth, body)
        if (resp.status != null && resp.status != "success") error(resp.status)
    }

    /**
     * Apply per-panel time overrides. Grafana rules:
     *  - timeFrom "1h" -> from = now-1h, to unchanged.
     *  - timeShift "1d" -> both from and to get "-1d" arithmetically inserted after "now".
     * Both fields accept Grafana duration expressions ("1h", "30m", "1d", "1M", "1y").
     */
    private fun applyPanelTimeOverrides(
        from: String,
        to: String,
        timeFrom: String?,
        timeShift: String?,
    ): Pair<String, String> {
        var f = from
        var t = to
        if (!timeFrom.isNullOrBlank()) f = "now-$timeFrom"
        if (!timeShift.isNullOrBlank()) {
            f = shiftExpr(f, timeShift)
            t = shiftExpr(t, timeShift)
        }
        return f to t
    }

    private fun shiftExpr(expr: String, shift: String): String = when {
        expr == "now" -> "now-$shift"
        expr.startsWith("now") -> "now-$shift" + expr.removePrefix("now")
        else -> expr // numeric timestamps or non-`now` expressions: leave alone
    }

    /**
     * Publish a snapshot of the current dashboard to the Grafana snapshot service.
     * When [external] is true the snapshot goes to snapshots.raintank.io (public); when
     * false it stays on the user's own Grafana. [expiresSeconds] = 0 means "never".
     *
     * Returns the shareable URL that the mobile OS share sheet can hand off.
     */
    suspend fun createSnapshot(
        uid: String,
        name: String? = null,
        expiresSeconds: Long = 0,
        external: Boolean = false,
    ): Result<String> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val detail = api.dashboardByUid(auth, uid)
        val dashboard = detail.dashboard

        val body = buildJsonObject {
            put("dashboard", dashboard)
            if (!name.isNullOrBlank()) put("name", JsonPrimitive(name))
            put("expires", JsonPrimitive(expiresSeconds))
            put("external", JsonPrimitive(external))
        }
        val resp = api.createSnapshot(auth, body)
        if (!resp.isSuccessful) {
            val hint = when (resp.code()) {
                401, 403 -> "your Grafana role can't create snapshots"
                else -> "HTTP ${resp.code()}"
            }
            error(hint)
        }
        val snap = resp.body() ?: error("empty response")
        // Local snapshots come back as `/dashboard/snapshot/<key>` relative to the Grafana
        // URL; external snapshots return an absolute snapshots.raintank.io URL.
        if (snap.url.startsWith("http")) snap.url
        else entity.grafanaUrl.trimEnd('/') + "/dashboard/snapshot/" + snap.key
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

    // ---- Dashboard version history ----

    /** Newest-first list of version metadata rows. Returns an empty list on 404 so dashboards
     *  loaded from a source that doesn't expose history (e.g. provisioned files without the
     *  storage backend) don't error out. */
    suspend fun listVersions(uid: String): Result<List<com.fusionlancers.grafusion.data.api.DashboardVersionSummary>> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.dashboardVersions(auth, uid)
        when {
            resp.isSuccessful -> (resp.body() ?: emptyList()).sortedByDescending { it.version }
            resp.code() == 404 -> emptyList()
            else -> error("HTTP ${resp.code()}")
        }
    }

    /** Fetch a single version's full model - used for the compare/preview sheet. */
    suspend fun getVersion(uid: String, version: Int): Result<com.fusionlancers.grafusion.data.api.DashboardVersionDetail> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.dashboardVersion(auth, uid, version)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("Empty version response")
    }

    /** Roll the dashboard back to a prior version. Grafana creates a new version whose
     *  restoredFrom points at the chosen one, so subsequent list calls will surface the
     *  restore event as its own row. */
    suspend fun restoreVersion(uid: String, version: Int): Result<Unit> = runCatching {
        val entity = accountRepository.activeEntity() ?: error("No active account")
        val auth = accountRepository.authHeaderFor(entity) ?: error("No credentials")
        val api = apiFactory.forBaseUrl(entity.grafanaUrl)
        val resp = api.restoreDashboardVersion(
            auth,
            uid,
            com.fusionlancers.grafusion.data.api.RestoreDashboardBody(version = version),
        )
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        // The next successful panelsFor() call overwrites the cached JSON, so we don't need to
        // touch the cache here; the detail screen re-runs panelsFor on resume.
    }
}

