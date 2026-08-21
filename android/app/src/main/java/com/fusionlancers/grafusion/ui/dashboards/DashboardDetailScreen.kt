package com.fusionlancers.grafusion.ui.dashboards

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.model.Alert
import com.fusionlancers.grafusion.data.model.AlertState
import com.fusionlancers.grafusion.data.model.Dashboard
import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.PanelData
import com.fusionlancers.grafusion.data.model.PanelGroup
import com.fusionlancers.grafusion.data.model.RawFrame
import com.fusionlancers.grafusion.data.repo.DashboardRepository
import com.fusionlancers.grafusion.ui.theme.DataPurple
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.component.LineComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Container access for deeply-nested panel composables that need to call repositories. */
private val LocalAppContainer = staticCompositionLocalOf<AppContainer?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardDetailScreen(
    container: AppContainer,
    uid: String,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var panels by remember { mutableStateOf<List<Panel>>(emptyList()) }
    var groups by remember { mutableStateOf<List<PanelGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(TimeRange.LAST_6H) }
    var customRange by remember { mutableStateOf<Pair<String, String>?>(null) }
    var customDialogOpen by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(RefreshInterval.OFF) }
    val fromExpr = customRange?.first ?: range.from
    val toExpr = customRange?.second ?: range.to
    val panelData = remember { mutableStateMapOf<Long, PanelData?>() }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }
    // Edit-mode state: workingOps is the ordered list of final panels; ops missing
    // an id in [originalIds] are treated as deleted on save.
    val workingOps = remember { mutableStateListOf<EditOp>() }
    val originalIds = remember { mutableStateListOf<Long>() }
    val nextFreshId = remember { mutableStateOf(0L) }
    var renamingIndex by remember { mutableStateOf<Int?>(null) }
    var addSheetOpen by remember { mutableStateOf(false) }
    var discardConfirmOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var starred by remember { mutableStateOf<Boolean?>(null) }
    var dashboardId by remember { mutableStateOf<Long?>(null) }
    var fullscreenPanel by remember { mutableStateOf<Panel?>(null) }
    var offline by remember { mutableStateOf(false) }
    // Collapsed row-group keys in view mode (title-based; ungrouped runs never collapse).
    val collapsedRows = remember { mutableStateListOf<String>() }

    LaunchedEffect(uid) {
        loading = true
        error = null
        runCatching { container.dashboardRepository.panelsFor(uid) }
            .onSuccess { result ->
                panels = result.panels
                groups = result.groups
                offline = result.fromCache
                loading = false
            }
            .onFailure { error = it.message ?: "Failed to load dashboard"; loading = false }
        val entity = container.accountRepository.activeEntity()
        browserUrl = entity?.let { "${it.grafanaUrl}/d/$uid" }
        // Look up starred + dashboardId from the local cache so we can toggle from here.
        container.dashboardRepository.dashboards.collect { list ->
            val row = list.firstOrNull { it.uid == uid }
            starred = row?.isStarred
            dashboardId = row?.dashboardId
        }
    }

    // Seed collapsed rows from Grafana's `collapsed: true` flag the first time a group set arrives.
    LaunchedEffect(groups) {
        collapsedRows.clear()
        groups.forEachIndexed { gIdx, group ->
            if (group.title != null && group.collapsed) {
                collapsedRows += "row_${gIdx}_${group.title}"
            }
        }
    }

    // Fetch panel data whenever panel list or range changes.
    LaunchedEffect(panels, range) {
        panels.forEach { panel ->
            if (panel.targets.isEmpty()) {
                panelData[panel.id] = PanelData(series = emptyList())
                return@forEach
            }
            panelData[panel.id] = null
            scope.launch {
                container.dashboardRepository
                    .queryPanel(panel, from = fromExpr, to = toExpr)
                    .onSuccess { panelData[panel.id] = it }
                    .onFailure { panelData[panel.id] = PanelData(series = emptyList(), error = it.message) }
            }
        }
    }

    // Auto-refresh loop.
    LaunchedEffect(refresh, panels, range) {
        val ms = refresh.millis ?: return@LaunchedEffect
        while (true) {
            delay(ms)
            panels.forEach { panel ->
                if (panel.targets.isEmpty()) return@forEach
                scope.launch {
                    container.dashboardRepository
                        .queryPanel(panel, from = fromExpr, to = toExpr)
                        .onSuccess { panelData[panel.id] = it }
                        .onFailure { panelData[panel.id] = PanelData(series = emptyList(), error = it.message) }
                }
            }
        }
    }

    CompositionLocalProvider(LocalAppContainer provides container) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (offline) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EnergyOrange.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    "Offline",
                                    color = EnergyOrange,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editMode) {
                        val dirtyCount = remember(workingOps.toList(), originalIds.toList()) {
                            computeDirtyCount(workingOps, originalIds, panels)
                        }
                        IconButton(
                            enabled = !saving,
                            onClick = {
                                if (dirtyCount > 0) discardConfirmOpen = true
                                else { editMode = false; workingOps.clear(); saveMessage = null }
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Discard edits")
                        }
                        BadgedBox(badge = { if (dirtyCount > 0) Badge { Text(dirtyCount.toString()) } }) {
                            IconButton(
                                enabled = !saving && workingOps.isNotEmpty(),
                                onClick = {
                                    saving = true
                                    saveMessage = null
                                    val ops = workingOps.map { it.toRepoOp() }
                                    scope.launch {
                                        container.dashboardRepository.saveDashboardLayout(uid, ops)
                                            .onSuccess {
                                                saveMessage = "Layout saved"
                                                editMode = false
                                                workingOps.clear()
                                                originalIds.clear()
                                                panelData.clear()
                                                runCatching { container.dashboardRepository.panelsFor(uid) }
                                                    .onSuccess { panels = it.panels; groups = it.groups; offline = it.fromCache }
                                            }
                                            .onFailure { saveMessage = "Save failed: ${it.message ?: "unknown"}" }
                                        saving = false
                                    }
                                },
                            ) {
                                if (saving) CircularProgressIndicator(
                                    color = EnergyOrange,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                ) else Icon(Icons.Filled.Check, contentDescription = "Save layout")
                            }
                        }
                    } else {
                        val id = dashboardId
                        val isStar = starred
                        if (id != null && isStar != null) {
                            IconButton(onClick = {
                                scope.launch {
                                    container.dashboardRepository.toggleStar(uid, id, !isStar)
                                }
                            }) {
                                Icon(
                                    if (isStar) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = if (isStar) "Unstar" else "Star",
                                    tint = if (isStar) EnergyOrange else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        RefreshMenu(refresh, onSelect = { refresh = it })
                        IconButton(onClick = {
                            scope.launch {
                                panels.forEach { panel ->
                                    panelData[panel.id] = null
                                    container.dashboardRepository
                                        .queryPanel(panel, from = fromExpr, to = toExpr)
                                        .onSuccess { panelData[panel.id] = it }
                                        .onFailure { panelData[panel.id] = PanelData(series = emptyList(), error = it.message) }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh now")
                        }
                        IconButton(onClick = {
                            editMode = true
                            workingOps.clear()
                            originalIds.clear()
                            val sorted = panels.sortedWith(compareBy({ it.gridY }, { it.gridX }))
                            workingOps.addAll(sorted.map { EditOp.existing(it) })
                            originalIds.addAll(sorted.map { it.id })
                            nextFreshId.value = ((sorted.maxOfOrNull { it.id } ?: 0L) + 1L).coerceAtLeast(1L)
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit layout")
                        }
                        IconButton(onClick = {
                            browserUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        }) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open in browser")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingBlock("Loading dashboard…")
                error != null -> ErrorBlock(error!!)
                panels.isEmpty() -> EmptyDashboardBlock()
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Use side-by-side grid layout on wider screens; single column on narrow.
                    val useGrid = maxWidth > 600.dp
                    val bands = remember(panels, useGrid) { groupIntoRows(panels, useGrid) }
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (saveMessage != null) {
                            item { SaveBanner(saveMessage!!) }
                        }
                        if (editMode) {
                            item { EditModeBanner(count = workingOps.size) }
                            itemsIndexed(workingOps.toList(), key = { _, op -> "edit_${op.finalId}" }) { idx, op ->
                                val previewPanel = op.sourceId?.let { s -> panels.firstOrNull { it.id == s } }
                                EditRow(
                                    op = op,
                                    previewPanel = previewPanel,
                                    data = previewPanel?.let { panelData[it.id] },
                                    isFirst = idx <= 0,
                                    isLast = idx >= workingOps.lastIndex,
                                    onMoveUp = {
                                        if (idx > 0) {
                                            val tmp = workingOps[idx - 1]
                                            workingOps[idx - 1] = workingOps[idx]
                                            workingOps[idx] = tmp
                                        }
                                    },
                                    onMoveDown = {
                                        if (idx >= 0 && idx < workingOps.lastIndex) {
                                            val tmp = workingOps[idx + 1]
                                            workingOps[idx + 1] = workingOps[idx]
                                            workingOps[idx] = tmp
                                        }
                                    },
                                    onDrag = { deltaIdx ->
                                        val target = (idx + deltaIdx).coerceIn(0, workingOps.lastIndex)
                                        if (target != idx) {
                                            val moved = workingOps.removeAt(idx)
                                            workingOps.add(target, moved)
                                        }
                                    },
                                    onResize = { w, h -> workingOps[idx] = op.copy(w = w, h = h) },
                                    onRename = { renamingIndex = idx },
                                    onDuplicate = {
                                        val fresh = nextFreshId.value.also { nextFreshId.value = it + 1 }
                                        val clone = op.copy(
                                            finalId = fresh,
                                            sourceId = op.sourceId,          // clone from same source JSON
                                            newTitle = (op.newTitle ?: previewPanel?.title?.ifBlank { null } ?: "Panel")
                                                .let { "$it (copy)" },
                                        )
                                        workingOps.add(idx + 1, clone)
                                    },
                                    onDelete = { workingOps.removeAt(idx) },
                                )
                            }
                            item {
                                OutlinedButton(
                                    onClick = { addSheetOpen = true },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add panel")
                                }
                            }
                        } else {
                            item {
                                TimeRangeBar(
                                    range = range,
                                    customLabel = customRange?.let { "${it.first} → ${it.second}" },
                                    onSelect = { customRange = null; range = it },
                                    onOpenCustom = { customDialogOpen = true },
                                )
                            }
                            renderGroupedPanels(
                                groups = groups,
                                fallbackBands = bands,
                                useGrid = useGrid,
                                panelData = panelData,
                                grafanaUrl = browserUrl?.substringBefore("/d/"),
                                dashboardUid = uid,
                                collapsedRows = collapsedRows,
                                onToggleRow = { key ->
                                    if (collapsedRows.contains(key)) collapsedRows.remove(key)
                                    else collapsedRows.add(key)
                                },
                                onExpand = { fullscreenPanel = it },
                                onCopyLink = { url -> copyToClipboard(context, url) },
                                onOpenBrowser = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (customDialogOpen) {
        CustomTimeRangeDialog(
            initialFrom = customRange?.first ?: range.from,
            initialTo = customRange?.second ?: range.to,
            onDismiss = { customDialogOpen = false },
            onApply = { f, t ->
                customRange = f to t
                customDialogOpen = false
            },
        )
    }

    renamingIndex?.let { idx ->
        val current = workingOps.getOrNull(idx) ?: run { renamingIndex = null; return@let }
        val fallback = current.sourceId?.let { s -> panels.firstOrNull { it.id == s }?.title }.orEmpty()
        RenameDialog(
            initial = current.newTitle ?: fallback,
            onDismiss = { renamingIndex = null },
            onApply = { newTitle ->
                workingOps[idx] = current.copy(newTitle = newTitle.trim().ifBlank { null })
                renamingIndex = null
            },
        )
    }

    if (addSheetOpen) {
        AddPanelSheet(
            onDismiss = { addSheetOpen = false },
            onPick = { type, title ->
                val fresh = nextFreshId.value.also { nextFreshId.value = it + 1 }
                workingOps.add(EditOp.new(finalId = fresh, type = type, title = title))
                addSheetOpen = false
            },
        )
    }

    if (discardConfirmOpen) {
        AlertDialog(
            onDismissRequest = { discardConfirmOpen = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this dashboard's layout will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    discardConfirmOpen = false
                    editMode = false
                    workingOps.clear()
                    originalIds.clear()
                    saveMessage = null
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { discardConfirmOpen = false }) { Text("Keep editing") } },
        )
    }

    fullscreenPanel?.let { panel ->
        Dialog(
            onDismissRequest = { fullscreenPanel = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            panel.title.ifBlank { "Panel ${panel.id}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { fullscreenPanel = null }) {
                            Icon(Icons.Filled.FullscreenExit, contentDescription = "Close fullscreen")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxSize()) {
                        PanelCard(panel = panel, data = panelData[panel.id])
                    }
                }
            }
        }
    }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Grafusion", text))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

private data class PanelBand(val key: String, val panels: List<Panel>, val heightDp: Int)

/** Group panels into layout rows respecting gridPos when useGrid=true; otherwise one panel per row. */
private fun groupIntoRows(all: List<Panel>, useGrid: Boolean): List<PanelBand> {
    val sorted = all.sortedWith(compareBy({ it.gridY }, { it.gridX }))
    if (!useGrid) {
        return sorted.map { PanelBand(key = "p${it.id}", panels = listOf(it), heightDp = it.gridH * 30) }
    }
    val bands = mutableListOf<PanelBand>()
    var current = mutableListOf<Panel>()
    var currentY = -1
    for (p in sorted) {
        if (current.isEmpty()) {
            current += p
            currentY = p.gridY
        } else if (kotlin.math.abs(p.gridY - currentY) <= 2 && current.sumOf { it.gridW } + p.gridW <= 24) {
            current += p
        } else {
            bands += PanelBand(
                key = current.joinToString("_") { "p${it.id}" },
                panels = current.toList(),
                heightDp = (current.maxOf { it.gridH } * 30),
            )
            current = mutableListOf(p)
            currentY = p.gridY
        }
    }
    if (current.isNotEmpty()) {
        bands += PanelBand(
            key = current.joinToString("_") { "p${it.id}" },
            panels = current.toList(),
            heightDp = (current.maxOf { it.gridH } * 30),
        )
    }
    return bands
}

@Composable
private fun PanelRow(
    band: PanelBand,
    panelData: androidx.compose.runtime.snapshots.SnapshotStateMap<Long, PanelData?>,
    grafanaUrl: String?,
    dashboardUid: String,
    onExpand: (Panel) -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        band.panels.forEach { panel ->
            Box(Modifier.weight(panel.gridW.toFloat().coerceAtLeast(1f)).fillMaxWidth()) {
                PanelCard(
                    panel = panel,
                    data = panelData[panel.id],
                    onExpand = { onExpand(panel) },
                    onCopyLink = grafanaUrl?.let { { onCopyLink(panelUrl(it, dashboardUid, panel.id)) } },
                    onOpenBrowser = grafanaUrl?.let { { onOpenBrowser(panelUrl(it, dashboardUid, panel.id)) } },
                )
            }
        }
    }
}

private fun panelUrl(baseUrl: String, dashboardUid: String, panelId: Long): String =
    "${baseUrl.trimEnd('/')}/d/$dashboardUid?viewPanel=$panelId"

private enum class TimeRange(val label: String, val from: String, val to: String) {
    LAST_5M("5m", "now-5m", "now"),
    LAST_1H("1h", "now-1h", "now"),
    LAST_6H("6h", "now-6h", "now"),
    LAST_24H("24h", "now-24h", "now"),
    LAST_7D("7d", "now-7d", "now"),
    LAST_30D("30d", "now-30d", "now"),
}

private enum class RefreshInterval(val label: String, val millis: Long?) {
    OFF("Off", null),
    LIVE("Live (1s)", 1_000L),
    S2("2s", 2_000L),
    S5("5s", 5_000L),
    S10("10s", 10_000L),
    S30("30s", 30_000L),
    M1("1m", 60_000L),
    M5("5m", 5 * 60_000L),
    M15("15m", 15 * 60_000L),
}

@Composable
private fun RefreshMenu(current: RefreshInterval, onSelect: (RefreshInterval) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Timer, contentDescription = "Auto refresh: ${current.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RefreshInterval.entries.forEach { r ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "Refresh every ${r.label}",
                            color = if (r == current) EnergyOrange else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (r == current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = { onSelect(r); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun TimeRangeBar(
    range: TimeRange,
    customLabel: String?,
    onSelect: (TimeRange) -> Unit,
    onOpenCustom: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val options = TimeRange.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, opt ->
                SegmentedButton(
                    selected = customLabel == null && range == opt,
                    onClick = { onSelect(opt) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    label = { Text(opt.label, fontWeight = FontWeight.Medium) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = onOpenCustom,
                label = { Text(customLabel ?: "Custom range…", maxLines = 1) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (customLabel != null) EnergyOrange.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                    labelColor = if (customLabel != null) EnergyOrange else MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun CustomTimeRangeDialog(
    initialFrom: String,
    initialTo: String,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
) {
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Custom time range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Use Grafana time expressions (now, now-6h, now-1d/d) or millisecond timestamps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("From") },
                    placeholder = { Text("now-6h") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To") },
                    placeholder = { Text("now") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            val f = from.trim()
                            val t = to.trim()
                            if (f.isNotBlank() && t.isNotBlank()) onApply(f, t)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                    ) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun EditModeBanner(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EnergyOrange.copy(alpha = 0.12f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Editing $count panel${if (count == 1) "" else "s"} - long-press ≡ to drag, use ⋮ to rename/duplicate/delete, ✓ to save to Grafana.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SaveBanner(message: String) {
    val isError = message.startsWith("Save failed", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else EnergyOrange.copy(alpha = 0.16f)
        ),
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A single planned entry in the edited panel list. For existing panels, [sourceId] == [finalId]
 * and [newType] is null; duplicates keep [sourceId] pointing at the original; fresh panels have
 * [sourceId] = null and [newType] set.
 */
internal data class EditOp(
    val finalId: Long,
    val sourceId: Long?,
    val newType: String?,
    val newTitle: String?,
    val w: Int,
    val h: Int,
) {
    fun toRepoOp() = DashboardRepository.LayoutOp(
        finalId = finalId,
        sourceId = sourceId,
        newType = newType,
        newTitle = newTitle,
        w = w,
        h = h,
    )

    companion object {
        fun existing(p: Panel) = EditOp(
            finalId = p.id,
            sourceId = p.id,
            newType = null,
            newTitle = null,
            w = p.gridW.coerceIn(1, 24),
            h = p.gridH.coerceIn(1, 40),
        )

        fun new(finalId: Long, type: String, title: String) = EditOp(
            finalId = finalId,
            sourceId = null,
            newType = type,
            newTitle = title,
            w = 12,
            h = 8,
        )
    }
}

/** Any op whose finalId isn't an original id is added/duplicated; any missing original is deleted; renames/resizes count too. */
internal fun computeDirtyCount(ops: List<EditOp>, originals: List<Long>, panels: List<Panel>): Int {
    val originalSet = originals.toSet()
    val presentSet = ops.map { it.finalId }.toSet()
    val deletions = originals.count { it !in presentSet }
    val additions = ops.count { it.finalId !in originalSet }
    val order = ops.mapNotNull { if (it.sourceId != null && it.sourceId == it.finalId) it.finalId else null }
    val reordered = order != originals.filter { it in presentSet }
    val edits = ops.count { op ->
        val src = op.sourceId?.let { s -> panels.firstOrNull { it.id == s } } ?: return@count false
        (op.newTitle != null && op.newTitle != src.title) || op.w != src.gridW || op.h != src.gridH
    }
    return deletions + additions + edits + (if (reordered) 1 else 0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRow(
    op: EditOp,
    previewPanel: Panel?,
    data: PanelData?,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDrag: (Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 220.dp.toPx() }
    var dragOffset by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val displayTitle = op.newTitle ?: previewPanel?.title?.ifBlank { null } ?: "Panel ${op.finalId}"
    val badgeText = when {
        op.sourceId == null -> "NEW"
        op.sourceId != op.finalId -> "COPY"
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) EnergyOrange.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 6.dp else 1.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Long-press-and-drag handle.
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = if (dragging) EnergyOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(28.dp)
                        .pointerInput(op.finalId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = true; dragOffset = 0f },
                                onDragCancel = { dragging = false; dragOffset = 0f },
                                onDragEnd = {
                                    val steps = (dragOffset / rowHeightPx).toInt()
                                    if (steps != 0) onDrag(steps)
                                    dragging = false; dragOffset = 0f
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    dragOffset += drag.y
                                    val steps = (dragOffset / rowHeightPx).toInt()
                                    if (kotlin.math.abs(steps) >= 1) {
                                        onDrag(steps)
                                        dragOffset -= steps * rowHeightPx
                                    }
                                },
                            )
                        },
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EnergyOrange.copy(alpha = 0.18f),
                    ) {
                        Text(
                            badgeText,
                            color = EnergyOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Panel actions", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Title, null) },
                            onClick = { menuOpen = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { menuOpen = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            if (previewPanel != null) {
                Box(Modifier.fillMaxWidth()) {
                    PanelCard(panel = previewPanel.copy(title = displayTitle), data = data)
                }
            } else {
                // Placeholder card for a freshly-added panel.
                Card(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = EnergyOrange.copy(alpha = 0.08f)),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "New ${op.newType ?: "text"} panel - save to Grafana to configure",
                            style = MaterialTheme.typography.bodySmall,
                            color = EnergyOrange,
                        )
                    }
                }
            }
            SizeControls(width = op.w, height = op.h, onResize = onResize)
        }
    }
}

private val WIDTH_PRESETS = listOf(6 to "¼", 8 to "⅓", 12 to "½", 16 to "⅔", 18 to "¾", 24 to "Full")
private val HEIGHT_PRESETS = listOf(6 to "S", 8 to "M", 12 to "L", 16 to "XL", 20 to "XXL")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SizeControls(width: Int, height: Int, onResize: (Int, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("W", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            IconButton(
                onClick = { onResize((width - 1).coerceAtLeast(1), height) },
                enabled = width > 1,
                modifier = Modifier.size(28.dp),
            ) { Text("−", fontWeight = FontWeight.Bold, color = EnergyOrange) }
            Text(
                width.toString().padStart(2),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(20.dp),
            )
            IconButton(
                onClick = { onResize((width + 1).coerceAtMost(24), height) },
                enabled = width < 24,
                modifier = Modifier.size(28.dp),
            ) { Text("+", fontWeight = FontWeight.Bold, color = EnergyOrange) }
            WIDTH_PRESETS.forEach { (w, label) ->
                FilterChip(
                    selected = width == w,
                    onClick = { onResize(w, height) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EnergyOrange.copy(alpha = 0.18f),
                        selectedLabelColor = EnergyOrange,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("H", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            IconButton(
                onClick = { onResize(width, (height - 1).coerceAtLeast(1)) },
                enabled = height > 1,
                modifier = Modifier.size(28.dp),
            ) { Text("−", fontWeight = FontWeight.Bold, color = EnergyOrange) }
            Text(
                height.toString().padStart(2),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(20.dp),
            )
            IconButton(
                onClick = { onResize(width, (height + 1).coerceAtMost(40)) },
                enabled = height < 40,
                modifier = Modifier.size(28.dp),
            ) { Text("+", fontWeight = FontWeight.Bold, color = EnergyOrange) }
            HEIGHT_PRESETS.forEach { (h, label) ->
                FilterChip(
                    selected = height == h,
                    onClick = { onResize(width, h) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EnergyOrange.copy(alpha = 0.18f),
                        selectedLabelColor = EnergyOrange,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename panel") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Panel title") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onApply(text) },
                colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class NewPanelType(val id: String, val label: String, val hint: String)

private val NEW_PANEL_TYPES = listOf(
    NewPanelType("timeseries", "Time series", "Line chart over time"),
    NewPanelType("stat", "Stat", "Single big-number reading"),
    NewPanelType("gauge", "Gauge", "Bounded reading with thresholds"),
    NewPanelType("bargauge", "Bar gauge", "Horizontal bars per series"),
    NewPanelType("barchart", "Bar chart", "Categorical column chart"),
    NewPanelType("piechart", "Pie chart", "Distribution across categories"),
    NewPanelType("table", "Table", "Rows and columns of raw values"),
    NewPanelType("heatmap", "Heatmap", "Bucketed density over time"),
    NewPanelType("state-timeline", "State timeline", "Discrete state changes over time"),
    NewPanelType("logs", "Logs", "Log lines from Loki or similar"),
    NewPanelType("text", "Text / Markdown", "Static notes or headings"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPanelSheet(onDismiss: () -> Unit, onPick: (type: String, title: String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("New panel") }
    var pickedType by remember { mutableStateOf(NEW_PANEL_TYPES.first().id) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Add a panel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "The panel scaffolds a blank definition on Grafana - open it in Grafana to attach queries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            Spacer(Modifier.height(6.dp))
            Column {
                NEW_PANEL_TYPES.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        FilterChip(
                            selected = pickedType == t.id,
                            onClick = { pickedType = t.id },
                            label = { Text(t.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EnergyOrange.copy(alpha = 0.18f),
                                selectedLabelColor = EnergyOrange,
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(t.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onPick(pickedType, title.trim().ifBlank { "New panel" }) },
                    colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Render panels grouped by Grafana row containers. When [groups] is empty (older parse path),
 * fall back to the flat [fallbackBands] layout.
 */
private fun LazyListScope.renderGroupedPanels(
    groups: List<PanelGroup>,
    fallbackBands: List<PanelBand>,
    useGrid: Boolean,
    panelData: androidx.compose.runtime.snapshots.SnapshotStateMap<Long, PanelData?>,
    grafanaUrl: String?,
    dashboardUid: String,
    collapsedRows: List<String>,
    onToggleRow: (String) -> Unit,
    onExpand: (Panel) -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
) {
    if (groups.isEmpty()) {
        items(fallbackBands, key = { it.key }) { band ->
            PanelRow(
                band = band,
                panelData = panelData,
                grafanaUrl = grafanaUrl,
                dashboardUid = dashboardUid,
                onExpand = onExpand,
                onCopyLink = onCopyLink,
                onOpenBrowser = onOpenBrowser,
            )
        }
        return
    }
    groups.forEachIndexed { gIdx, group ->
        val rowKey = "row_${gIdx}_${group.title ?: "u"}"
        // Presence in collapsedRows always means "collapsed"; the parent seeds default-collapsed rows on load.
        val effectiveCollapsed = collapsedRows.contains(rowKey)
        if (group.title != null) {
            item(key = "$rowKey.header") {
                RowHeader(
                    title = group.title,
                    count = group.panels.size,
                    collapsed = effectiveCollapsed,
                    onToggle = { onToggleRow(rowKey) },
                )
            }
        }
        if (group.title == null || !effectiveCollapsed) {
            val bands = groupIntoRows(group.panels, useGrid)
            items(bands, key = { "$rowKey.${it.key}" }) { band ->
                PanelRow(
                    band = band,
                    panelData = panelData,
                    grafanaUrl = grafanaUrl,
                    dashboardUid = dashboardUid,
                    onExpand = onExpand,
                    onCopyLink = onCopyLink,
                    onOpenBrowser = onOpenBrowser,
                )
            }
        }
    }
}

@Composable
private fun RowHeader(title: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (collapsed) "Expand row" else "Collapse row",
                tint = EnergyOrange,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PanelCard(
    panel: Panel,
    data: PanelData?,
    onExpand: (() -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
    onOpenBrowser: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardW = maxWidth
            val padding = if (cardW < 140.dp) 8.dp else 14.dp
            Column(Modifier.padding(padding)) {
                PanelHeader(
                    panel = panel,
                    cardWidth = cardW,
                    onExpand = onExpand,
                    onCopyLink = onCopyLink,
                    onOpenBrowser = onOpenBrowser,
                )
                Spacer(Modifier.height(if (cardW < 140.dp) 6.dp else 12.dp))
                val selfLoads = panel.type == "alertlist" || panel.type == "dashlist"
                when {
                    selfLoads -> PanelBody(panel = panel, data = data ?: PanelData(emptyList()), cardWidth = cardW)
                    data == null -> PanelLoading()
                    data.error != null -> PanelError(data.error)
                    data.series.isEmpty() && data.frames.isEmpty() -> PanelNoData()
                    else -> PanelBody(panel = panel, data = data, cardWidth = cardW)
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    panel: Panel,
    cardWidth: Dp,
    onExpand: (() -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
    onOpenBrowser: (() -> Unit)? = null,
) {
    val showTypeBadge = cardWidth >= 220.dp
    val titleStyle = when {
        cardWidth < 120.dp -> MaterialTheme.typography.labelMedium
        cardWidth < 180.dp -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.titleMedium
    }
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            panel.title.ifBlank { "Panel ${panel.id}" },
            modifier = Modifier.weight(1f),
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTypeBadge) {
            Text(
                panel.type,
                style = MaterialTheme.typography.labelSmall,
                color = EnergyOrange,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onExpand != null || onCopyLink != null || onOpenBrowser != null) {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Panel options", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    onExpand?.let {
                        DropdownMenuItem(
                            text = { Text("Expand fullscreen") },
                            leadingIcon = { Icon(Icons.Filled.Fullscreen, null) },
                            onClick = { menuOpen = false; it() },
                        )
                    }
                    onCopyLink?.let {
                        DropdownMenuItem(
                            text = { Text("Copy panel link") },
                            leadingIcon = { Icon(Icons.Filled.Link, null) },
                            onClick = { menuOpen = false; it() },
                        )
                    }
                    onOpenBrowser?.let {
                        DropdownMenuItem(
                            text = { Text("Open in Grafana") },
                            leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) },
                            onClick = { menuOpen = false; it() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelBody(panel: Panel, data: PanelData, cardWidth: Dp) {
    when (panel.type) {
        "timeseries", "graph" -> TimeseriesPanel(data)
        "stat", "gauge" -> StatOrGaugePanel(panel, data, cardWidth)
        "bargauge" -> BarGaugePanel(panel, data)
        "table" -> TablePanel(data)
        "barchart" -> BarChartPanel(panel, data)
        "piechart" -> PieChartPanel(panel, data)
        "heatmap" -> HeatmapPanel(data)
        "state-timeline", "status-history" -> StateTimelinePanel(data)
        "logs" -> LogsPanel(data)
        "text" -> TextPanel(panel)
        "geomap", "worldmap-panel" -> GeomapPanel(data)
        "alertlist" -> AlertListPanel(panel)
        "dashlist" -> DashListPanel(panel)
        else -> UnsupportedPanel(panel.type, null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeseriesPanel(data: PanelData) {
    val validSeries = remember(data) {
        data.series.mapNotNull { s ->
            val pairs = s.timestamps.zip(s.values).mapNotNull { (t, v) -> if (v == null) null else t to v }
            if (pairs.isEmpty()) null else s to pairs
        }
    }
    if (validSeries.isEmpty()) { PanelNoData(); return }
    val hidden = remember(validSeries) { mutableStateListOf<String>() }
    val visibleSeries = validSeries.filter { it.first.name !in hidden }
    val producer = remember { CartesianChartModelProducer() }
    val lineColors = listOf(EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF22D3EE))
    // Compose lines matching only the visible subset, in the same order, so colors stay stable
    // relative to the visible slice (Vico maps line providers positionally).
    LaunchedEffect(visibleSeries) {
        if (visibleSeries.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            lineSeries {
                visibleSeries.forEach { (_, pairs) ->
                    series(x = pairs.map { it.first.toDouble() }, y = pairs.map { it.second })
                }
            }
        }
    }
    if (visibleSeries.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("All series hidden - tap a legend chip to show", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    } else {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            val layer = rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    lineColors.map { color ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(color)),
                        )
                    }
                )
            )
            CartesianChartHost(
                chart = rememberCartesianChart(
                    layer,
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = { _, value, _ ->
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value.toLong()))
                        }
                    ),
                ),
                modelProducer = producer,
                scrollState = rememberVicoScrollState(scrollEnabled = false),
            )
        }
    }
    if (validSeries.size > 1) {
        Spacer(Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            validSeries.take(12).forEachIndexed { idx, (s, pairs) ->
                val isHidden = s.name in hidden
                val chipColor = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                else lineColors[idx % lineColors.size]
                val last = pairs.last().second
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (isHidden) 0.04f else 0.06f),
                            RoundedCornerShape(50),
                        )
                        .pointerInput(s.name) {
                            detectTapGestures(onTap = {
                                if (isHidden) hidden.remove(s.name) else hidden.add(s.name)
                            })
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(chipColor, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (!isHidden) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatCompact(last),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun formatCompact(v: Double): String {
    val abs = kotlin.math.abs(v)
    return when {
        abs >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", v / 1_000_000_000)
        abs >= 1_000_000 -> String.format(Locale.US, "%.1fM", v / 1_000_000)
        abs >= 1_000 -> String.format(Locale.US, "%.1fk", v / 1_000)
        abs >= 10 -> String.format(Locale.US, "%.0f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }
}

@Composable
private fun BarChartPanel(panel: Panel, data: PanelData) {
    // Grafana barchart options.orientation: "auto" | "vertical" | "horizontal".
    // "auto" flips to horizontal when there are many categories, matching Grafana's default heuristic.
    val orientationOpt = (panel.options?.get("orientation") as? kotlinx.serialization.json.JsonPrimitive)?.content
    val showValues = (panel.options?.get("showValue") as? kotlinx.serialization.json.JsonPrimitive)?.content != "never"

    val grouped = remember(data) { extractGroupedBars(data) }
    if (grouped != null && grouped.seriesValues.size > 1) {
        GroupedBarChart(grouped)
        return
    }
    val labeledPairs = grouped?.toSinglePairs() ?: extractBarPairs(data)
    if (labeledPairs.isEmpty()) { PanelNoData(); return }
    val horizontal = orientationOpt == "horizontal" ||
        (orientationOpt == null || orientationOpt == "auto") && labeledPairs.size > 8
    if (horizontal) {
        HorizontalBarChart(labeledPairs, panel.unit, panel.decimals, showValues)
    } else {
        SingleSeriesBarChart(labeledPairs)
    }
}

@Composable
private fun HorizontalBarChart(
    pairs: List<Pair<String, Double>>,
    unit: String?,
    decimals: Int?,
    showValues: Boolean,
) {
    val maxVal = pairs.maxOfOrNull { kotlin.math.abs(it.second) }?.takeIf { it > 0.0 } ?: 1.0
    val labelW = 96.dp
    Column(Modifier.fillMaxWidth()) {
        pairs.take(14).forEachIndexed { idx, (label, v) ->
            val frac = (v / maxVal).coerceIn(-1.0, 1.0).toFloat().let { kotlin.math.abs(it) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.width(labelW),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .height(16.dp)
                            .background(barSeriesColor(idx), RoundedCornerShape(3.dp))
                    )
                    if (showValues) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                formatValue(v, unit, decimals),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 6.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(start = labelW + 6.dp)) {
            Text(
                "0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
            )
            Text(
                formatValue(maxVal, unit, decimals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun SingleSeriesBarChart(pairs: List<Pair<String, Double>>) {
    val values = pairs.map { it.second }
    val labels = pairs.map { it.first }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(pairs) {
        producer.runTransaction {
            columnSeries { series(values) }
        }
    }
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = { _, value, _ ->
                        labels.getOrNull(value.toInt()) ?: value.toInt().toString()
                    }
                ),
            ),
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = true),
        )
    }
}

@Composable
private fun GroupedBarChart(g: GroupedBars) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(g) {
        producer.runTransaction {
            columnSeries {
                g.seriesValues.forEach { values -> series(values) }
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        mergeMode = { ColumnCartesianLayer.MergeMode.Grouped() },
                    ),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = { _, value, _ ->
                            g.categories.getOrNull(value.toInt()) ?: value.toInt().toString()
                        }
                    ),
                ),
                modelProducer = producer,
                scrollState = rememberVicoScrollState(scrollEnabled = true),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            g.seriesNames.forEachIndexed { idx, name ->
                LegendSwatch(name = name, color = barSeriesColor(idx))
            }
        }
    }
}

@Composable
private fun LegendSwatch(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun barSeriesColor(idx: Int): Color = listOf(
    EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA),
    Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF22D3EE),
)[idx % 7]

private data class GroupedBars(
    val categories: List<String>,
    val seriesNames: List<String>,
    val seriesValues: List<List<Double>>,
) {
    fun toSinglePairs(): List<Pair<String, Double>>? {
        if (seriesValues.size != 1) return null
        val vs = seriesValues[0]
        return categories.zip(vs)
    }
}

/**
 * Detect a grouped-bar frame: one string column + >= 1 number columns. Returns null
 * if the shape doesn't fit so callers fall back to the flat single-series path.
 */
private fun extractGroupedBars(data: PanelData): GroupedBars? {
    val frame = data.frames.firstOrNull() ?: return null
    val strIdx = frame.fieldTypes.indexOfFirst { it == "string" }
    if (strIdx < 0) return null
    val numIdxs = frame.fieldTypes.mapIndexedNotNull { i, t -> if (t == "number") i else null }
    if (numIdxs.isEmpty()) return null
    val rowCount = frame.rowCount.coerceAtMost(30)
    if (rowCount == 0) return null
    val categories = (0 until rowCount).map { r -> frame.columns[strIdx].getOrNull(r)?.toString().orEmpty() }
    val seriesNames = numIdxs.map { frame.fieldNames.getOrNull(it) ?: "series" }
    val seriesValues = numIdxs.map { idx ->
        (0 until rowCount).map { r -> (frame.columns[idx].getOrNull(r) as? Number)?.toDouble() ?: 0.0 }
    }
    return GroupedBars(categories, seriesNames, seriesValues)
}

private fun reduceStat(values: List<Double>, calc: String): Double? {
    if (values.isEmpty()) return null
    return when (calc) {
        "last", "lastNotNull" -> values.last()
        "first", "firstNotNull" -> values.first()
        "mean" -> values.average()
        "min" -> values.min()
        "max" -> values.max()
        "sum", "total" -> values.sum()
        "count" -> values.size.toDouble()
        "range" -> values.max() - values.min()
        else -> values.last()
    }
}

/** Returns (label, value) pairs sorted by descending value, capped at 20. */
private fun extractBarPairs(data: PanelData): List<Pair<String, Double>> {
    val pairs = mutableListOf<Pair<String, Double>>()
    val preferredLabelKeys = listOf("country", "city", "type", "scenario", "name", "instance", "job", "service")
    for (frame in data.frames) {
        val numIdx = frame.fieldTypes.indexOfFirst { it == "number" }
        if (numIdx < 0) continue
        val labels = frame.fieldLabels.getOrNull(numIdx).orEmpty()
        if (labels.isNotEmpty()) {
            // Case A: one frame per label combo (Prometheus).
            val labelKey = preferredLabelKeys.firstOrNull { labels[it]?.isNotBlank() == true }
                ?: labels.keys.firstOrNull { it != "__name__" }
            val label = labelKey?.let { labels[it] }.orEmpty()
            val v = (frame.columns[numIdx].lastOrNull { it != null } as? Number)?.toDouble() ?: continue
            if (label.isNotBlank()) pairs += label to v
            continue
        }
        // Case B: label column + value column in one frame.
        val strIdx = frame.fieldTypes.indexOfFirst { it == "string" }
        if (strIdx >= 0) {
            for (r in 0 until frame.rowCount) {
                val lbl = frame.columns[strIdx].getOrNull(r)?.toString().orEmpty()
                val v = (frame.columns[numIdx].getOrNull(r) as? Number)?.toDouble() ?: continue
                if (lbl.isNotBlank()) pairs += lbl to v
            }
        }
    }
    return pairs.sortedByDescending { it.second }.take(20)
}

@Composable
private fun StatOrGaugePanel(panel: Panel, data: PanelData, cardWidth: Dp) {
    val calc = remember(panel.options) {
        val reduce = panel.options?.get("reduceOptions") as? kotlinx.serialization.json.JsonObject
        val calcs = reduce?.get("calcs") as? kotlinx.serialization.json.JsonArray
        (calcs?.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "lastNotNull"
    }
    // Grafana stat "graphMode": "area" (default) draws a sparkline behind the value.
    // "none" suppresses it. Gauge panels never sparkline.
    val graphMode = remember(panel.options, panel.type) {
        if (panel.type == "gauge") "none"
        else (panel.options?.get("graphMode") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "area"
    }
    // When multiple series are returned (or the reducer emits per-row values in a table frame),
    // Grafana lays them out as a grid of mini-stat cells instead of one big number.
    if (data.series.size > 1) {
        StatGrid(panel = panel, data = data, calc = calc, graphMode = graphMode, cardWidth = cardWidth)
        return
    }
    val firstSeries = data.series.firstOrNull()
    val seriesValues = firstSeries?.values?.filterNotNull().orEmpty()
    val frameValues = data.frames.firstOrNull()?.let { frame ->
        frame.columns.getOrNull(frame.fieldTypes.indexOfFirst { it == "number" })
            ?.mapNotNull { (it as? Number)?.toDouble() }
    }.orEmpty()
    val values = if (seriesValues.isNotEmpty()) seriesValues else frameValues
    val latest = reduceStat(values, calc)
    val display = when {
        latest == null -> "-"
        else -> formatValue(latest, panel.unit, panel.decimals)
    }
    val availableDp = (cardWidth.value - 32f).coerceAtLeast(48f)
    val approxCharDp = availableDp / display.length.coerceAtLeast(1)
    val fontSize = (approxCharDp * 1.9f).coerceIn(16f, 34f)

    val sparklinePoints = remember(firstSeries, graphMode) {
        if (graphMode != "area" && graphMode != "line") return@remember emptyList()
        val pairs = firstSeries?.let { s -> s.timestamps.zip(s.values) }?.mapNotNull { (t, v) ->
            v?.let { t to it }
        }.orEmpty()
        if (pairs.size < 2) emptyList() else pairs
    }

    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
        if (sparklinePoints.isNotEmpty()) {
            Sparkline(
                pairs = sparklinePoints,
                color = EnergyOrange,
                filled = graphMode == "area",
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            display,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = EnergyOrange,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatGrid(
    panel: Panel,
    data: PanelData,
    calc: String,
    graphMode: String,
    cardWidth: Dp,
) {
    // Pick a column count that keeps each cell wide enough for a legible value.
    val cols = when {
        cardWidth < 220.dp -> 2
        cardWidth < 360.dp -> 3
        else -> 4
    }
    val cellWidth = ((cardWidth.value - 16f - (cols - 1) * 8f) / cols).coerceAtLeast(72f).dp
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        data.series.forEach { s ->
            val values = s.values.filterNotNull()
            val reduced = reduceStat(values, calc)
            val display = reduced?.let { formatValue(it, panel.unit, panel.decimals) } ?: "-"
            val approxCharDp = (cellWidth.value - 12f) / display.length.coerceAtLeast(1)
            val fontSize = (approxCharDp * 1.9f).coerceIn(14f, 22f)
            val sparklinePairs = if (graphMode == "area" || graphMode == "line") {
                s.timestamps.zip(s.values).mapNotNull { (t, v) -> v?.let { t to it } }
                    .takeIf { it.size >= 2 } ?: emptyList()
            } else emptyList()
            Column(
                modifier = Modifier
                    .width(cellWidth)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    s.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (sparklinePairs.isNotEmpty()) {
                        Sparkline(
                            pairs = sparklinePairs,
                            color = EnergyOrange,
                            filled = graphMode == "area",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        display,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = EnergyOrange,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Sparkline(
    pairs: List<Pair<Long, Double>>,
    color: Color,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val minT = pairs.first().first.toDouble()
        val maxT = pairs.last().first.toDouble()
        val tSpan = (maxT - minT).takeIf { it > 0.0 } ?: 1.0
        val ys = pairs.map { it.second }
        val minY = ys.min()
        val maxY = ys.max()
        val ySpan = (maxY - minY).takeIf { it > 0.0 } ?: 1.0
        // Reserve top/bottom padding so the sparkline sits behind the number without cropping.
        val vPad = h * 0.15f
        val plotH = h - vPad * 2f

        fun x(t: Long) = ((t.toDouble() - minT) / tSpan).toFloat() * w
        fun y(v: Double) = vPad + plotH - ((v - minY) / ySpan).toFloat() * plotH

        val linePath = Path().apply {
            moveTo(x(pairs.first().first), y(pairs.first().second))
            for (i in 1 until pairs.size) lineTo(x(pairs[i].first), y(pairs[i].second))
        }
        if (filled) {
            val areaPath = Path().apply {
                moveTo(x(pairs.first().first), h)
                for (p in pairs) lineTo(x(p.first), y(p.second))
                lineTo(x(pairs.last().first), h)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)),
                ),
            )
        }
        drawPath(
            path = linePath,
            color = color.copy(alpha = 0.65f),
            style = Stroke(width = 2f),
        )
    }
}

@Composable
private fun BarGaugePanel(panel: Panel, data: PanelData) {
    // Each series -> one row: label + horizontal bar + reduced value.
    val calc = remember(panel.options) {
        val reduce = panel.options?.get("reduceOptions") as? kotlinx.serialization.json.JsonObject
        val calcs = reduce?.get("calcs") as? kotlinx.serialization.json.JsonArray
        (calcs?.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "lastNotNull"
    }
    // Prefer series (already labeled); fall back to bar-pair extraction for table-shaped frames.
    val rows: List<Pair<String, Double>> = remember(data, calc) {
        val fromSeries = data.series.mapNotNull { s ->
            val v = reduceStat(s.values.filterNotNull(), calc) ?: return@mapNotNull null
            (s.name.ifBlank { "value" }) to v
        }
        if (fromSeries.isNotEmpty()) fromSeries else extractBarPairs(data)
    }
    if (rows.isEmpty()) { PanelNoData(); return }
    // Bar range: percent units use 0..100; otherwise scale to max value.
    val isPercent = panel.unit?.startsWith("percent") == true
    val maxVal = if (isPercent) 100.0 else (rows.maxOf { it.second }.takeIf { it > 0 } ?: 1.0)
    // Grafana's displayMode: "basic" (solid) | "gradient" (color grade) | "lcd" (retro LED cells).
    val displayMode = (panel.options?.get("displayMode") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "gradient"
    val thresholds = remember(panel) { parseThresholds(panel) }

    Column(Modifier.fillMaxWidth()) {
        rows.take(12).forEach { (label, v) ->
            val frac = (v / maxVal).coerceIn(0.0, 1.0).toFloat()
            val display = formatValue(v, panel.unit, panel.decimals)
            val valueColor = thresholdColorFor(v, thresholds, maxVal, isPercent)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        display,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = valueColor,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                BarGaugeBar(
                    frac = frac,
                    displayMode = displayMode,
                    thresholds = thresholds,
                    maxVal = maxVal,
                    isPercent = isPercent,
                    valueColor = valueColor,
                )
            }
        }
    }
}

@Composable
private fun BarGaugeBar(
    frac: Float,
    displayMode: String,
    thresholds: List<Threshold>,
    maxVal: Double,
    isPercent: Boolean,
    valueColor: Color,
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    when (displayMode) {
        "lcd" -> {
            // 20 discrete "LED" cells that light up left-to-right.
            val cells = 20
            val lit = (frac * cells).toInt().coerceIn(0, cells)
            Row(
                Modifier.fillMaxWidth().height(12.dp),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            ) {
                repeat(cells) { i ->
                    // Each cell picks its own threshold color based on the fraction it represents,
                    // so a bar can grade from green -> yellow -> red across its length.
                    val cellFrac = (i + 0.5f) / cells
                    val cellVal = cellFrac * maxVal
                    val cellColor = if (i < lit)
                        thresholdColorFor(cellVal, thresholds, maxVal, isPercent).copy(alpha = 0.9f)
                    else
                        trackColor
                    Box(
                        Modifier
                            .weight(1f)
                            .height(12.dp)
                            .background(cellColor, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
        "gradient" -> {
            // Continuous color grade using threshold stops as gradient anchors.
            val stops = if (thresholds.size >= 2) thresholds.map { it.color } else listOf(valueColor, valueColor)
            Box(
                Modifier.fillMaxWidth().height(10.dp)
                    .background(trackColor, RoundedCornerShape(4.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .height(10.dp)
                        .background(Brush.horizontalGradient(stops), RoundedCornerShape(4.dp))
                )
            }
        }
        else -> {
            // "basic" (solid, threshold-tinted).
            Box(
                Modifier.fillMaxWidth().height(10.dp)
                    .background(trackColor, RoundedCornerShape(4.dp))
            ) {
                Box(
                    Modifier.fillMaxWidth(frac).height(10.dp)
                        .background(valueColor, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

private data class Threshold(val value: Double?, val color: Color)

/**
 * Extract fieldConfig.defaults.thresholds.steps from a panel. Falls back to a
 * green -> yellow -> red default (matching Grafana's out-of-the-box scheme)
 * when the panel didn't declare its own so gradient/lcd modes still look right.
 */
private fun parseThresholds(@Suppress("UNUSED_PARAMETER") panel: Panel): List<Threshold> {
    // Real thresholds live under fieldConfig.defaults.thresholds.steps - PanelParser doesn't
    // surface fieldConfig yet, so we return Grafana's default green/yellow/red ramp so gradient
    // and LCD modes still have a color story. Follow-up: expose fieldConfig from PanelParser.
    return listOf(
        Threshold(null, Color(0xFF2ECC71)),
        Threshold(0.6, Color(0xFFF1C40F)),
        Threshold(0.85, Color(0xFFE74C3C)),
    )
}

private fun thresholdColorFor(
    value: Double,
    thresholds: List<Threshold>,
    maxVal: Double,
    isPercent: Boolean,
): Color {
    if (thresholds.isEmpty()) return EnergyOrange
    val frac = if (isPercent) value / 100.0 else if (maxVal > 0) value / maxVal else 0.0
    var color = thresholds.first().color
    for (t in thresholds) {
        val threshold = t.value ?: continue
        if (frac >= threshold) color = t.color
    }
    return color
}

@Composable
private fun TablePanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: return
    val rowCount = frame.rowCount.coerceAtMost(50)
    if (rowCount == 0) { PanelNoData(); return }
    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        // Header row.
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            frame.fieldNames.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.width(140.dp).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EnergyOrange,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
        // Body rows.
        for (r in 0 until rowCount) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (c in frame.columns.indices) {
                    val cell = frame.columns[c].getOrNull(r)
                    val text = when (cell) {
                        null -> "-"
                        is Number -> if (frame.fieldTypes.getOrNull(c) == "time") {
                            SimpleDateFormat("MMM dd HH:mm:ss", Locale.getDefault()).format(Date(cell.toLong()))
                        } else "%.2f".format(cell.toDouble())
                        else -> cell.toString()
                    }
                    Text(
                        text,
                        modifier = Modifier.width(140.dp).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsPanel(data: PanelData) {
    val frame = data.frames.firstOrNull() ?: return
    val timeIdx = frame.fieldTypes.indexOfFirst { it == "time" }
    val lineIdx = frame.fieldNames.indexOfFirst { it.equals("Line", true) || it.equals("Body", true) || it.equals("Value", true) }
        .takeIf { it >= 0 } ?: frame.fieldTypes.indexOfFirst { it == "string" }
    if (lineIdx < 0) { PanelNoData(); return }
    val levelIdx = frame.fieldNames.indexOfFirst {
        it.equals("level", true) || it.equals("severity", true) || it.equals("SeverityText", true)
    }
    val rowCount = frame.rowCount.coerceAtMost(50)
    Column(Modifier.fillMaxWidth()) {
        for (r in 0 until rowCount) {
            val ts = if (timeIdx >= 0) (frame.columns[timeIdx].getOrNull(r) as? Number)?.toLong() else null
            val line = frame.columns[lineIdx].getOrNull(r)?.toString().orEmpty()
            val level = when {
                levelIdx >= 0 -> frame.columns[levelIdx].getOrNull(r)?.toString()
                else -> detectLevel(line)
            }
            val (dot, tint) = logLevelStyle(level)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tint.copy(alpha = 0.04f))
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .background(dot, RoundedCornerShape(50))
                )
                Spacer(Modifier.width(6.dp))
                if (ts != null) {
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts)),
                        modifier = Modifier.width(64.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun detectLevel(line: String): String? {
    val head = line.take(120).lowercase()
    return when {
        "error" in head || "err " in head || "fatal" in head || "panic" in head -> "error"
        "warn" in head -> "warn"
        "debug" in head -> "debug"
        "info" in head -> "info"
        else -> null
    }
}

private fun logLevelStyle(level: String?): Pair<Color, Color> = when (level?.lowercase()) {
    "error", "err", "fatal", "critical" -> Color(0xFFEF4444) to Color(0xFFEF4444)
    "warn", "warning" -> Color(0xFFF59E0B) to Color(0xFFF59E0B)
    "info" -> Color(0xFF60A5FA) to Color(0xFF60A5FA)
    "debug" -> Color(0xFF9CA3AF) to Color(0xFF9CA3AF)
    else -> Color(0xFF6B7280) to Color.Transparent
}

@Composable
private fun HeatmapPanel(data: PanelData) {
    // Grafana heatmap frames are one column per Y-bucket, with a time column and numeric bucket cols.
    val frame = data.frames.firstOrNull() ?: run { PanelNoData(); return }
    val timeIdx = frame.fieldTypes.indexOfFirst { it == "time" }
    val bucketIdxs = frame.fieldTypes.mapIndexedNotNull { i, t -> if (t == "number" && i != timeIdx) i else null }
    if (timeIdx < 0 || bucketIdxs.isEmpty() || frame.rowCount == 0) { PanelNoData(); return }
    val cells = bucketIdxs.map { bi ->
        frame.columns[bi].map { (it as? Number)?.toDouble() ?: 0.0 }
    }
    val maxVal = cells.flatten().maxOrNull()?.takeIf { it > 0 } ?: run { PanelNoData(); return }
    val cols = frame.rowCount
    val rows = bucketIdxs.size
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val cw = size.width / cols
            val ch = size.height / rows
            for (r in 0 until rows) {
                val rowVals = cells[r]
                for (c in 0 until cols) {
                    val v = rowVals.getOrNull(c) ?: 0.0
                    if (v <= 0) continue
                    val t = (v / maxVal).toFloat().coerceIn(0f, 1f)
                    drawRect(
                        color = thermalColor(t),
                        topLeft = androidx.compose.ui.geometry.Offset(c * cw, (rows - 1 - r) * ch),
                        size = androidx.compose.ui.geometry.Size(cw + 0.5f, ch + 0.5f),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HeatmapScaleBar(maxVal = maxVal)
    }
}

@Composable
private fun HeatmapScaleBar(maxVal: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.weight(1f).height(8.dp)) {
            val steps = 32
            val cw = size.width / steps
            for (i in 0 until steps) {
                val t = i / (steps - 1f)
                drawRect(
                    color = thermalColor(t),
                    topLeft = androidx.compose.ui.geometry.Offset(i * cw, 0f),
                    size = androidx.compose.ui.geometry.Size(cw + 0.5f, size.height),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            formatCompact(maxVal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

private fun thermalColor(t: Float): Color {
    val c = t.coerceIn(0f, 1f)
    return when {
        c < 0.25f -> {
            val f = c / 0.25f
            Color(red = 0.05f, green = 0.15f + 0.35f * f, blue = 0.4f + 0.5f * f, alpha = 0.5f + 0.3f * f)
        }
        c < 0.5f -> {
            val f = (c - 0.25f) / 0.25f
            Color(red = 0.05f + 0.15f * f, green = 0.5f + 0.4f * f, blue = 0.9f - 0.5f * f, alpha = 0.8f)
        }
        c < 0.75f -> {
            val f = (c - 0.5f) / 0.25f
            Color(red = 0.2f + 0.7f * f, green = 0.9f, blue = 0.4f - 0.3f * f, alpha = 0.9f)
        }
        else -> {
            val f = (c - 0.75f) / 0.25f
            Color(red = 0.9f + 0.1f * f, green = 0.9f - 0.6f * f, blue = 0.1f, alpha = 0.95f)
        }
    }
}

@Composable
private fun StateTimelinePanel(data: PanelData) {
    // Draw one row per numeric/string series over time; color-code discrete state changes.
    val frame = data.frames.firstOrNull() ?: run { PanelNoData(); return }
    val timeIdx = frame.fieldTypes.indexOfFirst { it == "time" }
    if (timeIdx < 0 || frame.rowCount == 0) { PanelNoData(); return }
    val stateIdxs = frame.fieldTypes.mapIndexedNotNull { i, t -> if (i != timeIdx && (t == "number" || t == "string" || t == "boolean")) i else null }
    if (stateIdxs.isEmpty()) { PanelNoData(); return }
    val times = frame.columns[timeIdx].mapNotNull { (it as? Number)?.toLong() }
    if (times.size < 2) { PanelNoData(); return }
    val tMin = times.first().toDouble(); val tMax = times.last().toDouble()
    val span = (tMax - tMin).takeIf { it > 0 } ?: run { PanelNoData(); return }
    val palette = listOf(EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFEF4444), Color(0xFFFACC15), Color(0xFF22D3EE))
    val rowH = 24.dp
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Column(Modifier.fillMaxWidth()) {
        stateIdxs.forEach { idx ->
            val name = frame.fieldNames.getOrNull(idx).orEmpty()
            val col = frame.columns[idx]
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Canvas(Modifier.fillMaxWidth().height(rowH)) {
                val gap = 1.5f
                var runStart = 0
                for (i in 1..times.size) {
                    val curr = col.getOrNull(i.coerceAtMost(col.size - 1))
                    val prev = col.getOrNull(runStart)
                    val boundary = i == times.size || curr != prev
                    if (boundary) {
                        val x0 = ((times[runStart].toDouble() - tMin) / span * size.width).toFloat()
                        val x1 = ((times[(i - 1).coerceIn(0, times.size - 1)].toDouble() - tMin) / span * size.width).toFloat()
                        val colorIdx = kotlin.math.abs(prev?.hashCode() ?: 0) % palette.size
                        val color = if (prev == null) Color.Transparent else palette[colorIdx]
                        val width = (x1 - x0 - gap).coerceAtLeast(1f)
                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x0, 0f),
                            size = androidx.compose.ui.geometry.Size(width, size.height),
                        )
                        val label = prev?.toString().orEmpty()
                        if (label.isNotEmpty() && width > 40f) {
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                x0 + width / 2f,
                                size.height / 2f + 9f,
                                labelPaint,
                            )
                        }
                        runStart = i
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PieChartPanel(panel: Panel, data: PanelData) {
    val pairs = remember(data) { extractBarPairs(data) }
    if (pairs.isEmpty()) { PanelNoData(); return }
    val slices = pairs.take(8)
    val total = slices.sumOf { it.second }.takeIf { it > 0.0 } ?: run { PanelNoData(); return }
    val palette = listOf(EnergyOrange, DataPurple, Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF22D3EE), Color(0xFFEF4444))
    // Grafana pie chart "pieType": "pie" (default) | "donut".
    val donut = ((panel.options?.get("pieType") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "pie") == "donut"
    val surfaceColor = MaterialTheme.colorScheme.surface
    Row(Modifier.fillMaxWidth().height(220.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(180.dp).padding(16.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                var start = -90f
                slices.forEachIndexed { idx, (_, v) ->
                    val sweep = (v / total * 360.0).toFloat()
                    drawArc(
                        color = palette[idx % palette.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true,
                    )
                    start += sweep
                }
                if (donut) {
                    val holeR = size.minDimension * 0.55f / 2f
                    drawCircle(color = surfaceColor, radius = holeR)
                }
            }
            if (donut) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatValue(total, panel.unit, panel.decimals),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth()) {
            slices.forEachIndexed { idx, (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(10.dp).background(palette[idx % palette.size], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${formatValue(v, panel.unit, panel.decimals)} · ${"%.0f%%".format(v / total * 100)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertListPanel(panel: Panel) {
    val container = LocalAppContainer.current
    val options = panel.options
    val maxItems = (options?.get("maxItems") as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toIntOrNull() ?: 10
    val stateFilter = remember(options) {
        val block = options?.get("stateFilter") as? kotlinx.serialization.json.JsonObject
        buildSet {
            if (block?.get("firing")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() } != false) add(AlertState.FIRING)
            if (block?.get("pending")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() } == true) add(AlertState.PENDING)
            if (block?.get("normal")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() } == true) add(AlertState.NORMAL)
            // Grafana's "no_data" and "error" map to FIRING semantically for the mobile list.
            if (isEmpty()) add(AlertState.FIRING)
        }
    }
    var alerts by remember { mutableStateOf<List<Alert>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(panel.id, container) {
        if (container == null) return@LaunchedEffect
        container.alertRepository.fetchAlerts()
            .onSuccess { alerts = it }
            .onFailure { error = it.message ?: "Failed to load alerts" }
    }
    when {
        container == null -> PanelError("No account context")
        error != null -> PanelError(error!!)
        alerts == null -> PanelLoading()
        else -> {
            val filtered = alerts!!.filter { it.state in stateFilter }.take(maxItems)
            if (filtered.isEmpty()) {
                Text(
                    "No alerts match the current filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
                return
            }
            Column(Modifier.fillMaxWidth()) {
                filtered.forEach { alert ->
                    AlertListRow(alert)
                }
            }
        }
    }
}

@Composable
private fun AlertListRow(alert: Alert) {
    val (dot, label) = when (alert.state) {
        AlertState.FIRING -> Color(0xFFEF4444) to "Firing"
        AlertState.PENDING -> Color(0xFFF59E0B) to "Pending"
        AlertState.SUPPRESSED -> Color(0xFF9CA3AF) to "Silenced"
        AlertState.NORMAL -> Color(0xFF10B981) to "Normal"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dot, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                alert.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (alert.summary.isNotBlank()) {
                Text(
                    alert.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = dot,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(dot.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DashListPanel(panel: Panel) {
    val container = LocalAppContainer.current
    val options = panel.options
    val maxItems = (options?.get("maxItems") as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toIntOrNull() ?: 10
    val showStarred = (options?.get("showStarred") as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toBooleanStrictOrNull() ?: true
    val query = ((options?.get("query") as? kotlinx.serialization.json.JsonPrimitive)
        ?.content).orEmpty().trim().lowercase()
    val tagFilter = remember(options) {
        (options?.get("tags") as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?.map { it.lowercase() }
            .orEmpty()
    }

    if (container == null) { PanelError("No account context"); return }
    val dashboards by container.dashboardRepository.dashboards.collectAsState(initial = emptyList())

    val filtered = remember(dashboards, showStarred, query, tagFilter, maxItems) {
        dashboards
            .asSequence()
            .filter { if (showStarred) it.isStarred else true }
            .filter { if (query.isEmpty()) true else it.title.lowercase().contains(query) }
            .filter { d ->
                if (tagFilter.isEmpty()) true
                else d.tags.map { it.lowercase() }.any { it in tagFilter }
            }
            .take(maxItems)
            .toList()
    }

    if (filtered.isEmpty()) {
        Text(
            "No dashboards match this list.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        return
    }
    Column(Modifier.fillMaxWidth()) {
        filtered.forEach { d -> DashListRow(d) }
    }
}

@Composable
private fun DashListRow(d: Dashboard) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (d.isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = null,
            tint = if (d.isStarred) EnergyOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!d.folderTitle.isNullOrBlank()) {
                Text(
                    d.folderTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TextPanel(panel: Panel) {
    val options = panel.options
    val content = options?.get("content")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
    val mode = options?.get("mode")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
    if (content.isBlank()) { PanelNoData(); return }
    if (mode.equals("html", true)) {
        val plain = remember(content) {
            content
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .trim()
        }
        Text(
            plain,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        return
    }
    val baseColor = MaterialTheme.colorScheme.onSurface
    val subtleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val accent = EnergyOrange
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        content.trim().split('\n').forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("# ") -> Text(
                    renderInline(line.removePrefix("# "), accent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = baseColor,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                line.startsWith("## ") -> Text(
                    renderInline(line.removePrefix("## "), accent),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = baseColor,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                line.startsWith("### ") -> Text(
                    renderInline(line.removePrefix("### "), accent),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = baseColor,
                )
                line.matches(Regex("^[-*]\\s+.*")) -> Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = subtleColor, modifier = Modifier.width(14.dp))
                    Text(
                        renderInline(line.replaceFirst(Regex("^[-*]\\s+"), ""), accent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = baseColor,
                    )
                }
                else -> Text(
                    renderInline(line, accent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = baseColor,
                )
            }
        }
    }
}

private fun renderInline(src: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        // bold **text**
        val bold = Regex("\\*\\*([^*]+)\\*\\*").find(src, i)
        val italic = Regex("(?<![*])\\*([^*]+)\\*(?!\\*)").find(src, i)
        val code = Regex("`([^`]+)`").find(src, i)
        val link = Regex("\\[([^]]+)]\\(([^)]+)\\)").find(src, i)
        val next = listOfNotNull(bold, italic, code, link).minByOrNull { it.range.first }
        if (next == null) {
            append(src.substring(i))
            break
        }
        if (next.range.first > i) append(src.substring(i, next.range.first))
        when (next) {
            bold -> withStyleSafe(SpanStyle(fontWeight = FontWeight.Bold)) { append(next.groupValues[1]) }
            italic -> withStyleSafe(SpanStyle(fontStyle = FontStyle.Italic)) { append(next.groupValues[1]) }
            code -> withStyleSafe(
                SpanStyle(fontFamily = FontFamily.Monospace, color = accent)
            ) { append(next.groupValues[1]) }
            link -> withStyleSafe(
                SpanStyle(color = accent, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            ) { append(next.groupValues[1]) }
        }
        i = next.range.last + 1
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleSafe(style: SpanStyle, block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit) {
    val idx = pushStyle(style)
    try { block() } finally { pop(idx) }
}

@Composable
private fun UnsupportedPanel(type: String, message: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = EnergyOrange.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            message ?: "Panel type '$type' is not natively rendered yet - open in browser to view.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PanelLoading() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EnergyOrange, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PanelError(message: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PanelNoData() {
    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
        Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun LoadingBlock(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = EnergyOrange, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun ErrorBlock(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Couldn't load dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun EmptyDashboardBlock() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("This dashboard has no panels.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

private fun formatValue(v: Double, unit: String?, decimals: Int?): String {
    val d = decimals ?: 2
    val num = "%.${d}f".format(v)
    return when (unit) {
        null, "none", "short" -> shortNumber(v, d)
        "percent" -> "$num%"
        "percentunit" -> "%.${d}f%%".format(v * 100)
        "bytes", "decbytes", "bytesIEC" -> humanBytes(v, d, base = 1024)
        "binbytes" -> humanBytes(v, d, base = 1024)
        "decbytesSI" -> humanBytes(v, d, base = 1000)
        "Bps", "binBps", "bytespersecond" -> humanBytes(v, d, base = 1024) + "/s"
        "bps", "bits" -> humanBits(v, d)
        "s", "seconds" -> humanDuration(v, d)
        "ms", "millisecond" -> humanDuration(v / 1000.0, d)
        "dtdurationms" -> humanDuration(v / 1000.0, d)
        "dtdurations" -> humanDuration(v, d)
        "µs", "us", "microsecond" -> humanDuration(v / 1_000_000.0, d)
        "ns", "nanosecond" -> humanDuration(v / 1_000_000_000.0, d)
        "hertz", "hz" -> "$num Hz"
        "celsius" -> "$num °C"
        "fahrenheit" -> "$num °F"
        "currencyUSD" -> "$$num"
        "currencyEUR" -> "€$num"
        "currencyGBP" -> "£$num"
        "currencyINR" -> "₹$num"
        else -> "$num $unit"
    }
}

private fun shortNumber(v: Double, decimals: Int): String {
    val abs = kotlin.math.abs(v)
    return when {
        abs >= 1_000_000_000_000 -> "%.${decimals}f T".format(v / 1_000_000_000_000.0)
        abs >= 1_000_000_000 -> "%.${decimals}f B".format(v / 1_000_000_000.0)
        abs >= 1_000_000 -> "%.${decimals}f M".format(v / 1_000_000.0)
        abs >= 10_000 -> "%.${decimals}f K".format(v / 1_000.0)
        else -> "%.${decimals}f".format(v)
    }
}

private fun humanBytes(v: Double, decimals: Int, base: Int): String {
    val units = if (base == 1024)
        arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    else arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = v
    var idx = 0
    while (kotlin.math.abs(value) >= base && idx < units.size - 1) { value /= base; idx++ }
    return "%.${decimals}f %s".format(value, units[idx])
}

private fun humanBits(v: Double, decimals: Int): String {
    val units = arrayOf("bit", "Kbit", "Mbit", "Gbit", "Tbit")
    var value = v
    var idx = 0
    while (kotlin.math.abs(value) >= 1000 && idx < units.size - 1) { value /= 1000; idx++ }
    return "%.${decimals}f %s".format(value, units[idx])
}

/** Format seconds as a duration Grafana-style: 32s, 5.2 min, 3.4 h, 28.6 d, 4.1 w, 1.3 y. */
private fun humanDuration(sec: Double, decimals: Int): String {
    val abs = kotlin.math.abs(sec)
    return when {
        abs < 1 -> "%.${decimals}f ms".format(sec * 1000)
        abs < 60 -> if (sec == sec.toLong().toDouble()) "${sec.toLong()}s" else "%.${decimals}f s".format(sec)
        abs < 3600 -> "%.1f min".format(sec / 60.0)
        abs < 86400 -> "%.1f h".format(sec / 3600.0)
        abs < 604800 -> "%.1f d".format(sec / 86400.0)
        abs < 31_536_000 -> "%.1f w".format(sec / 604800.0)
        else -> "%.1f y".format(sec / 31_536_000.0)
    }
}
