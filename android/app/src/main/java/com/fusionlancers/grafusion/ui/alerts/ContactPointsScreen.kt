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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Webhook
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.AmGrafanaReceiverConfig
import com.fusionlancers.grafusion.data.api.AmReceiver
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Read-only browser for Grafana contact points. Answers the "will I actually get paged if this
 * rule fires?" question that always follows viewing an alert rule - so it lives one tap away
 * from the rules and silences icons in the Alerts header.
 *
 * We show one card per receiver with all its channel configs listed (Grafana lets a receiver
 * fan out to email + slack + pagerduty simultaneously). Destination text is best-effort - we
 * pull the "public" fields per type; secrets/tokens obviously stay in the web UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPointsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var receivers by remember { mutableStateOf<List<AmReceiver>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            refreshing = true
            error = null
            container.alertRepository.alertmanagerConfig()
                .onSuccess { receivers = it.receivers.sortedBy { r -> r.name.lowercase() } }
                .onFailure { error = it.message ?: "Failed to load contact points" }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contact points", fontWeight = FontWeight.SemiBold) },
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
                refreshing && receivers.isEmpty() -> LoadingState()
                receivers.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(receivers, key = { it.name }) { r -> ReceiverCard(r) }
                }
            }
        }
    }
}

@Composable
private fun ReceiverCard(receiver: AmReceiver) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                receiver.name.ifBlank { "Unnamed receiver" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (receiver.grafanaConfigs.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "No Grafana-managed channels (may be an external Alertmanager receiver).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    receiver.grafanaConfigs.forEach { cfg -> ChannelRow(cfg) }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(cfg: AmGrafanaReceiverConfig) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(8.dp),
            color = EnergyOrange.copy(alpha = 0.15f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(iconFor(cfg.type), null, tint = EnergyOrange, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                labelFor(cfg.type),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val dest = destinationSummary(cfg)
            if (dest.isNotBlank()) {
                Text(
                    dest,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (cfg.disableResolveMessage) {
                Text(
                    "Doesn't send resolve messages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun iconFor(type: String): ImageVector = when (type.lowercase()) {
    "email" -> Icons.Filled.AlternateEmail
    "slack", "teams", "discord", "telegram", "line", "wecom", "googlechat" -> Icons.Filled.Chat
    "pagerduty", "opsgenie", "victorops" -> Icons.Filled.Campaign
    "sms", "phone" -> Icons.Filled.Phone
    "webhook", "kafka", "sns", "pushover" -> Icons.Filled.Webhook
    else -> Icons.Filled.Notifications
}

private fun labelFor(type: String): String = when (type.lowercase()) {
    "email" -> "Email"
    "slack" -> "Slack"
    "pagerduty" -> "PagerDuty"
    "opsgenie" -> "OpsGenie"
    "webhook" -> "Webhook"
    "teams", "msteams" -> "Microsoft Teams"
    "discord" -> "Discord"
    "telegram" -> "Telegram"
    "pushover" -> "Pushover"
    "sns" -> "AWS SNS"
    "kafka" -> "Kafka"
    "victorops" -> "VictorOps"
    "line" -> "LINE"
    "wecom" -> "WeCom"
    "googlechat" -> "Google Chat"
    "" -> "Channel"
    else -> type.replaceFirstChar { it.uppercase() }
}

/**
 * Best-effort human summary of where a channel delivers. Grafana stores destination in
 * type-specific settings fields (addresses for email, url for webhook, etc.); we pick the most
 * user-recognizable one per type and fall back to nothing if the field is missing rather than
 * dumping raw JSON at the user.
 */
private fun destinationSummary(cfg: AmGrafanaReceiverConfig): String {
    val s = cfg.settings ?: return ""
    fun str(key: String): String? = (s[key] as? JsonPrimitive)?.contentOrNullSafe()
    return when (cfg.type.lowercase()) {
        "email" -> str("addresses").orEmpty()
        "slack" -> listOfNotNull(str("recipient"), str("channel")).firstOrNull().orEmpty()
        "webhook" -> str("url").orEmpty()
        "pagerduty" -> if (cfg.secureFields["integrationKey"] == true) "integration key configured" else ""
        "opsgenie" -> if (cfg.secureFields["apiKey"] == true) "API key configured" else str("apiUrl").orEmpty()
        "telegram" -> str("chatid").orEmpty()
        "discord" -> str("url").orEmpty()
        "teams", "msteams" -> str("url").orEmpty()
        "pushover" -> str("userKey").orEmpty()
        "line" -> if (cfg.secureFields["token"] == true) "token configured" else ""
        "googlechat" -> str("url").orEmpty()
        "sns" -> listOfNotNull(str("topic_arn"), str("phone_number")).firstOrNull().orEmpty()
        "sms" -> str("addresses").orEmpty()
        else -> str("url") ?: str("address") ?: ""
    }
}

// JsonNull is a JsonPrimitive whose .content is the string "null"; guard against that so a
// null setting silently degrades to "" instead of showing the literal text "null" in the UI.
private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is JsonNull) null else content.takeIf { it.isNotBlank() }

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.ContactMail, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("No contact points", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Grafana Alerting either isn't configured on this instance or the current user can't read the alertmanager config.",
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
        Text("Contact points unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
