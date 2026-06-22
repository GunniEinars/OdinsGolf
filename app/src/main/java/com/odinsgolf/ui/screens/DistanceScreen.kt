package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.odinsgolf.data.model.Units
import com.odinsgolf.geo.Distances
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.GpsStatusPill
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim
import kotlin.math.roundToInt

@Composable
fun DistanceScreen(
    state: GolfUiState,
    onPrevHole: () -> Unit,
    onNextHole: () -> Unit,
    onToggleUnits: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenScorecard: () -> Unit,
    onOpenHoles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 14.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hole = state.hole
            val units = state.settings.units

            Text(
                text = state.course?.name ?: "Loading…",
                color = OdinOnDim,
                style = MaterialTheme.typography.caption2,
                maxLines = 1,
            )

            // Hole navigation: ◀  H7 · Par 4  ▶
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NavArrow("‹", onPrevHole)
                Text(
                    text = if (hole != null) "H${hole.displayNumber} · Par ${hole.par}" else "—",
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.SemiBold,
                )
                NavArrow("›", onNextHole)
            }

            Spacer(Modifier.height(6.dp))

            if (state.loadError != null) {
                Text(state.loadError, color = MaterialTheme.colors.error, textAlign = TextAlign.Center)
            } else if (hole != null && !hole.hasGeometry) {
                Text(
                    "Course geometry missing.\nCapture points in Survey.",
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                )
            } else if (hole != null) {
                val d = Distances.toGreen(hole, state.gps.point)

                // Hero: center-green distance.
                Text(
                    text = formatDistance(d.centerMeters, units),
                    color = OdinGreen,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("center · ${units.suffix}", color = OdinOnDim, style = MaterialTheme.typography.caption2)

                Spacer(Modifier.height(6.dp))

                // Front / Back row.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    LabeledValue("Front", formatDistance(d.frontMeters, units))
                    LabeledValue("Back", formatDistance(d.backMeters, units))
                }
                d.depthMeters?.let {
                    Text(
                        "depth ${units.fromMeters(it).roundToInt()} ${units.suffix}",
                        color = OdinOnDim,
                        style = MaterialTheme.typography.caption3,
                    )
                }

                // Up to 3 hazards/targets.
                val hazards = Distances.toHazards(hole, state.gps.point).take(3)
                if (hazards.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    hazards.forEach { (name, m) ->
                        Text(
                            "$name  ${formatDistance(m, units)} ${units.suffix}",
                            color = OdinOnDim,
                            style = MaterialTheme.typography.caption2,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            GpsStatusPill(state.gps, state.nowElapsed, state.settings.debugGps)

            Spacer(Modifier.height(10.dp))

            // Action chips.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(label = { Text("Map") }, onClick = onOpenMap)
                CompactChip(label = { Text("Card") }, onClick = onOpenScorecard)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(label = { Text("Holes") }, onClick = onOpenHoles)
                CompactChip(label = { Text(units.suffix) }, onClick = onToggleUnits)
                CompactChip(label = { Text("⚙") }, onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun NavArrow(symbol: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.height(34.dp),
    ) {
        Text(symbol, fontSize = 20.sp)
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.title2, fontWeight = FontWeight.SemiBold)
        Text(label, color = OdinOnDim, style = MaterialTheme.typography.caption3)
    }
}
