package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import com.odinsgolf.data.model.GpsUpdateMode
import com.odinsgolf.data.model.Units
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.formatHandicap
import com.odinsgolf.ui.components.rotaryScroll
import com.odinsgolf.ui.theme.OdinOnDim

@Composable
fun SettingsScreen(
    state: GolfUiState,
    onSetUnits: (Units) -> Unit,
    onCycleGpsMode: () -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onCycleRoundMode: () -> Unit,
    onCycleScoringFormat: () -> Unit,
    onCycleAllowance: () -> Unit,
    onOpenHoles: () -> Unit,
    onOpenHandicap: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenHistory: () -> Unit,
    onSetDebugGps: (Boolean) -> Unit,
    onOpenSurvey: () -> Unit,
    onResetRound: () -> Unit,
) {
    val s = state.settings
    val listState = rememberScalingLazyListState()
    Scaffold {
        ScalingLazyColumn(state = listState, modifier = Modifier.rotaryScroll(listState)) {
            item { ListHeader { Text("More") } }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onOpenHoles,
                    label = { Text("Jump to hole") },
                    secondaryLabel = { Text("H${state.hole?.displayNumber ?: "—"}") },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onOpenCourses,
                    label = { Text("Course") },
                    secondaryLabel = { Text(state.course?.name ?: "—") },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = { onSetUnits(if (s.units == Units.METERS) Units.YARDS else Units.METERS) },
                    label = { Text("Units") },
                    secondaryLabel = { Text(if (s.units == Units.METERS) "Meters" else "Yards") },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onCycleGpsMode,
                    label = { Text("GPS mode") },
                    secondaryLabel = {
                        val warn = if (s.gpsMode == GpsUpdateMode.PRECISE) " (uses battery)" else ""
                        Text(s.gpsMode.label + warn)
                    },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onCycleRoundMode,
                    label = { Text("Play") },
                    secondaryLabel = { Text(s.roundMode.label) },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onCycleScoringFormat,
                    label = { Text("Format") },
                    secondaryLabel = { Text(s.scoringFormat.label) },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onOpenHandicap,
                    label = { Text("Handicap") },
                    secondaryLabel = { Text(formatHandicap(s.handicapIndex)) },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onCycleAllowance,
                    label = { Text("Hcp allowance") },
                    secondaryLabel = { Text("${s.handicapAllowancePercent}%") },
                )
            }

            item {
                ToggleChip(
                    modifier = Modifier.fillMaxWidth(),
                    checked = s.keepScreenOn,
                    onCheckedChange = onSetKeepScreenOn,
                    label = { Text("Keep screen on") },
                    toggleControl = {
                        androidx.wear.compose.material.Switch(
                            checked = s.keepScreenOn,
                        )
                    },
                )
            }

            item {
                ToggleChip(
                    modifier = Modifier.fillMaxWidth(),
                    checked = s.debugGps,
                    onCheckedChange = onSetDebugGps,
                    label = { Text("Debug GPS info") },
                    toggleControl = {
                        androidx.wear.compose.material.Switch(checked = s.debugGps)
                    },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onOpenHistory,
                    label = { Text("Round history") },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(),
                    onClick = onOpenSurvey,
                    label = { Text("Survey / capture points") },
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = onResetRound,
                    label = { Text("Reset scorecard") },
                )
            }

            item {
                val c = state.course
                Text(
                    text = "Source: " + (c?.attribution?.firstOrNull() ?: "—"),
                    color = OdinOnDim,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Data quality: " + (state.course?.dataQuality?.joinToString(", ") ?: "—"),
                    color = OdinOnDim,
                    style = MaterialTheme.typography.caption3,
                )
            }
        }
    }
}
