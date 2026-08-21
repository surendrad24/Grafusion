package com.fusionlancers.grafusion.wear

import android.content.Context
import com.fusionlancers.grafusion.data.model.Alert
import com.fusionlancers.grafusion.data.model.AlertState
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Publishes the current firing-alert count to any paired Wear device via the DataClient.
 * The watch's AlertsDataListenerService picks it up at /grafusion/alerts and refreshes
 * the tile. Silently no-ops when Google Play Services / Wearable APIs are unavailable
 * (e.g. Play-Services-less phone) so unpaired users pay no cost.
 */
object WearAlertsPublisher {

    private const val PATH = "/grafusion/alerts"

    fun publish(context: Context, alerts: List<Alert>) {
        val firing = alerts.count { it.state == AlertState.FIRING }
        runCatching {
            val client = Wearable.getDataClient(context.applicationContext)
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putInt("firing_count", firing)
                dataMap.putLong("last_updated_ms", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            client.putDataItem(request)
        }
    }
}
