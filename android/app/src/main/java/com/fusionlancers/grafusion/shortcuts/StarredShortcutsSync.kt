package com.fusionlancers.grafusion.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.fusionlancers.grafusion.MainActivity
import com.fusionlancers.grafusion.R
import com.fusionlancers.grafusion.data.model.Dashboard

/**
 * Publishes the top starred dashboards as launcher shortcuts so long-pressing the Grafusion
 * icon jumps straight into them. We cap at the platform-declared max (usually 4-5) and use the
 * same EXTRA_OPEN_ROUTE plumbing MainActivity already understands - no new intent surface.
 *
 * Called from a Flow collector on the dashboards list; when the starred set changes we rewrite
 * the whole shortcut set (cheaper than diffing when the list is this small).
 */
object StarredShortcutsSync {

    fun sync(context: Context, dashboards: List<Dashboard>) {
        val app = context.applicationContext
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(app).coerceAtLeast(1)
        val starred = dashboards.asSequence()
            .filter { it.isStarred }
            .distinctBy { it.uid }
            .take(max)
            .toList()

        // No stars yet -> clear the pill so users don't see leftover shortcuts from a previous account.
        if (starred.isEmpty()) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(app)
            return
        }

        val infos = starred.mapIndexed { index, dash ->
            val intent = Intent(app, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.EXTRA_OPEN_ROUTE, "dashboard/${dash.uid}?title=${Uri_encode(dash.title)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            ShortcutInfoCompat.Builder(app, "starred-${dash.uid}")
                .setShortLabel(dash.title.take(20).ifBlank { "Dashboard" })
                .setLongLabel(dash.title.take(48).ifBlank { "Dashboard" })
                .setIcon(IconCompat.createWithResource(app, R.mipmap.ic_launcher))
                .setIntent(intent)
                .setRank(index)
                .build()
        }
        // setDynamicShortcuts replaces the whole set atomically - matches our "rewrite on change" model.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(app, infos) }
    }

    // Tiny inline encoder so we don't have to import android.net.Uri just for one call - keeps the
    // helper JVM-testable if we ever want to unit-test the label truncation.
    private fun Uri_encode(s: String): String =
        android.net.Uri.encode(s)
}
