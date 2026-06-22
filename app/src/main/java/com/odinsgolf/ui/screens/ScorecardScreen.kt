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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.odinsgolf.data.model.FairwayResult
import com.odinsgolf.scoring.Scoring
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim

@Composable
fun ScorecardScreen(
    state: GolfUiState,
    onIncStrokes: () -> Unit,
    onDecStrokes: () -> Unit,
    onIncPutts: () -> Unit,
    onDecPutts: () -> Unit,
    onCycleFairway: () -> Unit,
    onToggleGir: () -> Unit,
    onNextHole: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        val scroll = rememberScrollState()
        val hole = state.hole
        val score = state.currentScore
        val round = state.round
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 14.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Hole ${hole?.displayNumber ?: "—"} · Par ${hole?.par ?: "—"}",
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.SemiBold,
            )

            // Big stroke stepper.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepBtn("–", onDecStrokes)
                Text(
                    text = (score?.strokes ?: 0).let { if (it == 0) "–" else it.toString() },
                    color = OdinGreen,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                )
                StepBtn("+", onIncStrokes)
            }
            val toPar = score?.toPar ?: 0
            Text(
                if (score?.entered == true) "this hole ${Scoring.toParLabel(toPar)}" else "strokes",
                color = OdinOnDim,
                style = MaterialTheme.typography.caption2,
            )

            Spacer(Modifier.height(8.dp))

            // Putts stepper (compact).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Putts", color = OdinOnDim, style = MaterialTheme.typography.caption2)
                CompactChip(label = { Text("–") }, onClick = onDecPutts)
                Text((score?.putts ?: 0).toString(), style = MaterialTheme.typography.title3)
                CompactChip(label = { Text("+") }, onClick = onIncPutts)
            }

            Spacer(Modifier.height(6.dp))

            // Fairway (par 4/5 only) + GIR toggles.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if ((hole?.par ?: 0) >= 4) {
                    val fwLabel = when (score?.fairway) {
                        FairwayResult.HIT -> "FW ✓"
                        FairwayResult.MISS -> "FW ✗"
                        else -> "FW –"
                    }
                    CompactChip(label = { Text(fwLabel) }, onClick = onCycleFairway)
                }
                CompactChip(
                    label = { Text(if (score?.gir == true) "GIR ✓" else "GIR –") },
                    onClick = onToggleGir,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Running totals.
            if (round != null) {
                SummaryRow("Out (1–9)", round.strokesForRange(1..9), round.parForRange(1..9))
                SummaryRow("In (10–18)", round.strokesForRange(10..18), round.parForRange(10..18))
                Text(
                    "Total ${round.totalStrokes}  ·  ${Scoring.toParLabel(round.toPar)}",
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Stableford ${Scoring.totalStableford(round)} pts  ·  HCP ${round.playerHandicap}",
                    color = OdinOnDim,
                    style = MaterialTheme.typography.caption2,
                )
                if (round.playerHandicap > 0) {
                    Text(
                        "Net ${Scoring.toParLabel(Scoring.netToPar(round))}",
                        color = OdinOnDim,
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            CompactChip(label = { Text("Next hole ›") }, onClick = onNextHole)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(label = { Text("Export") }, onClick = onExport)
                CompactChip(label = { Text("Reset") }, onClick = onReset)
            }
        }
    }
}

@Composable
private fun StepBtn(symbol: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.secondaryButtonColors()) {
        Text(symbol, fontSize = 24.sp)
    }
}

@Composable
private fun SummaryRow(label: String, strokes: Int, par: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = OdinOnDim, style = MaterialTheme.typography.caption2)
        Text(
            if (strokes == 0) "–" else "$strokes (${Scoring.toParLabel(strokes - par)})",
            style = MaterialTheme.typography.caption1,
        )
    }
}
