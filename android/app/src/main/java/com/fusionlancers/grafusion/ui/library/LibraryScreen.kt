package com.fusionlancers.grafusion.ui.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.LibraryElement
import com.fusionlancers.grafusion.data.api.PlaylistSummary
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

private enum class LibraryTab { Panels, Playlists }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(LibraryTab.Panels) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var panels by remember { mutableStateOf<List<LibraryElement>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistSummary>>(emptyList()) }
    val scope = rememberCoroutineScope()

    suspend fun load(t: LibraryTab) {
        loading = true
        error = null
        when (t) {
            LibraryTab.Panels -> container.libraryRepository.libraryPanels()
                .onSuccess { panels = it }.onFailure { error = it.message }
            LibraryTab.Playlists -> container.libraryRepository.playlists()
                .onSuccess { playlists = it }.onFailure { error = it.message }
        }
        loading = false
    }

    LaunchedEffect(tab) { load(tab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { scope.launch { load(tab) } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                Tab(
                    selected = tab == LibraryTab.Panels,
                    onClick = { tab = LibraryTab.Panels },
                    text = { Text("Panels", fontWeight = if (tab == LibraryTab.Panels) FontWeight.SemiBold else FontWeight.Normal) },
                    icon = { Icon(Icons.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Tab(
                    selected = tab == LibraryTab.Playlists,
                    onClick = { tab = LibraryTab.Playlists },
                    text = { Text("Playlists", fontWeight = if (tab == LibraryTab.Playlists) FontWeight.SemiBold else FontWeight.Normal) },
                    icon = { Icon(Icons.Filled.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EnergyOrange)
                    }
                    error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (tab) {
                            LibraryTab.Panels -> {
                                if (panels.isEmpty()) item { EmptyHint("No library panels") }
                                items(panels, key = { it.uid.ifBlank { it.id.toString() } }) { PanelRow(it) }
                            }
                            LibraryTab.Playlists -> {
                                if (playlists.isEmpty()) item { EmptyHint("No playlists") }
                                items(playlists, key = { it.uid.ifBlank { it.id.toString() } }) { PlaylistRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(msg: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun PanelRow(el: LibraryElement) {
    LibCard(
        title = el.name,
        subtitle = el.type.ifBlank { "panel" } +
            (el.meta.folderName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
        detail = "${el.meta.connectedDashboards} dashboards",
        icon = Icons.Filled.Dashboard,
    )
}

@Composable
private fun PlaylistRow(p: PlaylistSummary) {
    LibCard(
        title = p.name,
        subtitle = "every ${p.interval.ifBlank { "?" }}",
        detail = "",
        icon = Icons.Filled.PlayCircleOutline,
    )
}

@Composable
private fun LibCard(
    title: String,
    subtitle: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = EnergyOrange.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
    }
}
