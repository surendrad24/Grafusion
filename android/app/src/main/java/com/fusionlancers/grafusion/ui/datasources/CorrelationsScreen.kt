package com.fusionlancers.grafusion.ui.datasources

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.repo.DatasourceRepository.CorrelationRow
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Read-only view of Grafana 10+ datasource correlations. Rows are grouped by source datasource
 * so users can scan "everything Prom Prod links to" at a glance. Query-type correlations render
 * the source field + the target expression (peeked out of the JsonObject regardless of the
 * target plugin's shape). Missing source/target UIDs are surfaced as "unknown" so broken
 * correlations still show up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrelationsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<CorrelationRow>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            refreshing = true
            error = null
            container.datasourceRepository.listCorrelations()
                .onSuccess { rows = it }
                .onFailure { error = it.message ?: "Failed to load correlations" }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Correlations", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                error != null -> ErrorState(error!!)
                refreshing && rows.isEmpty() -> LoadingState()
                rows.isEmpty() -> EmptyState()
                else -> {
                    val grouped = rows.groupBy { it.sourceName }
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        grouped.forEach { (sourceName, sourceRows) ->
                            item(key = "hdr_$sourceName") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        sourceName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EnergyOrange,
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    sourceRows.firstOrNull()?.sourceType?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            "($it)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }
                            items(sourceRows, key = { it.correlation.uid }) { row -> CorrelationCard(row) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CorrelationCard(row: CorrelationRow) {
    val c = row.correlation
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.label.ifBlank { "(no label)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (c.provisioned) TypeBadge("provisioned", EnergyOrange)
                Spacer(Modifier.size(4.dp))
                TypeBadge(c.type.ifBlank { "query" }, MaterialTheme.colorScheme.primary)
            }
            if (c.description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    c.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
                Spacer(Modifier.size(6.dp))
                Icon(
                    Icons.Filled.ArrowForward,
                    null,
                    tint = EnergyOrange,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    row.targetName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }
            c.config?.let { cfg ->
                if (cfg.field.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "source field: ${cfg.field}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                val expr = peekTargetExpression(cfg.target)
                if (expr != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        expr,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                    )
                }
                cfg.transformations?.takeIf { it.isNotEmpty() }?.let { tx ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${tx.size} transformation${if (tx.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Pull the most likely target-expression field regardless of datasource plugin. Prom uses
 * `expr`, Loki uses `expr`, SQL uses `rawSql`, generic uses `queryText`, external uses `url`.
 */
private fun peekTargetExpression(target: kotlinx.serialization.json.JsonObject?): String? {
    target ?: return null
    val keys = listOf("expr", "query", "rawSql", "queryText", "url")
    for (k in keys) {
        val v = target[k] as? JsonPrimitive ?: continue
        val s = runCatching { v.content }.getOrNull()
        if (!s.isNullOrBlank()) return s
    }
    return null
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Hub, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("No correlations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "This Grafana has no correlations defined (or is < v10 where the feature doesn't exist).",
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
        Text("Correlations unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
