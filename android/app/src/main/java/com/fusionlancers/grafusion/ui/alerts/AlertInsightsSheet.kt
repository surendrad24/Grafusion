package com.fusionlancers.grafusion.ui.alerts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fusionlancers.grafusion.data.db.NotificationHistoryEntity
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Aggregates a 30-day notification history window and renders a lightweight bar chart plus a
 * severity breakdown. We deliberately avoid Vico for this - the shape is simple enough that a
 * hand-drawn Canvas keeps the sheet snappy and dependency-free.
 *
 * "MTTR" isn't derivable here because we only log receipt events, not resolution; if that becomes
 * important later we'll need to persist state transitions from the Alertmanager sync.
 */
@Composable
fun AlertInsightsSheet(history: List<NotificationHistoryEntity>) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val windowDays = 30
    val buckets = remember(history) {
        val perDay = LongArray(windowDays)
        var criticalCount = 0
        var regularCount = 0
        val cutoff = today.minusDays((windowDays - 1).toLong())
        history.forEach { row ->
            val day = Instant.ofEpochMilli(row.receivedAt).atZone(zone).toLocalDate()
            if (!day.isBefore(cutoff) && !day.isAfter(today)) {
                val idx = (windowDays - 1) - java.time.temporal.ChronoUnit.DAYS.between(day, today).toInt()
                if (idx in 0 until windowDays) perDay[idx]++
                if (row.severity.equals("important", ignoreCase = true)) criticalCount++ else regularCount++
            }
        }
        BucketStats(perDay = perDay, critical = criticalCount, regular = regularCount)
    }

    val total = buckets.critical + buckets.regular
    val peak = buckets.perDay.max().coerceAtLeast(1)
    val dailyAvg = total.toDouble() / windowDays

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            "Alert insights",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Push notifications received over the last $windowDays days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(label = "Total", value = total.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "Critical", value = buckets.critical.toString(), modifier = Modifier.weight(1f), tint = Color(0xFFE74C3C))
            StatTile(label = "Per day", value = formatAvg(dailyAvg), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Daily volume",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(8.dp))
        DailyBarChart(perDay = buckets.perDay, peak = peak)

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                today.minusDays((windowDays - 1).toLong()).format(SHORT_DATE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Text(
                today.format(SHORT_DATE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private data class BucketStats(val perDay: LongArray, val critical: Int, val regular: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BucketStats) return false
        return perDay.contentEquals(other.perDay) && critical == other.critical && regular == other.regular
    }
    override fun hashCode(): Int = perDay.contentHashCode() * 31 + critical * 13 + regular
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, tint: Color = EnergyOrange) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.12f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DailyBarChart(perDay: LongArray, peak: Long) {
    val barColor = EnergyOrange
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        val n = perDay.size
        val gap = 2f
        val slotWidth = (size.width - gap * (n - 1)) / n
        perDay.forEachIndexed { i, v ->
            val x = i * (slotWidth + gap)
            val h = if (v <= 0L) 0f else (v.toFloat() / peak) * size.height
            // Draw an empty track first so days with zero events still get a visible tick.
            drawRect(
                color = empty,
                topLeft = Offset(x, 0f),
                size = Size(slotWidth, size.height),
            )
            if (h > 0f) {
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - h),
                    size = Size(slotWidth, h),
                )
            }
        }
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d")

private fun formatAvg(value: Double): String = if (value >= 10) {
    value.toInt().toString()
} else {
    "%.1f".format(value)
}
