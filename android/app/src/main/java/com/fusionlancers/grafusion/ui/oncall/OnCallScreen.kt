package com.fusionlancers.grafusion.ui.oncall

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.db.LocalScheduleEntity
import com.fusionlancers.grafusion.data.model.Incident
import com.fusionlancers.grafusion.data.model.IncidentState
import com.fusionlancers.grafusion.data.model.ScheduleSnapshot
import com.fusionlancers.grafusion.data.model.UpcomingShift
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar

private enum class OnCallTab { Schedules, Incidents, Local }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnCallScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var schedules by remember { mutableStateOf<List<ScheduleSnapshot>>(emptyList()) }
    var incidents by remember { mutableStateOf<List<Incident>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(OnCallTab.Schedules) }
    val snackbar = remember { SnackbarHostState() }
    val localSnapshots by container.localOnCallRepository.observe()
        .collectAsState(initial = emptyList())
    val localSchedules by container.localOnCallRepository.observeSchedules()
        .collectAsState(initial = emptyList())

    fun reload() {
        scope.launch {
            refreshing = true
            error = null
            val schedResult = container.onCallRepository.fetchSchedules()
            val incResult = container.onCallRepository.fetchIncidents()
            schedResult.onSuccess { schedules = it }
            incResult.onSuccess { incidents = it }
            val firstFailure = schedResult.exceptionOrNull() ?: incResult.exceptionOrNull()
            if (firstFailure != null && schedules.isEmpty() && incidents.isEmpty()) {
                error = firstFailure.message ?: "Failed to load OnCall data"
            }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(error) {
        // If the Grafana OnCall plugin isn't installed, quietly land the user on the Local tab so
        // the screen still does something useful instead of just showing an error.
        if (error?.contains("plugin is not installed", ignoreCase = true) == true &&
            tab == OnCallTab.Schedules
        ) {
            tab = OnCallTab.Local
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "OnCall",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            val firing = incidents.count { it.state == IncidentState.FIRING }
            if (firing > 0) StatusPill("$firing firing", EnergyOrange)
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabPill("Schedules ${schedules.size}", tab == OnCallTab.Schedules) { tab = OnCallTab.Schedules }
            TabPill("Incidents ${incidents.size}", tab == OnCallTab.Incidents) { tab = OnCallTab.Incidents }
            TabPill("Local ${localSchedules.size}", tab == OnCallTab.Local) { tab = OnCallTab.Local }
        }

        Spacer(Modifier.height(12.dp))

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::reload,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                tab == OnCallTab.Local -> LocalTab(
                    snapshots = localSnapshots,
                    schedules = localSchedules,
                    onCreateSchedule = { name ->
                        scope.launch { container.localOnCallRepository.createSchedule(name) }
                    },
                    onDeleteSchedule = { id ->
                        scope.launch { container.localOnCallRepository.deleteSchedule(id) }
                    },
                    onAddShift = { scheduleId, user, startMs, endMs ->
                        scope.launch {
                            container.localOnCallRepository.addShift(scheduleId, user, startMs, endMs)
                        }
                    },
                )
                error != null -> ErrorState(error!!)
                refreshing && schedules.isEmpty() && incidents.isEmpty() -> LoadingState()
                tab == OnCallTab.Schedules -> SchedulesList(schedules)
                else -> IncidentsList(
                    incidents = incidents,
                    onAcknowledge = { id ->
                        scope.launch {
                            container.onCallRepository.acknowledge(id)
                                .onSuccess {
                                    snackbar.showSnackbar("Incident acknowledged")
                                    reload()
                                }
                                .onFailure { snackbar.showSnackbar("Ack failed: ${it.message}") }
                        }
                    },
                    onResolve = { id ->
                        scope.launch {
                            container.onCallRepository.resolve(id)
                                .onSuccess {
                                    snackbar.showSnackbar("Incident resolved")
                                    reload()
                                }
                                .onFailure { snackbar.showSnackbar("Resolve failed: ${it.message}") }
                        }
                    },
                )
            }
        }
    }

    SnackbarHost(hostState = snackbar)
}

@Composable
private fun SchedulesList(schedules: List<ScheduleSnapshot>) {
    if (schedules.isEmpty()) {
        EmptyState("No OnCall schedules configured.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(schedules, key = { it.id }) { schedule ->
            ScheduleCard(schedule)
        }
    }
}

@Composable
private fun ScheduleCard(schedule: ScheduleSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                schedule.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (schedule.currentOnCall.isEmpty()) {
                Text(
                    "No one is currently on-call",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else {
                schedule.currentOnCall.forEach { user -> OnCallRow(user, badge = "NOW", badgeColor = EnergyOrange) }
            }
            if (schedule.upcoming.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Upcoming",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                schedule.upcoming.forEach { shift -> UpcomingRow(shift) }
            }
        }
    }
}

@Composable
private fun OnCallRow(user: String, badge: String, badgeColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = badgeColor.copy(alpha = 0.15f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, tint = badgeColor, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(
            user,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatusPill(badge, badgeColor)
    }
}

@Composable
private fun UpcomingRow(shift: UpcomingShift) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        Icon(
            Icons.Filled.Schedule,
            null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                shift.user,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val when_ = humanShiftWindow(shift.startsAt, shift.endsAt)
            if (when_.isNotBlank()) {
                Text(
                    when_,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        if (shift.isOverride) StatusPill("OVERRIDE", Color(0xFF7C3AED))
    }
}

@Composable
private fun IncidentsList(
    incidents: List<Incident>,
    onAcknowledge: (String) -> Unit,
    onResolve: (String) -> Unit,
) {
    if (incidents.isEmpty()) {
        EmptyState("No firing OnCall incidents right now.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(incidents, key = { it.id }) { incident ->
            IncidentCard(incident, onAcknowledge = onAcknowledge, onResolve = onResolve)
        }
    }
}

@Composable
private fun IncidentCard(incident: Incident, onAcknowledge: (String) -> Unit, onResolve: (String) -> Unit) {
    val context = LocalContext.current
    val tint = when (incident.state) {
        IncidentState.FIRING -> Color(0xFFE74C3C)
        IncidentState.ACKNOWLEDGED -> EnergyOrange
        IncidentState.RESOLVED -> Color(0xFF2ECC71)
        IncidentState.SILENCED -> Color(0xFF95A5A6)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.NotificationsActive, null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        incident.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        incident.integration,
                        "${incident.alertsCount} alert${if (incident.alertsCount == 1) "" else "s"}",
                        incident.createdAt?.let { humanAge(it) },
                    ).joinToString(" - ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                StatusPill(incident.state.name, tint)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (incident.state == IncidentState.FIRING) {
                    Button(
                        onClick = { onAcknowledge(incident.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                    ) {
                        Icon(Icons.Filled.Done, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Ack")
                    }
                }
                if (incident.state != IncidentState.RESOLVED) {
                    Button(
                        onClick = { onResolve(incident.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Resolve")
                    }
                }
                if (!incident.webUrl.isNullOrBlank()) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(incident.webUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) {
                        Icon(Icons.Filled.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Open")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EnergyOrange.copy(alpha = 0.18f),
            selectedLabelColor = EnergyOrange,
        ),
    )
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EnergyOrange)
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2ECC71), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Error, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "OnCall unavailable",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

private fun humanShiftWindow(start: String?, end: String?): String {
    val s = start?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val e = end?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return when {
        s != null && e != null -> "${timeFormatter.format(s)} -> ${timeFormatter.format(e)}"
        s != null -> "from ${timeFormatter.format(s)}"
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalTab(
    snapshots: List<ScheduleSnapshot>,
    schedules: List<LocalScheduleEntity>,
    onCreateSchedule: (String) -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    onAddShift: (scheduleId: Long, user: String, startMs: Long, endMs: Long) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var addShiftFor by remember { mutableStateOf<LocalScheduleEntity?>(null) }

    if (schedules.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Schedule, null, tint = EnergyOrange, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "No local schedules yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Create a rotation on the phone when the Grafana OnCall plugin isn't installed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showCreate = true },
                colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("New schedule")
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = { showCreate = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("New schedule")
                    }
                }
            }
            items(schedules, key = { it.id }) { sched ->
                val snapshot = snapshots.firstOrNull { it.id == "local-${sched.id}" }
                LocalScheduleCard(
                    entity = sched,
                    snapshot = snapshot,
                    onAddShift = { addShiftFor = sched },
                    onDelete = { onDeleteSchedule(sched.id) },
                )
            }
        }
    }

    if (showCreate) {
        AddScheduleDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                onCreateSchedule(name)
                showCreate = false
            },
        )
    }
    addShiftFor?.let { sched ->
        AddShiftDialog(
            scheduleName = sched.name,
            onDismiss = { addShiftFor = null },
            onConfirm = { user, startMs, endMs ->
                onAddShift(sched.id, user, startMs, endMs)
                addShiftFor = null
            },
        )
    }
}

@Composable
private fun LocalScheduleCard(
    entity: LocalScheduleEntity,
    snapshot: ScheduleSnapshot?,
    onAddShift: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entity.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onAddShift) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add shift", tint = EnergyOrange)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete schedule",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val current = snapshot?.currentOnCall.orEmpty()
            if (current.isEmpty()) {
                Text(
                    "No one is currently on-call",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else {
                current.forEach { user -> OnCallRow(user, badge = "NOW", badgeColor = EnergyOrange) }
            }
            val upcoming = snapshot?.upcoming.orEmpty()
            if (upcoming.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Upcoming",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                upcoming.forEach { shift -> UpcomingRow(shift) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New schedule") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("Primary rotation") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim().ifBlank { "Untitled" }) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShiftDialog(
    scheduleName: String,
    onDismiss: () -> Unit,
    onConfirm: (user: String, startMs: Long, endMs: Long) -> Unit,
) {
    val now = remember { Calendar.getInstance() }
    var user by remember { mutableStateOf("") }
    // Default to a shift that starts now and runs 8 hours - a common on-call window and easy to nudge.
    var startMs by remember { mutableStateOf(now.timeInMillis) }
    var endMs by remember { mutableStateOf(now.timeInMillis + 8L * 60 * 60 * 1000) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add shift to $scheduleName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    singleLine = true,
                    label = { Text("On-call user") },
                    placeholder = { Text("alice@example.com") },
                )
                Spacer(Modifier.height(12.dp))
                ShiftEndpointRow(label = "Starts", millis = startMs, onClick = { pickingStart = true })
                Spacer(Modifier.height(6.dp))
                ShiftEndpointRow(label = "Ends", millis = endMs, onClick = { pickingEnd = true })
                if (endMs <= startMs) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "End must be after start",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = endMs > startMs,
                onClick = { onConfirm(user.trim().ifBlank { "unassigned" }, startMs, endMs) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingStart) {
        DateTimePickerDialog(
            initialMs = startMs,
            onDismiss = { pickingStart = false },
            onPicked = { startMs = it; pickingStart = false },
        )
    }
    if (pickingEnd) {
        DateTimePickerDialog(
            initialMs = endMs,
            onDismiss = { pickingEnd = false },
            onPicked = { endMs = it; pickingEnd = false },
        )
    }
}

@Composable
private fun ShiftEndpointRow(label: String, millis: Long, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text("$label: ${timeFormatter.format(Instant.ofEpochMilli(millis))}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(
    initialMs: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    val initial = remember(initialMs) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(initialMs), ZoneId.systemDefault())
    }
    var pickedDateMs by remember { mutableStateOf<Long?>(null) }

    if (pickedDateMs == null) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickedDateMs = dateState.selectedDateMillis ?: initialMs }) {
                    Text("Next")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick time") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) } },
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker emits a UTC-midnight epoch; add the picked wall-clock time interpreted in the
                    // user's zone so the resulting instant matches what they see on-screen.
                    val dateUtc = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(pickedDateMs!!),
                        ZoneOffset.UTC,
                    ).toLocalDate()
                    val ldt = dateUtc.atTime(timeState.hour, timeState.minute)
                    onPicked(ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

private fun humanAge(createdAt: String): String {
    val created = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return ""
    val d = Duration.between(created, Instant.now())
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        else -> "${d.toDays()}d ago"
    }
}
