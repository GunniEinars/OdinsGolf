package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/** Handicap-index editor: a single centered row of −1 / −0.1 / +0.1 / +1. */
@Composable
fun HandicapScreen(
    index: Double,
    onAdjust: (Double) -> Unit,
) {
    Scaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Handicap", color = OdinOnDim, style = MaterialTheme.typography.caption1)
            Text(
                formatHandicap(index),
                color = OdinGreen,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "playing ${Scoring.playingHandicap(index)}",
                color = OdinOnDim,
                style = MaterialTheme.typography.caption2,
            )
            Spacer(Modifier.height(12.dp))
            // All four controls on one row, sitting in the reachable middle band.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepButton("+1") { onAdjust(1.0) }
                StepButton("−1") { onAdjust(-1.0) }
                StepButton("+.1") { onAdjust(0.1) }
                StepButton("−.1") { onAdjust(-0.1) }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.size(46.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
