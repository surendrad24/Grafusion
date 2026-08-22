package com.fusionlancers.grafusion.ui.datasources

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.repo.DatasourceRepository
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasourcesScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDetail: (uid: String) -> Unit = {},
) {
    var loading by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<DatasourceRepository.HealthResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        val r = container.datasourceRepository.listWithHealth()
        r.onSuccess { results = it; error = null }
            .onFailure { error = it.message ?: "failed to load datasources" }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Datasources", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EnergyOrange)
                    }
                }
                error != null && results.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            val ok = results.count { it.health.status.equals("OK", true) }
                            val err = results.count { it.health.status.equals("ERROR", true) }
                            Text(
                                "$ok healthy · $err errors · ${results.size} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            )
                        }
                        items(results, key = { it.datasource.uid }) { row ->
                            DatasourceRow(row, onClick = { onOpenDetail(row.datasource.uid) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatasourceRow(row: DatasourceRepository.HealthResult, onClick: () -> Unit) {
    val ds = row.datasource
    val status = row.health.status.uppercase()
    val (icon, tint) = when (status) {
        "OK" -> Icons.Filled.CheckCircle to Color(0xFF22C55E)
        "ERROR" -> Icons.Filled.ErrorOutline to Color(0xFFEF4444)
        else -> Icons.Filled.HelpOutline to Color(0xFF94A3B8)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = EnergyOrange.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ds.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (ds.isDefault) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "DEFAULT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EnergyOrange,
                            )
                        }
                    }
                    Text(
                        ds.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Icon(icon, contentDescription = status, tint = tint, modifier = Modifier.size(22.dp))
            }
            if (row.health.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    row.health.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
            if (ds.url.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    ds.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}
