package com.odinsgolf.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.odinsgolf.data.model.GpsUpdateMode
import com.odinsgolf.data.model.RoundMode
import com.odinsgolf.data.model.ScoringFormat
import com.odinsgolf.ui.screens.CoursePickerScreen
import com.odinsgolf.ui.screens.DistanceScreen
import com.odinsgolf.ui.screens.HandicapScreen
import com.odinsgolf.ui.screens.HistoryScreen
import com.odinsgolf.ui.screens.HoleMapScreen
import com.odinsgolf.ui.screens.HoleSelectorScreen
import com.odinsgolf.ui.screens.RoundSummaryScreen
import com.odinsgolf.ui.screens.ScorecardScreen
import com.odinsgolf.ui.screens.SettingsScreen
import com.odinsgolf.ui.screens.SurveyScreen

private object Routes {
    const val ROUND = "round"      // the 3-screen pager: Distance ⇄ Map ⇄ Card
    const val SETTINGS = "settings"
    const val HOLES = "holes"
    const val HANDICAP = "handicap"
    const val COURSES = "courses"
    const val HISTORY = "history"
    const val SUMMARY = "summary"
    const val SURVEY = "survey"
}

@Composable
fun OdinsGolfApp(vm: RoundViewModel) {
    val nav = rememberSwipeDismissableNavController()
    val state by vm.uiState.collectAsStateWithLifecycle()

    SwipeDismissableNavHost(navController = nav, startDestination = Routes.ROUND) {
        // The on-course core: swipe between Distance, Map and Card.
        composable(Routes.ROUND) { RoundPager(state, vm, nav) }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = state,
                onSetUnits = vm::setUnits,
                onCycleGpsMode = {
                    val modes = GpsUpdateMode.entries
                    vm.setGpsMode(modes[(state.settings.gpsMode.ordinal + 1) % modes.size])
                },
                onSetKeepScreenOn = vm::setKeepScreenOn,
                onCycleRoundMode = {
                    val modes = RoundMode.entries
                    vm.setRoundMode(modes[(state.settings.roundMode.ordinal + 1) % modes.size])
                },
                onCycleScoringFormat = {
                    val formats = ScoringFormat.entries
                    vm.setScoringFormat(formats[(state.settings.scoringFormat.ordinal + 1) % formats.size])
                },
                onCycleAllowance = {
                    vm.setHandicapAllowance(if (state.settings.handicapAllowancePercent >= 100) 95 else 100)
                },
                onOpenHoles = { nav.navigate(Routes.HOLES) },
                onOpenHandicap = { nav.navigate(Routes.HANDICAP) },
                onOpenCourses = { nav.navigate(Routes.COURSES) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onSetDebugGps = vm::setDebugGps,
                onOpenSurvey = { nav.navigate(Routes.SURVEY) },
                onResetRound = vm::resetRound,
            )
        }
        composable(Routes.HOLES) {
            HoleSelectorScreen(
                state = state,
                onSelectHole = { n ->
                    vm.selectHole(n)
                    // Jump straight back to the dashboard on the chosen hole.
                    nav.popBackStack(Routes.ROUND, inclusive = false)
                },
            )
        }
        composable(Routes.HANDICAP) {
            HandicapScreen(
                index = state.settings.handicapIndex,
                course = state.course,
                allowancePercent = state.settings.handicapAllowancePercent,
                onAdjust = { vm.adjustHandicap(it) },
            )
        }
        composable(Routes.COURSES) {
            val courses by vm.courses.collectAsStateWithLifecycle()
            CoursePickerScreen(
                courses = courses,
                selectedFile = state.settings.selectedCourseFile,
                onSelect = { file ->
                    vm.selectCourse(file)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.HISTORY) {
            val rounds by vm.history.collectAsStateWithLifecycle()
            HistoryScreen(
                rounds = rounds,
                onOpenRound = { r ->
                    vm.selectSummary(r)
                    nav.navigate(Routes.SUMMARY)
                },
            )
        }
        composable(Routes.SUMMARY) {
            val round by vm.summaryRound.collectAsStateWithLifecycle()
            RoundSummaryScreen(round = round)
        }
        composable(Routes.SURVEY) {
            SurveyScreen(state = state, onCapture = { vm.captureSurveyPoint(it) })
        }
    }
}

/**
 * Distance ⇄ Card ⇄ Map, swiped horizontally. Card is the first swipe-left (you
 * score every hole); the Map is one further (you go there deliberately). Pushed
 * screens (Settings etc.) sit above this.
 */
@Composable
private fun RoundPager(state: GolfUiState, vm: RoundViewModel, nav: NavController) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> DistanceScreen(
                state = state,
                onPrevHole = vm::prevHole,
                onNextHole = vm::nextHole,
                onOpenMore = { nav.navigate(Routes.SETTINGS) },
            )
            1 -> ScorecardScreen(
                state = state,
                onIncStrokes = vm::incStrokes,
                onDecStrokes = vm::decStrokes,
                onConfirmStrokes = vm::confirmStrokes,
                onIncPutts = vm::incPutts,
                onDecPutts = vm::decPutts,
                onCycleFairway = vm::cycleFairway,
                onToggleGir = vm::toggleGir,
                onNextHole = vm::nextHole,
                onReset = vm::resetRound,
                onExport = { vm.exportRound() },
                onSaveRound = {
                    val ok = vm.saveRoundToHistory()
                    if (ok) nav.navigate(Routes.SUMMARY)
                    ok
                },
            )
            else -> HoleMapScreen(state = state, onToggleMapStyle = vm::toggleMapStyle)
        }
    }
}
