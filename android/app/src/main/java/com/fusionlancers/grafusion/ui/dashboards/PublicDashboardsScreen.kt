package com.fusionlancers.grafusion.ui.dashboards

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.repo.DashboardRepository
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

/**
 * Cross-instance browser for public dashboards (Grafana 10+). The public-dashboard access
 * token turns a Grafana dashboard into an unauthenticated web view; when things drift from
 * "temporary demo" to "still up months later" you need a place to see the whole list and
 * revoke the ones that shouldn't still be exposed. That's this screen.
 *
 * Creation is intentionally *not* here - it's a per-dashboard config living on the dashboard
 * detail screen once we surface it there. This is the audit / revoke surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicDashboardsScreen(
    container: AppContainer,
    onOpenDashboard: (uid: String, title: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var rows by remember { mutableStateOf<List<DashboardRepository.PublicDashboardRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<DashboardRepository.PublicDashboardRow?>(null) }
    var deleting by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        container.dashboardRepository.listPublicDashboards()
            .onSuccess { rows = it }
            .onFailure { error = it.message ?: "Failed to load" }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Public dashboards", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when {
                loading -> CenterLoad()
                error != null -> Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 24.dp),
                )
                rows.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = { it.summary.uid }) { row ->
                        PublicDashboardRow(
                            row = row,
                            onOpenInApp = { onOpenDashboard(row.summary.dashboardUid, row.summary.title) },
                            onOpenBrowser = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(row.shareUrl)))
                                }.onFailure {
                                    scope.launch { snackbar.showSnackbar("Can't open URL") }
                                }
                            },
                            onShare = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, row.summary.title.ifBlank { "Grafana dashboard" })
                                    putExtra(Intent.EXTRA_TEXT, row.shareUrl)
                                }
                                context.startActivity(Intent.createChooser(send, "Share public link"))
                            },
                            onDelete = { confirmDelete = row },
                        )
                    }
                }
            }
        }

        confirmDelete?.let { row ->
            AlertDialog(
                onDismissRequest = { if (!deleting) confirmDelete = null },
                title = { Text("Revoke public link?") },
                text = {
                    Column {
                        Text(
                            "The public URL will return 404 immediately. The dashboard itself is not deleted.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            row.summary.title.ifBlank { row.summary.dashboardUid },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            deleting = true
                            scope.launch {
                                container.dashboardRepository
                                    .deletePublicDashboard(row.summary.dashboardUid, row.summary.uid)
                                    .onSuccess {
                                        snackbar.showSnackbar("Public link revoked")
                                        confirmDelete = null
                                        reload()
                                    }
                                    .onFailure {
                                        snackbar.showSnackbar("Revoke failed: ${it.message}")
                                    }
                                deleting = false
                            }
                        },
                    ) {
                        if (deleting) CircularProgressIndicator(
                            color = EnergyOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        ) else Text("Revoke", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !deleting, onClick = { confirmDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun PublicDashboardRow(
    row: DashboardRepository.PublicDashboardRow,
    onOpenInApp: () -> Unit,
    onOpenBrowser: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = row.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable { onOpenInApp() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (s.isEnabled) Icons.Filled.Public else Icons.Filled.PublicOff,
                contentDescription = null,
                tint = if (s.isEnabled) EnergyOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                s.title.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (!s.isEnabled) {
                Text(
                    "paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else if (s.share == "email") {
                Text(
                    "email-gated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            row.shareUrl,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            configLine(s.timeSelectionEnabled, s.annotationsEnabled),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onOpenBrowser) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Open")
            }
            TextButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Share")
            }
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text("Revoke", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CenterLoad() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = EnergyOrange)
        Spacer(Modifier.height(8.dp))
        Text("Loading public dashboards…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.PublicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No public dashboards on this instance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Requires Grafana 10+ with the public-dashboards feature enabled.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

private fun configLine(timeSel: Boolean, annotations: Boolean): String {
    val bits = buildList {
        add(if (timeSel) "time picker on" else "time picker off")
        add(if (annotations) "annotations shown" else "annotations hidden")
    }
    return bits.joinToString("  •  ")
}
