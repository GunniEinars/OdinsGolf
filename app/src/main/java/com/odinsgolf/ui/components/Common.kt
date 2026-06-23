package com.odinsgolf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.odinsgolf.data.model.GpsState
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.data.model.Units
import com.odinsgolf.location.effectiveStatus
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim
import com.odinsgolf.ui.theme.OdinRed
import kotlin.math.roundToInt

/** Format a meters value in the chosen unit, whole numbers. "—" when null. */
fun formatDistance(meters: Double?, units: Units): String =
    if (meters == null) "—" else units.fromMeters(meters).roundToInt().toString()

/** Handicap index with one decimal, e.g. "15.7". */
fun formatHandicap(index: Double): String = String.format(java.util.Locale.US, "%.1f", index)

fun statusColor(status: GpsStatus): Color = when (status) {
    GpsStatus.GOOD_FIX -> OdinGreen
    GpsStatus.WEAK_FIX -> OdinAmber
    GpsStatus.STALE_FIX -> OdinAmber
    GpsStatus.SEARCHING -> OdinAmber
    GpsStatus.PAUSED -> OdinOnDim
    GpsStatus.PERMISSION_NEEDED -> OdinRed
    GpsStatus.UNAVAILABLE -> OdinRed
}

fun statusLabel(status: GpsStatus): String = when (status) {
    GpsStatus.GOOD_FIX -> "Live"
    GpsStatus.WEAK_FIX -> "Weak"
    GpsStatus.STALE_FIX -> "Stale"
    GpsStatus.SEARCHING -> "Searching"
    GpsStatus.PAUSED -> "Paused"
    GpsStatus.PERMISSION_NEEDED -> "No permission"
    GpsStatus.UNAVAILABLE -> "No GPS"
}

@Composable
fun StatusDot(color: Color, size: Int = 8) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    ) {}
}

/** Seconds since the last fix, or null if there is no fix yet. */
fun fixAgeSeconds(gps: GpsState, nowElapsed: Long): Long? {
    val fixAt = gps.fixElapsedRealtimeMillis ?: return null
    if (gps.point == null) return null
    return ((nowElapsed - fixAt) / 1000).coerceAtLeast(0)
}

/** Compact "● Live · ±6 m · 3s" status row. */
@Composable
fun GpsStatusPill(gps: GpsState, nowElapsed: Long, debug: Boolean) {
    val status = gps.effectiveStatus(nowElapsed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusDot(statusColor(status))
        val acc = gps.accuracyMeters
        val accText = if (acc != null && gps.point != null) " · ±${acc.roundToInt()} m" else ""
        val age = fixAgeSeconds(gps, nowElapsed)
        val ageText = if (age != null) " · ${age}s" else ""
        Text(
            text = statusLabel(status) + accText + ageText,
            color = OdinOnDim,
            style = androidx.wear.compose.material.MaterialTheme.typography.caption2,
        )
    }
}

/** Developer GPS readout: exact coordinates, accuracy and fix age. */
@Composable
fun DebugGpsReadout(gps: GpsState, nowElapsed: Long) {
    val p = gps.point
    val age = fixAgeSeconds(gps, nowElapsed)
    val lines = listOf(
        "raw: ${gps.status}",
        if (p != null) "lat ${"%.6f".format(java.util.Locale.US, p.lat)}" else "lat —",
        if (p != null) "lon ${"%.6f".format(java.util.Locale.US, p.lon)}" else "lon —",
        "acc ${gps.accuracyMeters?.let { "±${it.roundToInt()} m" } ?: "—"}  age ${age?.let { "${it}s" } ?: "—"}",
    )
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEach {
            Text(it, color = OdinOnDim, style = androidx.wear.compose.material.MaterialTheme.typography.caption3)
        }
    }
}
