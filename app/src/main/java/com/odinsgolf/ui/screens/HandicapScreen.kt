package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.scoring.Scoring
import com.odinsgolf.ui.components.formatHandicap
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim

/** Full-screen handicap-index editor: ±1 and ±0.1 to reach e.g. 15.7 quickly. */
@Composable
fun HandicapScreen(
    index: Double,
    onAdjust: (Double) -> Unit,
) {
    Scaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Handicap index", color = OdinOnDim, style = MaterialTheme.typography.caption1)
            Text(
                formatHandicap(index),
                color = OdinGreen,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "playing ${Scoring.playingHandicap(index)}",
                color = OdinOnDim,
                style = MaterialTheme.typography.caption2,
            )
            Spacer(Modifier.height(10.dp))
            // Coarse row: whole strokes.
            StepRow("−1", "+1") { onAdjust(it.toDouble()) }
            Spacer(Modifier.height(6.dp))
            // Fine row: tenths.
            StepRow("−0.1", "+0.1") { onAdjust(it * 0.1) }
        }
    }
}

@Composable
private fun StepRow(minusLabel: String, plusLabel: String, onStep: (Int) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onStep(-1) }, colors = ButtonDefaults.secondaryButtonColors()) {
            Text(minusLabel, fontSize = 16.sp)
        }
        Button(onClick = { onStep(1) }, colors = ButtonDefaults.secondaryButtonColors()) {
            Text(plusLabel, fontSize = 16.sp)
        }
    }
}
