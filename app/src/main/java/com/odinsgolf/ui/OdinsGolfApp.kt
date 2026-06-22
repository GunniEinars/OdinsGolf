package com.odinsgolf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.odinsgolf.data.model.GpsUpdateMode
import com.odinsgolf.ui.screens.DistanceScreen
import com.odinsgolf.ui.screens.HoleMapScreen
import com.odinsgolf.ui.screens.HoleSelectorScreen
import com.odinsgolf.ui.screens.ScorecardScreen
import com.odinsgolf.ui.screens.SettingsScreen
import com.odinsgolf.ui.screens.SurveyScreen

private object Routes {
    const val DISTANCE = "distance"
    const val MAP = "map"
    const val SCORECARD = "scorecard"
    const val HOLES = "holes"
    const val SETTINGS = "settings"
    const val SURVEY = "survey"
}

@Composable
fun OdinsGolfApp(vm: RoundViewModel) {
    val nav = rememberSwipeDismissableNavController()
    val state by vm.uiState.collectAsStateWithLifecycle()

    SwipeDismissableNavHost(navController = nav, startDestination = Routes.DISTANCE) {
        composable(Routes.DISTANCE) {
            DistanceScreen(
                state = state,
                onPrevHole = vm::prevHole,
                onNextHole = vm::nextHole,
                onToggleUnits = vm::toggleUnits,
                onOpenMap = { nav.navigate(Routes.MAP) },
                onOpenScorecard = { nav.navigate(Routes.SCORECARD) },
                onOpenHoles = { nav.navigate(Routes.HOLES) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.MAP) { HoleMapScreen(state = state) }
        composable(Routes.SCORECARD) {
            ScorecardScreen(
                state = state,
                onIncStrokes = vm::incStrokes,
                onDecStrokes = vm::decStrokes,
                onIncPutts = vm::incPutts,
                onDecPutts = vm::decPutts,
                onCycleFairway = vm::cycleFairway,
                onToggleGir = vm::toggleGir,
                onNextHole = vm::nextHole,
                onReset = vm::resetRound,
                onExport = { vm.exportRound() },
            )
        }
        composable(Routes.HOLES) {
            HoleSelectorScreen(
                state = state,
                onSelectHole = { n ->
                    vm.selectHole(n)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = state,
                onSetUnits = vm::setUnits,
                onCycleGpsMode = {
                    val modes = GpsUpdateMode.entries
                    val next = modes[(state.settings.gpsMode.ordinal + 1) % modes.size]
                    vm.setGpsMode(next)
                },
                onSetKeepScreenOn = vm::setKeepScreenOn,
                onIncHandicap = { vm.setHandicap((state.settings.handicap + 1).let { if (it > 54) 0 else it }) },
                onDecHandicap = { vm.setHandicap((state.settings.handicap - 1).coerceAtLeast(0)) },
                onSetDebugGps = vm::setDebugGps,
                onOpenSurvey = { nav.navigate(Routes.SURVEY) },
                onResetRound = vm::resetRound,
            )
        }
        composable(Routes.SURVEY) {
            SurveyScreen(state = state, onCapture = { vm.captureSurveyPoint(it) })
        }
    }
}
