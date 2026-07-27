package com.odinsgolf.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
import com.odinsgolf.ui.screens.MyBagScreen
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
    const val BAG = "bag"
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
                onSetAutoWarmGps = vm::setAutoWarmGps,
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
                onOpenBag = { nav.navigate(Routes.BAG) },
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
        composable(Routes.BAG) {
            val bag by vm.bag.collectAsStateWithLifecycle()
            MyBagScreen(
                bag = bag,
                units = state.settings.units,
                onAdjust = vm::adjustClub,
                onSetStyle = vm::setBagStyle,
                onReset = vm::resetBag,
            )
        }
        composable(Routes.SURVEY) {
            val points by vm.surveyPoints.collectAsStateWithLifecycle()
            SurveyScreen(
                state = state,
                holePoints = points.filter { it.holeNumber == state.currentHole },
                hasCapturedPoints = points.isNotEmpty(),
                onCapture = { vm.captureSurveyPoint(it) },
                onDeletePoint = vm::removeSurveyPoint,
                onClearAll = vm::clearSurvey,
            )
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
    val scope = rememberCoroutineScope()
    val bag by vm.bag.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val mark by vm.mark.collectAsStateWithLifecycle()
    val aim by vm.aim.collectAsStateWithLifecycle()
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> DistanceScreen(
                state = state,
                bag = bag,
                weather = weather,
                mark = mark,
                onMark = vm::markBall,
                onClearMark = vm::clearMark,
                onPrevHole = vm::prevHole,
                onNextHole = vm::nextHole,
                onSelectHole = vm::selectHole,
                onSetPin = vm::setPinDepth,
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
                // Advance the hole AND slide back to the Distance screen, so after scoring you
                // land on the next hole's yardage instead of a scrolled-down card.
                onNextHole = {
                    vm.nextHole()
                    scope.launch { pagerState.animateScrollToPage(0) }
                },
                onReset = vm::resetRound,
                onExport = { vm.exportRound() },
                onSaveRound = {
                    val ok = vm.saveRoundToHistory()
                    if (ok) nav.navigate(Routes.SUMMARY)
                    ok
                },
            )
            else -> HoleMapScreen(
                state = state,
                weather = weather,
                aim = aim,
                onSetAim = vm::setAim,
                onClearAim = vm::clearAim,
            )
        }
    }
}
