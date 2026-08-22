package com.fusionlancers.grafusion.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.repo.AlertRuleRow
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

/**
 * Read-only browser for Grafana Managed alert rules. Lets on-call folks jump from an alert card
 * to the exact rule that fired it - useful for "why is this alerting?" moments without needing
 * to alt-tab into Grafana's web UI. Editing lives in the web UI on purpose; misconfiguring an
 * alert rule from a phone at 3am is not a feature we want to encourage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertRulesScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<AlertRuleRow>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<AlertRuleRow?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun reload() {
        scope.launch {
            refreshing = true
            error = null
            container.alertRepository.listAlertRules()
                .onSuccess { rules = it }
                .onFailure { error = it.message ?: "Failed to load rules" }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    val filtered = remember(rules, query) {
        if (query.isBlank()) rules
        else rules.filter {
            it.displayName().contains(query, ignoreCase = true) ||
                it.folder.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.folder } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Alert rules", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search rules, folders, groups") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = ::reload,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    error != null -> ErrorState(error!!)
                    refreshing && rules.isEmpty() -> LoadingState()
                    filtered.isEmpty() -> EmptyState(query)
                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        grouped.forEach { (folder, rows) ->
                            item(key = "hdr-$folder") { FolderHeader(folder, rows.size) }
                            items(rows, key = { "${it.folder}/${it.group}/${it.displayName()}/${it.uid()}" }) { row ->
                                RuleCard(row = row, onClick = { selected = row })
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
        ) {
            RuleDetailSheet(row)
        }
    }
}

@Composable
private fun FolderHeader(name: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name.ifBlank { "Unfoldered" },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun RuleCard(row: AlertRuleRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = if (row.isRecording()) Icons.Filled.Bolt else Icons.Filled.Rule
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(10.dp),
                color = EnergyOrange.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = EnergyOrange, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    append(row.group)
                    row.rule.`for`?.let { append(" - for ").append(it) }
                    row.rule.labels["severity"]?.let { append(" - ").append(it) }
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RuleDetailSheet(row: AlertRuleRow) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(row.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "${row.folder} > ${row.group}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))

        row.rule.`for`?.let {
            MetaRow("For", it)
        }
        row.rule.grafanaAlert?.condition?.let {
            MetaRow("Condition ref", it)
        }
        row.rule.grafanaAlert?.noDataState?.let {
            MetaRow("No data", it)
        }
        row.rule.grafanaAlert?.execErrState?.let {
            MetaRow("Exec error", it)
        }
        row.rule.expr?.let {
            SectionLabel("Expression")
            CodeBlock(it)
        }

        if (row.rule.labels.isNotEmpty()) {
            SectionLabel("Labels")
            row.rule.labels.forEach { (k, v) -> MetaRow(k, v) }
        }
        if (row.rule.annotations.isNotEmpty()) {
            SectionLabel("Annotations")
            row.rule.annotations.forEach { (k, v) -> MetaRow(k, v) }
        }

        // The query model can be huge and datasource-specific; pretty-print it as JSON so power
        // users can read the raw structure without us having to model every datasource variant.
        row.rule.grafanaAlert?.data?.let { arr ->
            SectionLabel("Query model")
            CodeBlock(arr.toString())
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = EnergyOrange,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(0.35f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyState(query: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Rule, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (query.isBlank()) "No alert rules" else "No matches",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (query.isBlank())
                "Grafana Alerting either isn't configured on this instance or your user can't read the rules."
            else "No rules match '$query'.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EnergyOrange)
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Rules unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

