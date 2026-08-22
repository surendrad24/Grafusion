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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallSplit
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
import com.fusionlancers.grafusion.data.api.AmRoute
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

/**
 * Read-only view of the Alertmanager routing tree so users can see which contact point a given
 * alert will land at. We render as a flat list with indent-per-depth (rather than an actual
 * TreeView) because deep routes are rare and the flat list scrolls better on phones.
 *
 * Deliberately read-only - hand-editing the routing tree at 3am is how outages get worse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPoliciesScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nodes by remember { mutableStateOf<List<PolicyNode>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            refreshing = true
            error = null
            container.alertRepository.alertmanagerConfig()
                .onSuccess { cfg ->
                    nodes = cfg.route?.let { flatten(it, depth = 0, isDefault = true) } ?: emptyList()
                }
                .onFailure { error = it.message ?: "Failed to load routing tree" }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notification policies", fontWeight = FontWeight.SemiBold) },
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
                refreshing && nodes.isEmpty() -> LoadingState()
                nodes.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(nodes, key = { it.stableKey }) { n -> PolicyCard(n) }
                }
            }
        }
    }
}

@Composable
private fun PolicyCard(node: PolicyNode) {
    // Indent by depth using a leading spacer rather than nested Layouts - keeps the LazyColumn
    // efficient and lets each card size itself naturally.
    val indentDp = (node.depth * 14).dp
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(indentDp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(26.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = EnergyOrange.copy(alpha = 0.15f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (node.isDefault) Icons.Filled.AccountTree else Icons.Filled.CallSplit,
                                null,
                                tint = EnergyOrange,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (node.isDefault) "Default policy" else "Nested policy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                        Text(
                            node.receiver.ifBlank { "(inherits)" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (node.cont) {
                        Text(
                            "continue",
                            style = MaterialTheme.typography.labelSmall,
                            color = EnergyOrange,
                        )
                    }
                }
                if (node.matchers.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    node.matchers.forEach { m ->
                        Text(
                            m,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        )
                    }
                }
                val timing = buildList {
                    node.groupBy.takeIf { it.isNotEmpty() }?.let { add("group by ${it.joinToString(", ")}") }
                    node.groupWait?.let { add("wait $it") }
                    node.groupInterval?.let { add("interval $it") }
                    node.repeatInterval?.let { add("repeat $it") }
                    node.muteTimeIntervals.takeIf { it.isNotEmpty() }?.let { add("mute: ${it.joinToString(", ")}") }
                }
                if (timing.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        timing.joinToString(" - "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

private data class PolicyNode(
    val depth: Int,
    val isDefault: Boolean,
    val receiver: String,
    val matchers: List<String>,
    val groupBy: List<String>,
    val groupWait: String?,
    val groupInterval: String?,
    val repeatInterval: String?,
    val muteTimeIntervals: List<String>,
    val cont: Boolean,
    val stableKey: String,
)

/**
 * Depth-first flatten of the AmRoute tree. Each node gets a stable key built from its path so
 * two nodes with identical matchers under different parents don't collide in LazyColumn.
 */
private fun flatten(
    route: AmRoute,
    depth: Int,
    isDefault: Boolean,
    keyPrefix: String = "root",
): List<PolicyNode> {
    val matchers = buildList<String> {
        addAll(route.matchers)
        route.objectMatchers.forEach { triple ->
            if (triple.size >= 3) add("${triple[0]} ${triple[1]} \"${triple[2]}\"")
        }
    }
    val node = PolicyNode(
        depth = depth,
        isDefault = isDefault,
        receiver = route.receiver.orEmpty(),
        matchers = matchers,
        groupBy = route.groupBy,
        groupWait = route.groupWait,
        groupInterval = route.groupInterval,
        repeatInterval = route.repeatInterval,
        muteTimeIntervals = route.muteTimeIntervals,
        cont = route.cont,
        stableKey = "$keyPrefix|d$depth|${route.receiver}|${matchers.joinToString(";")}",
    )
    return buildList {
        add(node)
        route.routes.forEachIndexed { i, child ->
            addAll(flatten(child, depth + 1, isDefault = false, keyPrefix = "$keyPrefix/$i"))
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.AccountTree, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("No routing tree", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Grafana Alerting either isn't configured or your user can't read the alertmanager config.",
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
        Text("Routing tree unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
