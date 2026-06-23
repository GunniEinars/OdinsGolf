package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim

@Composable
fun HoleSelectorScreen(
    state: GolfUiState,
    onSelectHole: (Int) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(vignette = { Vignette(VignettePosition.TopAndBottom) }) {
        val range = state.activeRange
        val holes = state.course?.holes.orEmpty().filter { it.number in range }
        val round = state.round
        ScalingLazyColumn(state = listState) {
            item { ListHeader { Text("Select hole") } }
            items(holes) { hole ->
                val entered = round?.holes?.firstOrNull { it.holeNumber == hole.number }?.entered == true
                val isCurrent = hole.number == state.currentHole
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isCurrent) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    onClick = { onSelectHole(hole.number) },
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Hole ${hole.displayNumber}", fontWeight = FontWeight.SemiBold)
                            Text("Par ${hole.par}", color = if (isCurrent) MaterialTheme.colors.onPrimary else OdinOnDim)
                        }
                    },
                    secondaryLabel = {
                        Text(
                            if (entered) "Score entered ✓" else "—",
                            color = if (entered) OdinGreen else OdinOnDim,
                        )
                    },
                )
            }
        }
    }
}
