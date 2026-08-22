package com.fusionlancers.grafusion.ui.datasources

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.DatasourceDetail
import com.fusionlancers.grafusion.data.api.DatasourceHealth
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Metadata + on-demand health probe for a single datasource. Complements the list screen by
 * giving ops a place to answer "is this thing set up the way I think it is?" without opening
 * the Grafana web UI on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasourceDetailScreen(
    container: AppContainer,
    uid: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<DatasourceDetail?>(null) }
    var health by remember { mutableStateOf<DatasourceHealth?>(null) }
    var loading by remember { mutableStateOf(true) }
    var probing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        loading = true
        container.datasourceRepository.detail(uid)
            .onSuccess { detail = it; error = null }
            .onFailure { error = it.message ?: "failed to load datasource" }
        loading = false
        probing = true
        container.datasourceRepository.probe(uid)
            .onSuccess { health = it }
            .onFailure { health = DatasourceHealth("ERROR", it.message.orEmpty()) }
        probing = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        detail?.name.orEmpty().ifBlank { "Datasource" },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && detail == null -> LoadingState()
                error != null && detail == null -> ErrorState(error!!)
                detail != null -> DetailBody(
                    detail = detail!!,
                    health = health,
                    probing = probing,
                    onProbe = {
                        scope.launch {
                            probing = true
                            container.datasourceRepository.probe(uid)
                                .onSuccess { health = it }
                                .onFailure { health = DatasourceHealth("ERROR", it.message.orEmpty()) }
                            probing = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    detail: DatasourceDetail,
    health: DatasourceHealth?,
    probing: Boolean,
    onProbe: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header card - name + type + default badge + circle icon.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = EnergyOrange.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Storage, null, tint = EnergyOrange, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        detail.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        detail.typeName.ifBlank { detail.type },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                if (detail.isDefault) {
                    Text(
                        "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EnergyOrange,
                    )
                }
            }
        }

        // Health card - probe again button + latest status/message.
        HealthCard(health = health, probing = probing, onProbe = onProbe)

        // Connection details.
        SectionCard("Connection") {
            detail.url.takeIf { it.isNotBlank() }?.let { KeyValue("URL", it) }
            detail.access.takeIf { it.isNotBlank() }?.let { KeyValue("Access", it) }
            detail.database.takeIf { it.isNotBlank() }?.let { KeyValue("Database", it) }
            detail.user.takeIf { it.isNotBlank() }?.let { KeyValue("User", it) }
            if (detail.basicAuth) {
                KeyValue("Basic auth", detail.basicAuthUser.ifBlank { "enabled" })
            }
            if (detail.readOnly) KeyValue("Provisioning", "read-only (defined via YAML)")
        }

        // Secure fields - just names, obviously.
        if (detail.secureJsonFields.isNotEmpty()) {
            SectionCard("Secrets") {
                detail.secureJsonFields.forEach { (k, isSet) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Key, null, tint = EnergyOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(k, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (isSet) "set" else "not set",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSet) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        // jsonData - pretty-printed. Datasource-specific settings vary widely; showing the raw
        // JSON is far more useful than trying to model every plugin's config.
        detail.jsonData?.let { js ->
            if (js.isNotEmpty()) {
                SectionCard("Plugin settings") {
                    CodeBlock(prettyPrint(js))
                }
            }
        }

        SectionCard("Identifiers") {
            KeyValue("UID", detail.uid)
            KeyValue("Numeric ID", detail.id.toString())
            KeyValue("Type", detail.type)
        }
    }
}

@Composable
private fun HealthCard(
    health: DatasourceHealth?,
    probing: Boolean,
    onProbe: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val status = health?.status?.uppercase().orEmpty()
                val (icon, tint) = when (status) {
                    "OK" -> Icons.Filled.CheckCircle to Color(0xFF22C55E)
                    "ERROR" -> Icons.Filled.ErrorOutline to Color(0xFFEF4444)
                    else -> Icons.Filled.HelpOutline to Color(0xFF94A3B8)
                }
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Health", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(
                        health?.status ?: if (probing) "probing..." else "unknown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = onProbe,
                    enabled = !probing,
                    colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (probing) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (probing) "Testing" else "Test")
                }
            }
            health?.message?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = EnergyOrange,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
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

private val prettyJson = Json { prettyPrint = true }

// Kotlinx Json's prettyPrint on the JsonObject the API returned. Skips JsonNull entries so the
// output doesn't get cluttered with `"foo": null` lines.
private fun prettyPrint(obj: JsonObject): String {
    val filtered = JsonObject(obj.filterValues { it !is JsonNull && !(it is JsonPrimitive && it.content == "null") })
    return prettyJson.encodeToString(JsonObject.serializer(), filtered)
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
        Text("Datasource unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
