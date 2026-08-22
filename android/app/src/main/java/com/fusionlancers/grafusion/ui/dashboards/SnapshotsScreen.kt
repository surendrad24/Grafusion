package com.fusionlancers.grafusion.ui.dashboards

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
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
 * Closes the snapshot loop: creation already exists in the dashboard detail overflow, this
 * screen surfaces the list of snapshots the current user owns so they can re-share a URL
 * (via the Android share sheet), open one in the browser, or revoke a stale one. Delete goes
 * through a confirm dialog because snapshot URLs are unauthenticated - anyone with the link
 * loses access the moment the row is deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var rows by remember { mutableStateOf<List<DashboardRepository.SnapshotRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<DashboardRepository.SnapshotRow?>(null) }
    var deleting by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        container.dashboardRepository.listSnapshots()
            .onSuccess { rows = it }
            .onFailure { error = it.message ?: "Failed to load snapshots" }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snapshots", fontWeight = FontWeight.SemiBold) },
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
                loading -> CenterLoad("Loading snapshots…")
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
                    items(rows, key = { it.summary.id }) { row ->
                        SnapshotRow(
                            row = row,
                            onShare = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, row.summary.name.ifBlank { "Grafana snapshot" })
                                    putExtra(Intent.EXTRA_TEXT, row.shareUrl)
                                }
                                context.startActivity(Intent.createChooser(send, "Share snapshot"))
                            },
                            onOpen = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(row.shareUrl)))
                                }.onFailure {
                                    scope.launch { snackbar.showSnackbar("Can't open URL") }
                                }
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
                title = { Text("Delete snapshot?") },
                text = {
                    Column {
                        Text(
                            "Anyone with the link will get a 404 immediately.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            row.summary.name.ifBlank { row.summary.key },
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
                                container.dashboardRepository.deleteSnapshot(row.summary.key)
                                    .onSuccess {
                                        snackbar.showSnackbar("Snapshot deleted")
                                        confirmDelete = null
                                        reload()
                                    }
                                    .onFailure {
                                        snackbar.showSnackbar("Delete failed: ${it.message}")
                                    }
                                deleting = false
                            }
                        },
                    ) {
                        if (deleting) CircularProgressIndicator(
                            color = EnergyOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        ) else Text("Delete", color = MaterialTheme.colorScheme.error)
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
private fun SnapshotRow(
    row: DashboardRepository.SnapshotRow,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = row.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (s.external) Icons.Filled.Public else Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = EnergyOrange,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                s.name.ifBlank { "(unnamed)" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (s.external) {
                Text(
                    "external",
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
            metaLine(s.created, s.expires),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onOpen) {
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
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CenterLoad(msg: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = EnergyOrange)
        Spacer(Modifier.height(8.dp))
        Text(msg, style = MaterialTheme.typography.bodySmall)
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
            Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No snapshots yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Open a dashboard and use the overflow menu to share one.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

private fun metaLine(created: String?, expires: Long): String {
    val createdBit = created?.let { "created " + formatIso(it) } ?: "created ?"
    // Grafana returns expires as epoch seconds; 0 means never.
    val expiresBit = if (expires <= 0) "never expires"
    else "expires " + formatEpochSeconds(expires)
    return "$createdBit  •  $expiresBit"
}

private fun formatIso(iso: String): String = runCatching {
    val instant = java.time.OffsetDateTime.parse(iso).toInstant()
    val local = instant.atZone(java.time.ZoneId.systemDefault())
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(local)
}.getOrDefault(iso)

private fun formatEpochSeconds(seconds: Long): String = runCatching {
    val instant = java.time.Instant.ofEpochSecond(seconds)
    val local = instant.atZone(java.time.ZoneId.systemDefault())
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(local)
}.getOrDefault(seconds.toString())
