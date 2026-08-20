package com.fusionlancers.grafusion.ui.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fusionlancers.grafusion.ui.theme.EnergyOrange

private enum class PermState { GRANTED, DENIED, UNAVAILABLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Bump this counter to force a recheck after returning from a Settings intent.
    var recheck by remember { mutableIntStateOf(0) }

    val notifState = remember(recheck) { notificationsState(context) }
    val batteryState = remember(recheck) { batteryOptimizationState(context) }
    val dndState = remember(recheck) { dndAccessState(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { recheck++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification permissions", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Grafusion delivers push alerts through your Grafana webhook relay. Grant these permissions so alerts reach you reliably — even at night or during battery saver.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
            item {
                PermissionRow(
                    icon = Icons.Filled.Notifications,
                    title = "Post notifications",
                    description = "Show alert banners and the alerts channel.",
                    state = notifState,
                    actionLabel = if (notifState == PermState.GRANTED) "Granted" else "Grant",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openAppNotificationSettings(context); recheck++
                        }
                    },
                )
            }
            item {
                PermissionRow(
                    icon = Icons.Filled.BatteryFull,
                    title = "Ignore battery optimizations",
                    description = "Keep Grafusion able to receive alerts when the device is idle or in doze.",
                    state = batteryState,
                    actionLabel = if (batteryState == PermState.GRANTED) "Ignored" else "Open settings",
                    onAction = { openBatteryOptimizationSettings(context); recheck++ },
                )
            }
            item {
                PermissionRow(
                    icon = Icons.Filled.DoNotDisturbOff,
                    title = "Do Not Disturb access",
                    description = "Allow critical alerts to break through DND (used by per-priority overrides).",
                    state = dndState,
                    actionLabel = if (dndState == PermState.GRANTED) "Granted" else "Open settings",
                    onAction = { openDndAccessSettings(context); recheck++ },
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Button(
                        onClick = { recheck++ },
                        colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Recheck", maxLines = 1)
                    }
                }
            }
        }
    }
    LaunchedEffect(recheck) { /* recomposition trigger */ }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    state: PermState,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = EnergyOrange.copy(alpha = 0.18f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = EnergyOrange, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                StateBadge(state)
            }
            if (state != PermState.UNAVAILABLE) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    enabled = state != PermState.GRANTED,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun StateBadge(state: PermState) {
    val (icon, tint, label) = when (state) {
        PermState.GRANTED -> Triple(Icons.Filled.CheckCircle, Color(0xFF22C55E), "OK")
        PermState.DENIED -> Triple(Icons.Filled.ErrorOutline, Color(0xFFEF4444), "Off")
        PermState.UNAVAILABLE -> Triple(Icons.Filled.ErrorOutline, Color(0xFF6B7280), "N/A")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun notificationsState(context: Context): PermState {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermState.GRANTED
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return if (granted) PermState.GRANTED else PermState.DENIED
}

private fun batteryOptimizationState(context: Context): PermState {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return PermState.UNAVAILABLE
    return if (pm.isIgnoringBatteryOptimizations(context.packageName)) PermState.GRANTED else PermState.DENIED
}

private fun dndAccessState(context: Context): PermState {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return PermState.UNAVAILABLE
    return if (nm.isNotificationPolicyAccessGranted) PermState.GRANTED else PermState.DENIED
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openBatteryOptimizationSettings(context: Context) {
    // Deep link that lets the user whitelist Grafusion in one tap.
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        // Some OEMs block the direct request — fall back to the list screen.
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openDndAccessSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
