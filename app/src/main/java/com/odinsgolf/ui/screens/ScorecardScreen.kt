package com.odinsgolf.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
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
import com.odinsgolf.data.model.RoundMode
import com.odinsgolf.data.model.ScoringFormat
import com.odinsgolf.scoring.Scoring
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.formatHandicap
import com.odinsgolf.ui.components.rotaryScroll
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScorecardScreen(
    state: GolfUiState,
    onIncStrokes: () -> Unit,
    onDecStrokes: () -> Unit,
    onConfirmStrokes: () -> Unit,
    onIncPutts: () -> Unit,
    onDecPutts: () -> Unit,
    onCycleFairway: () -> Unit,
    onToggleGir: () -> Unit,
    onNextHole: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onSaveRound: () -> Boolean,
) {
    Scaffold(timeText = { TimeText() }) {
        val scroll = rememberScrollState()
        val saveMsg = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        // Two-tap guard on Reset so an accidental tap can't wipe the round card.
        val resetArmed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        LaunchedEffect(resetArmed.value) {
            if (resetArmed.value) { delay(3000); resetArmed.value = false }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val hole = state.hole
        val score = state.currentScore
        val round = state.round
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .rotaryScroll(scroll)
                .padding(horizontal = 14.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Hole ${hole?.displayNumber ?: "—"} · Par ${hole?.par ?: "—"}",
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.SemiBold,
            )
            // Net-play cue: how many handicap strokes you receive on this hole.
            val shotsHere = round?.let { Scoring.strokesReceived(Scoring.playingHandicap(it), hole?.strokeIndex) } ?: 0
            if (shotsHere > 0) {
                Text(
                    "+$shotsHere handicap ${if (shotsHere == 1) "stroke" else "strokes"} here",
                    color = OdinAmber,
                    style = MaterialTheme.typography.caption2,
                )
            }

            // Big stroke stepper. Opens on par (dim hint) until entered/confirmed.
            val pickedUp = score?.pickedUp == true
            val entered = score?.entered == true
            val display = when {
                pickedUp -> "PU"
                entered -> score!!.strokes.toString()
                else -> (hole?.par ?: 0).toString()
            }
            val displayColor = when {
                pickedUp -> OdinAmber
                entered -> OdinGreen
                else -> OdinOnDim
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepBtn("–", onDecStrokes)
                Text(
                    text = display,
                    color = displayColor,
                    fontSize = if (pickedUp) 30.sp else 44.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onConfirmStrokes),
                )
                StepBtn("+", onIncStrokes)
            }
            val toPar = score?.toPar ?: 0
            Text(
                when {
                    pickedUp -> "picked up · 0 pts"
                    entered -> "this hole ${Scoring.toParLabel(toPar)}"
                    else -> "tap to keep par · – past 1 = pick up"
                },
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

            // Running totals — only the nines relevant to the round mode.
            if (round != null) {
                val mode = state.settings.roundMode
                if (mode != RoundMode.BACK_9) {
                    SummaryRow("Out (1–9)", round.strokesForRange(1..9), round.parForRange(1..9))
                }
                if (mode != RoundMode.FRONT_9) {
                    SummaryRow("In (10–18)", round.strokesForRange(10..18), round.parForRange(10..18))
                }
                Text(
                    "Total ${round.totalStrokes}  ·  ${Scoring.toParLabel(round.toPar)}",
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.SemiBold,
                )
                // Headline the result for the chosen format: Stableford points, or net
                // stroke play (net total + net to par). Gross total above stays either way.
                val playing = Scoring.playingHandicap(round)
                when (state.settings.scoringFormat) {
                    ScoringFormat.STABLEFORD -> Text(
                        "Stableford ${Scoring.totalStableford(round)} pts",
                        color = OdinGreen,
                        style = MaterialTheme.typography.title3,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ScoringFormat.STROKE_PLAY -> Text(
                        "Net ${Scoring.totalNet(round)}  ·  ${Scoring.toParLabel(Scoring.netToPar(round))}",
                        color = OdinGreen,
                        style = MaterialTheme.typography.title3,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "HCP ${formatHandicap(round.handicapIndex)} · plays $playing",
                    color = OdinOnDim,
                    style = MaterialTheme.typography.caption2,
                )
            }

            Spacer(Modifier.height(10.dp))
            CompactChip(label = { Text("Next hole ›") }, onClick = onNextHole)
            Spacer(Modifier.height(4.dp))
            CompactChip(
                label = { Text("Save round") },
                onClick = { saveMsg.value = if (onSaveRound()) "Saved ✓" else "No score yet" },
            )
            if (saveMsg.value.isNotEmpty()) {
                Text(saveMsg.value, color = OdinGreen, style = MaterialTheme.typography.caption2)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(
                    label = { Text("Save card") },
                    onClick = {
                        val r = round
                        if (r == null || r.enteredHoles.isEmpty()) {
                            saveMsg.value = "No score yet"
                        } else {
                            saveMsg.value = "Saving…"
                            scope.launch {
                                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    onExport() // JSON backup pullable via adb
                                    val bmp = com.odinsgolf.data.RoundCardRenderer.render(r)
                                    com.odinsgolf.data.MediaExport.saveToGallery(
                                        context, bmp, "OdinsGolf_${r.startedEpochMillis}",
                                    )
                                }
                                saveMsg.value = if (ok) "Card saved to Gallery ✓" else "Save failed"
                            }
                        }
                    },
                )
                CompactChip(
                    label = { Text(if (resetArmed.value) "Confirm reset?" else "Reset") },
                    onClick = {
                        if (resetArmed.value) {
                            onReset(); resetArmed.value = false; saveMsg.value = ""
                        } else {
                            resetArmed.value = true
                        }
                    },
                )
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
