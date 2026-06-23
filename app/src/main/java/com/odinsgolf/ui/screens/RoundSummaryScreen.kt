package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.data.MediaExport
import com.odinsgolf.data.RoundCardRenderer
import com.odinsgolf.data.model.HoleScore
import com.odinsgolf.data.model.Round
import com.odinsgolf.scoring.Scoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.odinsgolf.ui.components.rotaryScroll
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim
import com.odinsgolf.ui.theme.OdinRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoundSummaryScreen(round: Round?) {
    if (round == null) {
        Scaffold { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No round") } }
        return
    }
    val scroll = rememberScrollState()
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .rotaryScroll(scroll)
                .padding(horizontal = 14.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(round.courseName, style = MaterialTheme.typography.title3, fontWeight = FontWeight.SemiBold)
            Text(fmt.format(Date(round.startedEpochMillis)), color = OdinOnDim, style = MaterialTheme.typography.caption3)

            Spacer(Modifier.height(6.dp))
            Text(Scoring.toParLabel(round.toPar), color = OdinGreen, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text("to par", color = OdinOnDim, style = MaterialTheme.typography.caption3)

            Spacer(Modifier.height(4.dp))
            Text(
                "${round.totalStrokes} strokes · ${Scoring.totalStableford(round)} pts",
                style = MaterialTheme.typography.caption1,
            )
            if (round.handicapIndex > 0) {
                Text(
                    "Net ${Scoring.toParLabel(Scoring.netToPar(round))} · HCP ${"%.1f".format(Locale.US, round.handicapIndex)}",
                    color = OdinOnDim,
                    style = MaterialTheme.typography.caption2,
                )
            }

            Spacer(Modifier.height(8.dp))
            NineRow("OUT", 1..9, round)
            Spacer(Modifier.height(3.dp))
            NineRow("IN", 10..18, round)

            Spacer(Modifier.height(12.dp))
            CompactChip(
                label = { Text("Save image") },
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val bmp = RoundCardRenderer.render(round)
                            MediaExport.saveToGallery(context, bmp, "OdinsGolf_${round.startedEpochMillis}")
                        }
                        msg = if (ok) "Saved to Gallery ✓" else "Save failed"
                    }
                },
            )
            if (msg.isNotEmpty()) {
                Text(msg, color = OdinGreen, style = MaterialTheme.typography.caption2)
            }
        }
    }
}

@Composable
private fun NineRow(label: String, range: IntRange, round: Round) {
    val holes = round.holes.filter { it.holeNumber in range }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(label, color = OdinOnDim, style = MaterialTheme.typography.caption3, modifier = Modifier.width(24.dp))
        holes.forEach { hs ->
            Text(
                text = when {
                    hs.pickedUp -> "P"
                    hs.entered -> hs.strokes.toString()
                    else -> "–"
                },
                color = cellColor(hs),
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = round.strokesForRange(range).let { if (it == 0) "–" else it.toString() },
            style = MaterialTheme.typography.caption1,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
        )
    }
}

private fun cellColor(s: HoleScore): Color = when {
    s.pickedUp -> OdinAmber
    !s.entered -> OdinOnDim
    s.strokes < s.par -> OdinGreen
    s.strokes == s.par -> Color.White
    s.strokes == s.par + 1 -> OdinAmber
    else -> OdinRed
}
