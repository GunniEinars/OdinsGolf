package com.odinsgolf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.odinsgolf.data.model.GpsUpdateMode
import com.odinsgolf.data.model.RoundMode
import com.odinsgolf.ui.screens.CoursePickerScreen
import com.odinsgolf.ui.screens.DistanceScreen
import com.odinsgolf.ui.screens.HandicapScreen
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
    const val HANDICAP = "handicap"
    const val COURSES = "courses"
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
                onCycleRoundMode = {
                    val modes = RoundMode.entries
                    vm.setRoundMode(modes[(state.settings.roundMode.ordinal + 1) % modes.size])
                },
                onOpenHandicap = { nav.navigate(Routes.HANDICAP) },
                onOpenCourses = { nav.navigate(Routes.COURSES) },
                onSetDebugGps = vm::setDebugGps,
                onOpenSurvey = { nav.navigate(Routes.SURVEY) },
                onResetRound = vm::resetRound,
            )
        }
        composable(Routes.HANDICAP) {
            HandicapScreen(index = state.settings.handicapIndex, onAdjust = { vm.adjustHandicap(it) })
        }
        composable(Routes.COURSES) {
            CoursePickerScreen(
                courses = vm.courses,
                selectedFile = state.settings.selectedCourseFile,
                onSelect = { file ->
                    vm.selectCourse(file)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.SURVEY) {
            SurveyScreen(state = state, onCapture = { vm.captureSurveyPoint(it) })
        }
    }
}
