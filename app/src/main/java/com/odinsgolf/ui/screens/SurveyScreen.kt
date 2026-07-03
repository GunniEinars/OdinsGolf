package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.odinsgolf.data.SurveyKind
import com.odinsgolf.data.SurveyPoint
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.geo.Geo
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.GpsStatusPill
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.components.rotaryScroll
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim

@Composable
fun SurveyScreen(
    state: GolfUiState,
    holePoints: List<SurveyPoint>,
    hasCapturedPoints: Boolean,
    onCapture: (SurveyKind) -> Boolean,
    onDeletePoint: (Long) -> Unit,
    onClearAll: () -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        val scroll = rememberScrollState()
        val hole = state.hole
        val captureMsg = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        val canCapture = state.gpsStatus.let {
            it == GpsStatus.GOOD_FIX || it == GpsStatus.WEAK_FIX
        }
        // Confirm a capture with the kind and the accuracy at the moment it lands.
        fun capture(kind: SurveyKind, label: String) {
            val ok = onCapture(kind)
            val acc = state.gps.accuracyMeters
            captureMsg.value = if (ok) {
                "$label captured ✓" + (acc?.let { " (±${it.toInt()} m)" } ?: "")
            } else {
                "No GPS fix — try again"
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .rotaryScroll(scroll)
                .padding(horizontal = 14.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Survey · Hole ${hole?.displayNumber ?: "—"}", style = MaterialTheme.typography.title3)
            Text(
                "Stand on the spot, then capture. Accuracy matters — wait for a Live fix.",
                color = OdinOnDim,
                style = MaterialTheme.typography.caption3,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            GpsStatusPill(state.gps, state.nowElapsed, state.settings.gpsMode.staleAfterMillis, state.settings.debugGps)
            Spacer(Modifier.height(10.dp))

            CaptureChip("Capture TEE", canCapture) { capture(SurveyKind.TEE, "Tee") }
            CaptureChip("Capture GREEN FRONT", canCapture) { capture(SurveyKind.FRONT, "Green front") }
            CaptureChip("Capture GREEN CENTER", canCapture) { capture(SurveyKind.CENTER, "Green center") }
            CaptureChip("Capture GREEN BACK", canCapture) { capture(SurveyKind.BACK, "Green back") }
            CaptureChip("Capture HAZARD", canCapture) { capture(SurveyKind.HAZARD, "Hazard") }

            if (captureMsg.value.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    captureMsg.value,
                    color = OdinGreen,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(12.dp))
            // Captured points for this hole: verify each against where you stand (live
            // distance) and delete a wrong one (e.g. a hazard tapped twice). Re-capturing
            // TEE/FRONT/CENTER/BACK simply replaces it; hazards add, so delete duplicates.
            Text("Captured · Hole ${hole?.displayNumber ?: "—"}", color = OdinOnDim, style = MaterialTheme.typography.caption2)
            if (holePoints.isEmpty()) {
                Text(
                    "Nothing captured yet.",
                    color = OdinOnDim,
                    style = MaterialTheme.typography.caption3,
                )
            } else {
                // Stable order: tee, front, center, back, then hazards by capture time.
                val ordered = holePoints.sortedWith(
                    compareBy({ kindOrder(it.kind) }, { it.epochMillis }),
                )
                ordered.forEach { p ->
                    CapturedRow(p, state.gps.point, state.settings.units, onDeletePoint)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "✕ deletes a point and reverts it to the built-in one.",
                    color = OdinOnDim,
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                )
            }

            // Revert everything to the built-in (shipped) data. Captured points live in a
            // separate file overlaid on top — the built-in coordinates are never overwritten,
            // so this is always a safe, full undo. Two-tap confirm so it can't happen by accident.
            if (hasCapturedPoints) {
                Spacer(Modifier.height(10.dp))
                val armed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(armed.value) {
                    if (armed.value) { kotlinx.coroutines.delay(3000); armed.value = false }
                }
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = {
                        if (armed.value) { onClearAll(); armed.value = false } else armed.value = true
                    },
                    label = { Text(if (armed.value) "Confirm — revert all?" else "Reset to built-in points") },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Captured points save to a file you can pull with adb (see SETUP docs).",
                color = OdinOnDim,
                style = MaterialTheme.typography.caption3,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun kindOrder(kind: SurveyKind): Int = when (kind) {
    SurveyKind.TEE -> 0
    SurveyKind.FRONT -> 1
    SurveyKind.CENTER -> 2
    SurveyKind.BACK -> 3
    SurveyKind.HAZARD -> 4
}

@Composable
private fun CapturedRow(
    point: SurveyPoint,
    here: GeoPoint?,
    units: com.odinsgolf.data.model.Units,
    onDelete: (Long) -> Unit,
) {
    // Live distance from where you now stand — an on-the-spot sanity check that the
    // point landed where you meant it to (a green centre should read a few metres, not 90).
    val away = here?.let { Geo.distanceMeters(it, GeoPoint(point.lat, point.lon)) }
    val acc = point.accuracyMeters?.let { "±${it.toInt()}m" } ?: ""
    val awayText = away?.let { "· ${formatDistance(it, units)} ${units.suffix} away" } ?: ""
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${label(point)} $acc $awayText".trim(),
            color = OdinGreen,
            style = MaterialTheme.typography.caption3,
            modifier = Modifier.weight(1f).padding(end = 6.dp),
        )
        CompactChip(
            onClick = { onDelete(point.epochMillis) },
            colors = ChipDefaults.secondaryChipColors(),
            label = { Text("✕", color = OdinAmber) },
        )
    }
}

private fun label(p: SurveyPoint): String = when (p.kind) {
    SurveyKind.TEE -> "Tee"
    SurveyKind.FRONT -> "Front"
    SurveyKind.CENTER -> "Center"
    SurveyKind.BACK -> "Back"
    SurveyKind.HAZARD -> p.label.ifBlank { "Hazard" }
}

@Composable
private fun CaptureChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        enabled = enabled,
        colors = ChipDefaults.primaryChipColors(),
        onClick = onClick,
        label = { Text(label) },
    )
}
