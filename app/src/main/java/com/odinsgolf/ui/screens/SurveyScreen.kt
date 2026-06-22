package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.odinsgolf.data.SurveyKind
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.location.effectiveStatus
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.GpsStatusPill
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim

@Composable
fun SurveyScreen(
    state: GolfUiState,
    onCapture: (SurveyKind) -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        val scroll = rememberScrollState()
        val hole = state.hole
        val canCapture = state.gps.effectiveStatus(state.nowElapsed).let {
            it == GpsStatus.GOOD_FIX || it == GpsStatus.WEAK_FIX
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
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
            GpsStatusPill(state.gps, state.nowElapsed, state.settings.debugGps)
            Spacer(Modifier.height(10.dp))

            CaptureChip("Capture TEE", canCapture) { onCapture(SurveyKind.TEE) }
            CaptureChip("Capture GREEN FRONT", canCapture) { onCapture(SurveyKind.FRONT) }
            CaptureChip("Capture GREEN CENTER", canCapture) { onCapture(SurveyKind.CENTER) }
            CaptureChip("Capture GREEN BACK", canCapture) { onCapture(SurveyKind.BACK) }
            CaptureChip("Capture HAZARD", canCapture) { onCapture(SurveyKind.HAZARD) }

            Spacer(Modifier.height(10.dp))
            // Show what is currently known for this hole (incl. captured overlay).
            hole?.let {
                Text("Known for this hole:", color = OdinOnDim, style = MaterialTheme.typography.caption2)
                Known("Tee", it.tee != null)
                Known("Front", it.green.front != null)
                Known("Center", it.green.center != null)
                Known("Back", it.green.back != null)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Captured points save to a file you can pull with adb (see SETUP docs).",
                color = OdinOnDim,
                style = MaterialTheme.typography.caption3,
                textAlign = TextAlign.Center,
            )
        }
    }
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

@Composable
private fun Known(label: String, present: Boolean) {
    Text(
        "$label: ${if (present) "✓" else "—"}",
        color = if (present) OdinGreen else OdinOnDim,
        style = MaterialTheme.typography.caption2,
    )
}
