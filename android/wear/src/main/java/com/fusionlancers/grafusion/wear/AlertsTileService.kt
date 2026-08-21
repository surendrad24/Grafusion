package com.fusionlancers.grafusion.wear

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Watch tile showing Grafana firing-alert count. The count is pushed from the phone
 * via [AlertsDataListenerService]. When no snapshot has arrived yet we render a
 * "pair phone" hint instead of a stale zero.
 */
class AlertsTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = if (prefs.contains(KEY_COUNT)) prefs.getInt(KEY_COUNT, 0) else null
        val updatedMs = prefs.getLong(KEY_UPDATED, 0L)

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(buildRoot(count, updatedMs))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )
    }

    private fun buildRoot(count: Int?, updatedMs: Long): LayoutElementBuilders.LayoutElement {
        val orange = argb(0xFFFF6A00.toInt())
        val white = argb(0xFFFFFFFF.toInt())

        val title = Text.Builder()
            .setText(getString(R.string.tile_title))
            .setFontStyle(FontStyle.Builder().setSize(sp(12f)).setColor(orange).build())
            .build()

        val subtitle = when {
            count == null -> getString(R.string.tile_pair_hint)
            count == 0 -> getString(R.string.tile_subtitle_clear)
            else -> getString(R.string.tile_subtitle_firing)
        }

        val body = Text.Builder()
            .setText(count?.toString() ?: "-")
            .setFontStyle(FontStyle.Builder().setSize(sp(42f)).setColor(white).build())
            .build()

        val sub = Text.Builder()
            .setText(subtitle)
            .setFontStyle(FontStyle.Builder().setSize(sp(12f)).setColor(white).build())
            .build()

        val updated = Text.Builder()
            .setText(
                if (updatedMs > 0) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(updatedMs))
                } else {
                    ""
                },
            )
            .setFontStyle(FontStyle.Builder().setSize(sp(10f)).setColor(white).build())
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setWidth(dp(160f))
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(dp(8f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(title)
            .addContent(body)
            .addContent(sub)
            .addContent(updated)
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MS = 15L * 60L * 1000L
        const val PREFS = "grafusion_wear_alerts"
        const val KEY_COUNT = "firing_count"
        const val KEY_UPDATED = "last_updated_ms"
    }
}
