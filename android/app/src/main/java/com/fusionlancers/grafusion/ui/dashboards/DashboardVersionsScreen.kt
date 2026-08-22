package com.fusionlancers.grafusion.ui.dashboards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.DashboardVersionDetail
import com.fusionlancers.grafusion.data.api.DashboardVersionSummary
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Read + rollback view over Grafana's dashboard version history. Each save is a row; a "restore"
 * itself creates a new row whose `restoredFrom` points at the source, which we surface as a
 * badge so the user can see the audit trail. Tapping a row opens a preview sheet with title,
 * refresh interval, panel count, and the commit message the editor typed. From there they can
 * roll back - Grafana handles the semantics (the restore creates a new version rather than
 * mutating history).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardVersionsScreen(
    container: AppContainer,
    uid: String,
    title: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<DashboardVersionSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<DashboardVersionDetail?>(null) }
    var selectedLoading by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var restoreConfirm by remember { mutableStateOf<Int?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var compareMode by remember { mutableStateOf(false) }
    val compareSelection = remember { mutableStateListOf<Int>() }
    var diffOpen by remember { mutableStateOf(false) }
    var diffLoading by remember { mutableStateOf(false) }
    var diffPair by remember { mutableStateOf<Pair<DashboardVersionDetail, DashboardVersionDetail>?>(null) }
    val diffSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    suspend fun reload() {
        loading = true
        error = null
        container.dashboardRepository.listVersions(uid)
            .onSuccess { versions = it }
            .onFailure { error = it.message ?: "Failed to load versions" }
        loading = false
    }

    LaunchedEffect(uid) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Version history", fontWeight = FontWeight.SemiBold)
                        if (title.isNotBlank()) {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        compareMode = !compareMode
                        compareSelection.clear()
                    }) {
                        Icon(
                            Icons.Filled.CompareArrows,
                            contentDescription = if (compareMode) "Exit compare" else "Compare versions",
                            tint = if (compareMode) EnergyOrange else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when {
                loading -> LoadingBlock()
                error != null -> ErrorBlock(error!!)
                versions.isEmpty() -> EmptyBlock()
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        if (compareMode) {
                            CompareBanner(
                                selection = compareSelection.toList(),
                                enabled = compareSelection.size == 2 && !diffLoading,
                                loading = diffLoading,
                                onCompare = {
                                    val pair = compareSelection.toList()
                                    if (pair.size == 2) {
                                        diffLoading = true
                                        diffPair = null
                                        diffOpen = true
                                        scope.launch {
                                            val a = container.dashboardRepository.getVersion(uid, pair[0])
                                            val b = container.dashboardRepository.getVersion(uid, pair[1])
                                            val da = a.getOrNull()
                                            val db = b.getOrNull()
                                            if (da != null && db != null) {
                                                // Order older -> newer so "from -> to" reads naturally.
                                                diffPair = if (da.version <= db.version) da to db else db to da
                                            } else {
                                                toast = (a.exceptionOrNull() ?: b.exceptionOrNull())?.message
                                                    ?: "Failed to load versions"
                                                diffOpen = false
                                            }
                                            diffLoading = false
                                        }
                                    }
                                },
                                onClear = { compareSelection.clear() },
                            )
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(versions, key = { it.id }) { v ->
                                VersionRow(
                                    summary = v,
                                    compareMode = compareMode,
                                    selected = compareSelection.contains(v.version),
                                    onClick = {
                                        if (compareMode) {
                                            if (compareSelection.contains(v.version)) {
                                                compareSelection.remove(v.version)
                                            } else {
                                                if (compareSelection.size >= 2) {
                                                    compareSelection.removeAt(0)
                                                }
                                                compareSelection.add(v.version)
                                            }
                                        } else {
                                            sheetOpen = true
                                            selected = null
                                            selectedLoading = true
                                            scope.launch {
                                                container.dashboardRepository.getVersion(uid, v.version)
                                                    .onSuccess { selected = it }
                                                    .onFailure { toast = it.message }
                                                selectedLoading = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (sheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { sheetOpen = false },
                sheetState = sheetState,
            ) {
                VersionSheet(
                    loading = selectedLoading,
                    detail = selected,
                    restoring = restoring,
                    onRestore = { v -> restoreConfirm = v },
                )
            }
        }

        if (diffOpen) {
            ModalBottomSheet(
                onDismissRequest = { diffOpen = false },
                sheetState = diffSheetState,
            ) {
                VersionDiffSheet(loading = diffLoading, pair = diffPair)
            }
        }

        restoreConfirm?.let { version ->
            AlertDialog(
                onDismissRequest = { if (!restoring) restoreConfirm = null },
                title = { Text("Restore version $version?") },
                text = {
                    Text(
                        "This creates a new version on top of the current one; the current " +
                            "layout stays in history and can be restored back.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !restoring,
                        onClick = {
                            restoring = true
                            scope.launch {
                                container.dashboardRepository.restoreVersion(uid, version)
                                    .onSuccess {
                                        toast = "Restored to v$version"
                                        restoreConfirm = null
                                        sheetOpen = false
                                        reload()
                                    }
                                    .onFailure { toast = "Restore failed: ${it.message}" }
                                restoring = false
                            }
                        },
                    ) {
                        if (restoring) CircularProgressIndicator(
                            color = EnergyOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        ) else Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !restoring,
                        onClick = { restoreConfirm = null },
                    ) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun VersionRow(
    summary: DashboardVersionSummary,
    compareMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val isRestore = (summary.restoredFrom ?: 0) > 0
    val bg = if (compareMode && selected) {
        EnergyOrange.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compareMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = EnergyOrange),
            )
            Spacer(Modifier.size(4.dp))
        }
        Column(Modifier.size(width = 46.dp, height = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "v${summary.version}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = EnergyOrange,
            )
            if (isRestore) {
                Text(
                    "restore",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                summary.message?.takeIf { it.isNotBlank() } ?: if (isRestore) "Restored from v${summary.restoredFrom}" else "(no message)",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Row {
                summary.createdBy?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        "  •  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                Text(
                    formatCreated(summary.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun VersionSheet(
    loading: Boolean,
    detail: DashboardVersionDetail?,
    restoring: Boolean,
    onRestore: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (loading || detail == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = EnergyOrange,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Loading version…", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        val data = detail.data
        val versionTitle = (data?.get("title") as? JsonPrimitive)?.contentOrNull() ?: "Untitled"
        val refresh = (data?.get("refresh") as? JsonPrimitive)?.contentOrNull()
        val panelCount = (data?.get("panels") as? JsonArray)?.size ?: 0
        val templatingCount = runCatching {
            data?.get("templating")?.jsonObject?.get("list")?.jsonArray?.size ?: 0
        }.getOrDefault(0)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "v${detail.version}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = EnergyOrange,
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(versionTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${detail.createdBy ?: "unknown"}  •  ${formatCreated(detail.created)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        detail.message?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(8.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        InfoRow("Panels", panelCount.toString())
        InfoRow("Variables", templatingCount.toString())
        InfoRow("Refresh", refresh?.takeIf { it.isNotBlank() } ?: "off")
        (detail.parentVersion?.takeIf { it > 0 })?.let { InfoRow("Parent", "v$it") }
        (detail.restoredFrom?.takeIf { it > 0 })?.let { InfoRow("Restored from", "v$it") }
        Spacer(Modifier.height(16.dp))
        TextButton(
            enabled = !restoring,
            onClick = { onRestore(detail.version) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Restore, contentDescription = null, tint = EnergyOrange)
            Spacer(Modifier.size(6.dp))
            Text("Restore this version", color = EnergyOrange, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.size(width = 110.dp, height = 18.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LoadingBlock() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = EnergyOrange)
        Spacer(Modifier.height(8.dp))
        Text("Loading versions…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorBlock(msg: String) {
    Text(
        msg,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 24.dp),
    )
}

@Composable
private fun EmptyBlock() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No history for this dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CompareBanner(
    selection: List<Int>,
    enabled: Boolean,
    loading: Boolean,
    onCompare: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (selection.size) {
                        0 -> "Pick two versions"
                        1 -> "v${selection[0]} selected - pick one more"
                        else -> "v${selection.min()} vs v${selection.max()}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Diff shows what changed between the two saves.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (selection.isNotEmpty()) {
                TextButton(onClick = onClear, enabled = !loading) { Text("Clear") }
            }
            Button(
                onClick = onCompare,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text("Diff")
                }
            }
        }
    }
}

@Composable
private fun VersionDiffSheet(
    loading: Boolean,
    pair: Pair<DashboardVersionDetail, DashboardVersionDetail>?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (loading || pair == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = EnergyOrange, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text("Loading versions…", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        val (from, to) = pair
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "v${from.version} → v${to.version}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = EnergyOrange,
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    "${from.createdBy ?: "?"} → ${to.createdBy ?: "?"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    "${formatCreated(from.created)}  →  ${formatCreated(to.created)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        val changes = remember(from, to) { computeVersionDiff(from.data, to.data) }
        if (changes.isEmpty()) {
            Text(
                "No structural changes detected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            ) {
                items(changes, key = { it.hashCode() }) { change -> DiffRow(change) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DiffRow(change: DiffChange) {
    val (color, glyph) = when (change.kind) {
        DiffKind.Added -> EnergyOrange to "+"
        DiffKind.Removed -> MaterialTheme.colorScheme.error to "-"
        DiffKind.Changed -> MaterialTheme.colorScheme.primary to "~"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.size(width = 20.dp, height = 20.dp),
        )
        Spacer(Modifier.size(6.dp))
        Column(Modifier.weight(1f)) {
            Text(change.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            change.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private enum class DiffKind { Added, Removed, Changed }
private data class DiffChange(val kind: DiffKind, val label: String, val detail: String? = null)

/**
 * Structural diff between two dashboard version bodies. We compare title, tags, refresh, timezone,
 * schema version, and the panel + variable lists (keyed by id / name so a re-ordered save doesn't
 * show as churn). Panel titles use the panel id as the stable key; variables use name.
 */
private fun computeVersionDiff(from: JsonObject?, to: JsonObject?): List<DiffChange> {
    if (from == null || to == null) return emptyList()
    val out = mutableListOf<DiffChange>()

    fun scalar(key: String) {
        val a = (from[key] as? JsonPrimitive)?.contentOrNull()
        val b = (to[key] as? JsonPrimitive)?.contentOrNull()
        if (a != b) out += DiffChange(DiffKind.Changed, key.replaceFirstChar { it.uppercase() }, "$a → $b")
    }
    scalar("title")
    scalar("description")
    scalar("refresh")
    scalar("timezone")
    scalar("schemaVersion")
    scalar("editable")

    val tagsFrom = (from["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }?.toSet().orEmpty()
    val tagsTo = (to["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }?.toSet().orEmpty()
    (tagsTo - tagsFrom).forEach { out += DiffChange(DiffKind.Added, "Tag", it) }
    (tagsFrom - tagsTo).forEach { out += DiffChange(DiffKind.Removed, "Tag", it) }

    val panelsFrom = panelIndex(from)
    val panelsTo = panelIndex(to)
    val addedPanels = panelsTo.keys - panelsFrom.keys
    val removedPanels = panelsFrom.keys - panelsTo.keys
    val commonPanels = panelsFrom.keys intersect panelsTo.keys
    addedPanels.sorted().forEach { id ->
        val p = panelsTo[id]!!
        out += DiffChange(DiffKind.Added, "Panel #$id: ${p.title.ifBlank { "(untitled)" }}", p.type)
    }
    removedPanels.sorted().forEach { id ->
        val p = panelsFrom[id]!!
        out += DiffChange(DiffKind.Removed, "Panel #$id: ${p.title.ifBlank { "(untitled)" }}", p.type)
    }
    commonPanels.sorted().forEach { id ->
        val a = panelsFrom[id]!!
        val b = panelsTo[id]!!
        val bits = mutableListOf<String>()
        if (a.title != b.title) bits += "title: \"${a.title}\" → \"${b.title}\""
        if (a.type != b.type) bits += "type: ${a.type} → ${b.type}"
        if (a.targets != b.targets) bits += "queries changed (${a.targets.size} → ${b.targets.size})"
        if (bits.isNotEmpty()) {
            out += DiffChange(DiffKind.Changed, "Panel #$id", bits.joinToString(" • "))
        }
    }

    val varsFrom = variableIndex(from)
    val varsTo = variableIndex(to)
    val addedVars = varsTo.keys - varsFrom.keys
    val removedVars = varsFrom.keys - varsTo.keys
    val commonVars = varsFrom.keys intersect varsTo.keys
    addedVars.sorted().forEach {
        val v = varsTo[it]!!
        out += DiffChange(DiffKind.Added, "Variable \$$it", v.type)
    }
    removedVars.sorted().forEach {
        val v = varsFrom[it]!!
        out += DiffChange(DiffKind.Removed, "Variable \$$it", v.type)
    }
    commonVars.sorted().forEach {
        val a = varsFrom[it]!!
        val b = varsTo[it]!!
        val bits = mutableListOf<String>()
        if (a.type != b.type) bits += "type: ${a.type} → ${b.type}"
        if (a.query != b.query) bits += "query changed"
        if (bits.isNotEmpty()) out += DiffChange(DiffKind.Changed, "Variable \$$it", bits.joinToString(" • "))
    }
    return out
}

private data class PanelSlice(val title: String, val type: String, val targets: List<String>)
private data class VarSlice(val type: String, val query: String)

private fun panelIndex(root: JsonObject): Map<Long, PanelSlice> {
    val out = mutableMapOf<Long, PanelSlice>()
    fun walk(arr: JsonArray) {
        arr.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull()?.toLongOrNull()
            if (id != null) {
                val title = (obj["title"] as? JsonPrimitive)?.contentOrNull().orEmpty()
                val type = (obj["type"] as? JsonPrimitive)?.contentOrNull().orEmpty()
                val targets = (obj["targets"] as? JsonArray)?.mapNotNull { t ->
                    val tObj = t as? JsonObject ?: return@mapNotNull null
                    // Pull the most likely "query text" field from any datasource shape.
                    (tObj["expr"] ?: tObj["query"] ?: tObj["rawSql"] ?: tObj["queryText"])
                        ?.let { (it as? JsonPrimitive)?.contentOrNull() }
                }.orEmpty()
                out[id] = PanelSlice(title, type, targets)
            }
            // Row-collapsed nested panels
            (obj["panels"] as? JsonArray)?.let { walk(it) }
        }
    }
    (root["panels"] as? JsonArray)?.let { walk(it) }
    return out
}

private fun variableIndex(root: JsonObject): Map<String, VarSlice> {
    val out = mutableMapOf<String, VarSlice>()
    val list = (root["templating"] as? JsonObject)?.get("list") as? JsonArray ?: return out
    list.forEach { el ->
        val obj = el as? JsonObject ?: return@forEach
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull() ?: return@forEach
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull().orEmpty()
        val query = when (val q = obj["query"]) {
            is JsonPrimitive -> q.contentOrNull().orEmpty()
            is JsonObject -> q.toString()
            else -> ""
        }
        out[name] = VarSlice(type, query)
    }
    return out
}

private fun JsonPrimitive.contentOrNull(): String? = runCatching { content }.getOrNull()

/**
 * Grafana returns ISO-8601 with an offset (e.g. 2024-05-12T10:22:11-04:00). We render a short
 * absolute stamp because relative ("2h ago") is awkward when browsing versions from months back
 * and the exact timestamp is what matters for correlation with incidents.
 */
private fun formatCreated(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return runCatching {
        val instant = java.time.OffsetDateTime.parse(iso).toInstant()
        val local = instant.atZone(java.time.ZoneId.systemDefault())
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        fmt.format(local)
    }.getOrDefault(iso)
}
