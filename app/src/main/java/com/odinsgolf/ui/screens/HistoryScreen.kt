package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.data.model.Round
import com.odinsgolf.scoring.Scoring
import com.odinsgolf.ui.components.rotaryScroll
import com.odinsgolf.ui.theme.OdinOnDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(rounds: List<Round>, onOpenRound: (Round) -> Unit) {
    val listState = rememberScalingLazyListState()
    val fmt = remember { SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault()) }
    Scaffold {
        ScalingLazyColumn(state = listState, modifier = Modifier.rotaryScroll(listState)) {
            item { ListHeader { Text("Round history") } }
            if (rounds.isEmpty()) {
                item {
                    Text(
                        "No saved rounds yet.\nSave one from the scorecard.",
                        color = OdinOnDim,
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }
            items(rounds) { r ->
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = { onOpenRound(r) },
                    label = { Text("${r.courseName} · ${Scoring.toParLabel(r.toPar)}") },
                    secondaryLabel = {
                        Text(
                            "${r.totalStrokes} strokes · ${Scoring.totalStableford(r)} pts\n" +
                                fmt.format(Date(r.startedEpochMillis)),
                        )
                    },
                )
            }
        }
    }
}
