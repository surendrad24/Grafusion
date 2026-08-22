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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.AmSilence
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Dedicated silences manager. Complements the inline silence rows shown per-alert in
 * [AlertsScreen] by giving the on-call user a place to see everything at once and hand-author
 * silences (e.g. before a deploy) without needing an alert to already be firing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilencesScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var silences by remember { mutableStateOf<List<AmSilence>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    fun reload() {
        scope.launch {
            refreshing = true
            error = null
            container.alertRepository.listSilences()
                .onSuccess { silences = it }
                .onFailure { error = it.message ?: "Failed to load silences" }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Silences", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = EnergyOrange,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New silence")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                error != null -> ErrorState(error!!)
                refreshing && silences.isEmpty() -> LoadingState()
                silences.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(silences, key = { it.id }) { s ->
                        SilenceCard(
                            silence = s,
                            onExpire = {
                                scope.launch {
                                    container.alertRepository.expireSilence(s.id)
                                        .onSuccess {
                                            snackbar.showSnackbar("Silence lifted")
                                            reload()
                                        }
                                        .onFailure { snackbar.showSnackbar("Failed: ${it.message}") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateSilenceDialog(
            onDismiss = { showCreate = false },
            onCreate = { matchers, minutes, comment ->
                scope.launch {
                    val who = container.accountRepository.activeEntity()?.login ?: "grafusion"
                    container.alertRepository.silenceByMatchers(matchers, minutes, comment, who)
                        .onSuccess {
                            snackbar.showSnackbar("Silence created")
                            showCreate = false
                            reload()
                        }
                        .onFailure { snackbar.showSnackbar("Failed: ${it.message}") }
                }
            },
        )
    }
}

@Composable
private fun SilenceCard(silence: AmSilence, onExpire: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = EnergyOrange.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.NotificationsPaused,
                            null,
                            tint = EnergyOrange,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        silence.comment.orEmpty().ifBlank { "Silenced" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val meta = listOfNotNull(
                        silence.createdBy?.takeIf { it.isNotBlank() },
                        remainingLabel(silence.endsAt),
                    ).joinToString(" - ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                IconButton(onClick = onExpire) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Lift silence",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            if (silence.matchers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    silence.matchers.forEach { m ->
                        Text(
                            "${m.name} ${if (m.isRegex) "=~" else "="} ${m.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSilenceDialog(
    onDismiss: () -> Unit,
    onCreate: (matchers: List<Triple<String, String, Boolean>>, minutes: Long, comment: String) -> Unit,
) {
    // Matcher rows kept as a mutable list of triples for simplicity; the parent maps to the
    // repository shape. We start with a single blank row so the intent is obvious.
    var rows by remember { mutableStateOf(listOf(MatcherRow("alertname", "", false))) }
    var comment by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(240L) }

    val valid = rows.any { it.name.isNotBlank() && it.value.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New silence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { idx, row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { v -> rows = rows.toMutableList().also { it[idx] = row.copy(name = v) } },
                            label = { Text("label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(6.dp))
                        OutlinedTextField(
                            value = row.value,
                            onValueChange = { v -> rows = rows.toMutableList().also { it[idx] = row.copy(value = v) } },
                            label = { Text(if (row.isRegex) "~" else "=") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f),
                        )
                        if (rows.size > 1) {
                            IconButton(onClick = { rows = rows.filterIndexed { i, _ -> i != idx } }) {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        rows = rows + MatcherRow("", "", false)
                    }) { Text("+ matcher") }
                }
                Spacer(Modifier.height(4.dp))
                Text("Duration", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60L to "1h", 240L to "4h", 720L to "12h", 1440L to "24h").forEach { (m, l) ->
                        DurationPill(l, minutes == m) { minutes = m }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val trimmed = rows
                        .filter { it.name.isNotBlank() && it.value.isNotBlank() }
                        .map { Triple(it.name, it.value, it.isRegex) }
                    onCreate(trimmed, minutes, comment.ifBlank { "Silenced from Grafusion" })
                },
                colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
            ) { Text("Silence") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class MatcherRow(val name: String, val value: String, val isRegex: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPill(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.NotificationsPaused, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("No active silences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + to silence a set of alerts, e.g. before a planned deploy.",
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
        Text("Silences unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

private val ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME
private val ISO_ALT = DateTimeFormatter.ISO_INSTANT

private fun remainingLabel(endsAt: String?): String? {
    val e = endsAt ?: return null
    val end = runCatching { Instant.from(ISO.parse(e)) }.getOrNull()
        ?: runCatching { Instant.from(ISO_ALT.parse(e)) }.getOrNull()
        ?: return null
    val d = Duration.between(Instant.now(), end)
    if (d.isNegative) return "expired"
    return when {
        d.toMinutes() < 60 -> "${d.toMinutes()}m left"
        d.toHours() < 48 -> "${d.toHours()}h left"
        else -> "${d.toDays()}d left"
    }
}

@Suppress("unused")
private val zoneForFuture = ZoneId.systemDefault()
