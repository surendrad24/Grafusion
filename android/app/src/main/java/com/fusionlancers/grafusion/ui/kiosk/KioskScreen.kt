package com.fusionlancers.grafusion.ui.kiosk

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.data.model.Dashboard
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import kotlinx.coroutines.delay
import android.app.Activity

/**
 * Kiosk / TV mode. Auto-cycles through the user's starred dashboards on a fixed interval,
 * one dashboard per screen. Keeps the display awake and hides system chrome so a tablet /
 * TV can display a wall of dashboards unattended.
 *
 * Playback here just navigates the existing dashboard detail; the actual auto-advance
 * happens by re-entering this screen after the interval and picking the next entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(
    container: AppContainer,
    onOpenDashboard: (uid: String, title: String) -> Unit,
    onBack: () -> Unit,
) {
    val dashboards by container.dashboardRepository.dashboards.collectAsState(initial = emptyList())
    val starred = remember(dashboards) { dashboards.filter { it.isStarred } }
    var intervalText by remember { mutableStateOf("30") }
    var running by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf(0) }

    // While running, cycle through the starred dashboards. Each tick navigates to the
    // next dashboard; when it comes back the timer restarts on the new index.
    LaunchedEffect(running, starred) {
        if (running && starred.isNotEmpty()) {
            val secs = intervalText.toLongOrNull()?.coerceAtLeast(5) ?: 30
            delay(secs * 1000L)
            val next = (index + 1) % starred.size
            index = next
            val d = starred[next]
            onOpenDashboard(d.uid, d.title)
        }
    }

    // Keep the screen on while kiosk mode is active. Restored on leave.
    val context = LocalContext.current
    DisposableEffect(running) {
        val window = (context as? Activity)?.window
        if (running) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kiosk mode", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Auto-cycle through your starred dashboards. Great for tablets or TVs on a wall mount.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Seconds per dashboard") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (starred.isNotEmpty()) {
                            running = true
                            index = 0
                            val d = starred[0]
                            onOpenDashboard(d.uid, d.title)
                        }
                    },
                    enabled = starred.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Rotation (${starred.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            if (starred.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Star some dashboards on the Home screen to add them here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(starred, key = { it.uid }) { d -> KioskItem(d, onOpenDashboard) }
                }
            }
        }
    }
}

@Composable
private fun KioskItem(d: Dashboard, onOpen: (String, String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(d.uid, d.title) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                if (!d.folderTitle.isNullOrBlank()) {
                    Text(d.folderTitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Icon(Icons.Filled.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp))
        }
    }
}
