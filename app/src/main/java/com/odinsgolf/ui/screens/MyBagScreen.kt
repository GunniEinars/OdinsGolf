package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.data.model.Bag
import com.odinsgolf.data.model.Units
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.components.rotaryScroll
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim

/**
 * Edit the player's club carries (5 m per tap). Feeds the offline caddie's club advice.
 * Stored in metres; shown in the chosen units.
 */
@Composable
fun MyBagScreen(
    bag: Bag,
    units: Units,
    onAdjust: (String, Int) -> Unit,
    onReset: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold {
        ScalingLazyColumn(state = listState, modifier = Modifier.rotaryScroll(listState)) {
            item { ListHeader { Text("My bag") } }
            item {
                Text(
                    "Your conservative carry per club — the distance the caddie plans with.",
                    color = OdinOnDim,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            items(bag.clubs) { club ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(club.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatDistance(club.carryMeters.toDouble(), units)} ${units.suffix}",
                            color = OdinGreen,
                            style = MaterialTheme.typography.caption1,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactChip(
                            colors = ChipDefaults.secondaryChipColors(),
                            onClick = { onAdjust(club.name, -5) },
                            label = { Text("−") },
                        )
                        CompactChip(
                            colors = ChipDefaults.secondaryChipColors(),
                            onClick = { onAdjust(club.name, 5) },
                            label = { Text("+") },
                        )
                    }
                }
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onReset,
                    label = { Text("Reset to defaults") },
                )
            }
        }
    }
}
