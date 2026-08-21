package com.fusionlancers.grafusion.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fusionlancers.grafusion.GrafusionApp
import com.fusionlancers.grafusion.MainActivity
import com.fusionlancers.grafusion.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM push messages forwarded by the Go relay from Grafana Alertmanager webhooks.
 *
 * The service is declared unconditionally in the manifest, but Firebase only routes messages
 * to it when the FCM SDK is initialized (i.e. google-services.json was present at build time).
 * Without google-services.json this class is dormant and the app falls back to the device-ID
 * stub for relay routing.
 */
class GrafusionMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val app = application as? GrafusionApp ?: return
        app.container.notificationPreferences.setFcmToken(token)
        // Re-register so the relay learns the fresh token. Silently swallow failures - the
        // AccountsScreen "Register device" button gives the user a manual retry surface.
        scope.launch {
            runCatching { app.container.notificationsRepository.registerCurrentDevice() }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Grafana webhook -> relay forwards a "data" payload. Fall back to notification block
        // if the relay sent a display-ready notification instead.
        val title = message.data["title"]
            ?: message.notification?.title
            ?: "Grafana alert"
        val body = message.data["body"]
            ?: message.notification?.body
            ?: message.data["summary"]
            ?: "Alert received"
        val fingerprint = message.data["fingerprint"] ?: message.data["alert_fingerprint"]
        val alertName = message.data["alertname"] ?: message.data["alert_name"] ?: title
        showAlertNotification(this, title, body, fingerprint, alertName)
    }
}

/** Notification channel + IDs shared by the messaging service and any local test paths. */
object NotificationChannels {
    const val ALERTS_ID = "grafusion_alerts"
    private const val ALERTS_NAME = "Grafana alerts"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(ALERTS_ID) != null) return
        val channel = NotificationChannel(ALERTS_ID, ALERTS_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Firing / pending Grafana alerts routed through the Grafusion relay."
            enableVibration(true)
        }
        mgr.createNotificationChannel(channel)
    }
}

internal fun showAlertNotification(
    context: Context,
    title: String,
    body: String,
    fingerprint: String? = null,
    alertName: String? = null,
) {
    NotificationChannels.ensureCreated(context)
    // POST_NOTIFICATIONS is runtime-required on Android 13+; the PermissionsScreen prompts for it.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }
    val openIntent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .apply {
            if (!fingerprint.isNullOrBlank()) putExtra(MainActivity.EXTRA_ALERT_FINGERPRINT, fingerprint)
            if (!alertName.isNullOrBlank()) putExtra(MainActivity.EXTRA_ALERT_NAME, alertName)
        }
    // Use a per-alert request code so successive notifications don't overwrite each other's extras.
    val requestCode = (fingerprint ?: alertName ?: title).hashCode()
    val openApp = PendingIntent.getActivity(
        context,
        requestCode,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, NotificationChannels.ALERTS_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openApp)
        .build()
    NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
}
