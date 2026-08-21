package com.fusionlancers.grafusion.ui.admin

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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.api.GrafanaOrg
import com.fusionlancers.grafusion.data.api.GrafanaServiceAccount
import com.fusionlancers.grafusion.data.api.GrafanaTeam
import com.fusionlancers.grafusion.data.api.OrgUser
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch

private enum class AdminTab(val label: String, val icon: ImageVector) {
    Users("Users", Icons.Filled.Person),
    Teams("Teams", Icons.Filled.Groups),
    Service("Service", Icons.Filled.Key),
    Orgs("Orgs", Icons.Filled.Business),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(AdminTab.Users) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf<List<OrgUser>>(emptyList()) }
    var teams by remember { mutableStateOf<List<GrafanaTeam>>(emptyList()) }
    var accts by remember { mutableStateOf<List<GrafanaServiceAccount>>(emptyList()) }
    var orgs by remember { mutableStateOf<List<GrafanaOrg>>(emptyList()) }
    val scope = rememberCoroutineScope()

    suspend fun load(t: AdminTab) {
        loading = true
        error = null
        when (t) {
            AdminTab.Users -> container.adminRepository.orgUsers()
                .onSuccess { users = it }.onFailure { error = it.message }
            AdminTab.Teams -> container.adminRepository.teams()
                .onSuccess { teams = it }.onFailure { error = it.message }
            AdminTab.Service -> container.adminRepository.serviceAccounts()
                .onSuccess { accts = it }.onFailure { error = it.message }
            AdminTab.Orgs -> container.adminRepository.orgs()
                .onSuccess { orgs = it }.onFailure { error = it.message }
        }
        loading = false
    }

    LaunchedEffect(tab) { load(tab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grafana admin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { load(tab) } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                AdminTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label, fontWeight = if (tab == t) FontWeight.SemiBold else FontWeight.Normal) },
                        icon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EnergyOrange)
                    }
                    error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (tab) {
                            AdminTab.Users -> items(users, key = { it.userId }) { UserRow(it) }
                            AdminTab.Teams -> items(teams, key = { it.id }) { TeamRow(it) }
                            AdminTab.Service -> items(accts, key = { it.id }) { ServiceAcctRow(it) }
                            AdminTab.Orgs -> items(orgs, key = { it.id }) { OrgRow(it) }
                        }
                        if (
                            (tab == AdminTab.Users && users.isEmpty()) ||
                            (tab == AdminTab.Teams && teams.isEmpty()) ||
                            (tab == AdminTab.Service && accts.isEmpty()) ||
                            (tab == AdminTab.Orgs && orgs.isEmpty())
                        ) {
                            item { EmptyHint(tab) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(tab: AdminTab) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "No ${tab.label.lowercase()} to show",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun UserRow(u: OrgUser) {
    AdminCard(
        title = u.name.ifBlank { u.login },
        subtitle = u.email.ifBlank { "@${u.login}" },
        badge = u.role,
        icon = Icons.Filled.Person,
        detail = u.lastSeenAtAge?.let { "Seen $it" }.orEmpty(),
        disabled = u.isDisabled,
    )
}

@Composable
private fun TeamRow(t: GrafanaTeam) {
    AdminCard(
        title = t.name,
        subtitle = t.email.ifBlank { "team #${t.id}" },
        badge = null,
        icon = Icons.Filled.Groups,
        detail = "${t.memberCount} members",
    )
}

@Composable
private fun ServiceAcctRow(a: GrafanaServiceAccount) {
    AdminCard(
        title = a.name.ifBlank { a.login },
        subtitle = "@${a.login}",
        badge = a.role,
        icon = Icons.Filled.Key,
        detail = "${a.tokens} tokens",
        disabled = a.isDisabled,
    )
}

@Composable
private fun OrgRow(o: GrafanaOrg) {
    AdminCard(
        title = o.name,
        subtitle = "org #${o.id}",
        badge = null,
        icon = Icons.Filled.Business,
        detail = "",
    )
}

@Composable
private fun AdminCard(
    title: String,
    subtitle: String,
    badge: String?,
    icon: ImageVector,
    detail: String,
    disabled: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = EnergyOrange.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (disabled) {
                        Spacer(Modifier.width(6.dp))
                        Text("DISABLED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            if (!badge.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EnergyOrange.copy(alpha = 0.15f),
                ) {
                    Text(
                        badge.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EnergyOrange,
                    )
                }
            }
        }
    }
}
