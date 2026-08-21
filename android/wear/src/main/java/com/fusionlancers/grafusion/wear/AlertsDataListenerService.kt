package com.fusionlancers.grafusion.wear

import android.content.Context
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives the DataItem the phone app publishes at /grafusion/alerts. Persists the
 * firing count + timestamp to SharedPreferences so [AlertsTileService] can render
 * the latest snapshot, then asks the Wear system to redraw the tile.
 */
class AlertsDataListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        var updated = false
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != PATH) continue
            val map = DataMapItem.fromDataItem(item).dataMap
            val count = map.getInt("firing_count", 0)
            val updatedMs = map.getLong("last_updated_ms", System.currentTimeMillis())
            applicationContext.getSharedPreferences(AlertsTileService.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(AlertsTileService.KEY_COUNT, count)
                .putLong(AlertsTileService.KEY_UPDATED, updatedMs)
                .apply()
            updated = true
        }
        if (updated) {
            TileService.getUpdater(applicationContext)
                .requestUpdate(AlertsTileService::class.java)
        }
    }

    companion object {
        const val PATH = "/grafusion/alerts"
    }
}
