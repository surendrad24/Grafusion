package com.fusionlancers.grafusion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fusionlancers.grafusion.MainActivity
import com.fusionlancers.grafusion.R
import com.fusionlancers.grafusion.data.model.AlertState
import com.fusionlancers.grafusion.data.model.Alert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget: shows current Grafana firing-alert count, name of the top firing
 * alert, and last-refreshed time. Taps open the app to the Alerts tab.
 *
 * Refresh runs on every AppWidgetProvider update tick (30 min by default) and immediately
 * whenever the manifest broadcasts arrive (add/enable, resize). Since network I/O isn't
 * allowed on the widget's broadcast thread we punt to a coroutine on IO.
 */
class AlertsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAll(context, manager, ids)
    }

    override fun onEnabled(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
        refreshAll(context, manager, ids)
    }

    private fun refreshAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Draw a "loading" state right away so the widget doesn't sit blank while we fetch.
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, count = null, top = null)) }

        val container = (context.applicationContext as com.fusionlancers.grafusion.GrafusionApp).container
        CoroutineScope(Dispatchers.IO).launch {
            val result = container.alertRepository.fetchAlerts()
            val alerts = result.getOrNull().orEmpty()
            val firing = alerts.filter { it.state == AlertState.FIRING }
            val top = firing.firstOrNull()
            val views = buildViews(context, count = firing.size, top = top)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }

    private fun buildViews(context: Context, count: Int?, top: Alert?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alerts)
        views.setTextViewText(R.id.widget_count, count?.toString() ?: "-")
        views.setTextViewText(
            R.id.widget_subtitle,
            when (count) {
                null -> "loading..."
                0 -> "all clear"
                1 -> "alert firing"
                else -> "alerts firing"
            },
        )
        views.setTextViewText(R.id.widget_top, top?.name ?: "")
        views.setTextViewText(
            R.id.widget_updated,
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, "alerts")
        }
        val pending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_count, pending)
        views.setOnClickPendingIntent(R.id.widget_subtitle, pending)
        views.setOnClickPendingIntent(R.id.widget_top, pending)
        views.setOnClickPendingIntent(R.id.widget_title, pending)
        return views
    }
}
