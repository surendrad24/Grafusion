package com.fusionlancers.grafusion.ui.dashboards

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.model.Dashboard
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

private sealed class DashFilter {
    object All : DashFilter()
    object Starred : DashFilter()
    data class Folder(val name: String) : DashFilter()
    data class Tag(val name: String) : DashFilter()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardListScreen(
    container: AppContainer,
    onOpenDashboard: (uid: String, title: String) -> Unit,
) {
    val dashboards by container.dashboardRepository.dashboards.collectAsState(initial = emptyList())
    var refreshing by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<DashFilter>(DashFilter.All) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        refreshing = true
        container.dashboardRepository.refresh()
        refreshing = false
    }

    val folders = remember(dashboards) {
        dashboards.mapNotNull { it.folderTitle?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()
    }
    val tags = remember(dashboards) {
        dashboards.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filtered = remember(dashboards, query, filter) {
        val q = query.trim().lowercase()
        dashboards.asSequence()
            .filter { d ->
                when (val f = filter) {
                    DashFilter.All -> true
                    DashFilter.Starred -> d.isStarred
                    is DashFilter.Folder -> d.folderTitle == f.name
                    is DashFilter.Tag -> f.name in d.tags
                }
            }
            .filter { d ->
                if (q.isBlank()) true
                else d.title.lowercase().contains(q) ||
                    d.folderTitle.orEmpty().lowercase().contains(q) ||
                    d.tags.any { it.lowercase().contains(q) }
            }
            .toList()
    }

    val grouped = remember(filtered) {
        filtered.groupBy { it.folderTitle ?: "General" }
            .toSortedMap()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Dashboards",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = EnergyOrange,
                )
            } else {
                Text(
                    "${filtered.size} of ${dashboards.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            placeholder = { Text("Search title, folder, tag…") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = EnergyOrange,
                cursorColor = EnergyOrange,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipItem("All", filter is DashFilter.All) { filter = DashFilter.All }
            FilterChipItem("Starred", filter is DashFilter.Starred) { filter = DashFilter.Starred }
            folders.forEach { name ->
                FilterChipItem(name, (filter as? DashFilter.Folder)?.name == name) {
                    filter = DashFilter.Folder(name)
                }
            }
            tags.forEach { name ->
                FilterChipItem("#$name", (filter as? DashFilter.Tag)?.name == name) {
                    filter = DashFilter.Tag(name)
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    container.dashboardRepository.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (dashboards.isEmpty() && !refreshing) {
                EmptyState(
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            container.dashboardRepository.refresh()
                            refreshing = false
                        }
                    },
                )
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No matches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    grouped.forEach { (folder, items) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "hdr-$folder") {
                            FolderHeader(folder, items.size)
                        }
                        items(items, key = { it.uid }) { d ->
                            DashboardCard(
                                dashboard = d,
                                onClick = { onOpenDashboard(d.uid, d.title) },
                                onToggleStar = {
                                    val id = d.dashboardId ?: return@DashboardCard
                                    scope.launch {
                                        container.dashboardRepository.toggleStar(d.uid, id, !d.isStarred)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EnergyOrange.copy(alpha = 0.2f),
            selectedLabelColor = EnergyOrange,
        ),
    )
}

@Composable
private fun FolderHeader(name: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun DashboardCard(dashboard: Dashboard, onClick: () -> Unit, onToggleStar: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = EnergyOrange.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Dashboard,
                            contentDescription = null,
                            tint = EnergyOrange,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                if (dashboard.cachedOffline) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = "Cached offline",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = onToggleStar, enabled = dashboard.dashboardId != null) {
                    Icon(
                        if (dashboard.isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (dashboard.isStarred) "Unstar" else "Star",
                        tint = if (dashboard.isStarred) EnergyOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                dashboard.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            dashboard.folderTitle?.let { folder ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        folder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
            if (dashboard.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dashboard.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EnergyOrange.copy(alpha = 0.12f),
                        ) {
                            Text(
                                tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                color = EnergyOrange,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(84.dp),
                shape = CircleShape,
                color = EnergyOrange.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Dashboard,
                        contentDescription = null,
                        tint = EnergyOrange,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "No dashboards yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Once you create dashboards in Grafana they'll appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.TextButton(onClick = onRefresh) {
                Text("Refresh", color = EnergyOrange, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
