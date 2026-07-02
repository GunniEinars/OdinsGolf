package com.odinsgolf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odinsgolf.data.AppSettings
import com.odinsgolf.data.CourseRepository
import com.odinsgolf.data.HistoryRepository
import com.odinsgolf.data.ScorecardRepository
import com.odinsgolf.data.SettingsRepository
import com.odinsgolf.data.SurveyKind
import com.odinsgolf.data.SurveyPoint
import com.odinsgolf.data.SurveyRepository
import com.odinsgolf.data.model.Course
import com.odinsgolf.data.model.FairwayResult
import com.odinsgolf.data.model.GpsState
import com.odinsgolf.data.model.GpsUpdateMode
import com.odinsgolf.data.model.HoleScore
import com.odinsgolf.data.model.MapStyle
import com.odinsgolf.data.model.Round
import com.odinsgolf.data.model.RoundMode
import com.odinsgolf.data.model.ScoringFormat
import com.odinsgolf.data.model.Units
import com.odinsgolf.location.LocationEngine
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.os.SystemClock

/** Full UI snapshot for the app. */
data class GolfUiState(
    val loading: Boolean = true,
    val course: Course? = null,
    val loadError: String? = null,
    val settings: AppSettings = AppSettings(),
    val gps: GpsState = GpsState(),
    val round: Round? = null,
    val nowElapsed: Long = SystemClock.elapsedRealtime(),
) {
    val currentHole: Int get() = settings.currentHole
    val hole get() = course?.holeByNumber(currentHole)
    val currentScore: HoleScore? get() = round?.holes?.firstOrNull { it.holeNumber == currentHole }

    /** Hole-number range for the active round mode. */
    val activeRange: IntRange
        get() = settings.roundMode.range(course?.holes?.size ?: 18)
}

class RoundViewModel(app: Application) : AndroidViewModel(app) {

    private val courseRepo = CourseRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val scoreRepo = ScorecardRepository(app)
    private val surveyRepo = SurveyRepository(app)
    private val historyRepo = HistoryRepository(app)
    private val location = LocationEngine(app)

    val gpsState: StateFlow<GpsState> get() = location.state

    private val historyFlow = MutableStateFlow(historyRepo.load())
    /** Saved rounds (newest first). */
    val history: StateFlow<List<Round>> = historyFlow.asStateFlow()

    private val summaryFlow = MutableStateFlow<Round?>(null)
    /** Round currently shown on the summary card (active save or a history item). */
    val summaryRound: StateFlow<Round?> = summaryFlow.asStateFlow()

    fun selectSummary(round: Round) { summaryFlow.value = round }

    private val courseFlow = MutableStateFlow<Course?>(null)
    private val loadErrorFlow = MutableStateFlow<String?>(null)
    private val roundFlow = MutableStateFlow<Round?>(null)
    private val tickFlow = MutableStateFlow(SystemClock.elapsedRealtime())

    // Combine course + error so a load failure reliably triggers a UI emission.
    private val courseLoad: Flow<Pair<Course?, String?>> =
        combine(courseFlow, loadErrorFlow) { course, error -> course to error }

    // Hole-map base layer is a per-outing choice held in memory, deliberately NOT
    // persisted: the app always opens on the reliable, offline vector hole view, so an
    // accidental satellite tap can never carry over to the next session or leave you on
    // a blank satellite map with no signal. Tapping still switches it for this outing.
    private val mapStyleFlow = MutableStateFlow(MapStyle.VECTOR)
    private val settingsFlow: Flow<AppSettings> =
        combine(settingsRepo.settings, mapStyleFlow) { s, style -> s.copy(mapStyle = style) }

    val uiState: StateFlow<GolfUiState> =
        combine(
            settingsFlow,
            courseLoad,
            location.state,
            roundFlow,
            tickFlow,
        ) { settings, load, gps, round, tick ->
            val (course, error) = load
            GolfUiState(
                loading = course == null && error == null,
                course = course,
                loadError = error,
                settings = settings,
                gps = gps,
                round = round,
                nowElapsed = tick,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GolfUiState())

    /** Bundled courses available in the picker (assets don't change at runtime). */
    val courses: List<CourseRepository.CourseSummary> = courseRepo.listCourses()

    init {
        // Load (and reload) the course whenever the selected file changes.
        viewModelScope.launch {
            settingsRepo.settings
                .map { it.selectedCourseFile }
                .distinctUntilChanged()
                .collect { loadCourse(it) }
        }
        // Slow ticker so "last updated" / stale state refreshes without new fixes.
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                tickFlow.value = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun loadCourse(file: String) {
        when (val res = courseRepo.loadCourse(file)) {
            is CourseRepository.LoadResult.Success -> {
                val overlaid = surveyRepo.overlay(res.course, surveyRepo.load(res.course.id))
                courseFlow.value = overlaid
                loadErrorFlow.value = null
                ensureRound(overlaid)
            }
            is CourseRepository.LoadResult.Failure -> {
                courseFlow.value = null
                loadErrorFlow.value = res.message
            }
        }
    }

    private fun ensureRound(course: Course) {
        val existing = scoreRepo.loadActiveRound()
        roundFlow.value = if (existing != null && existing.courseId == course.id) {
            existing
        } else {
            scoreRepo.newRound(course, currentHandicap(), currentAllowance()).also { scoreRepo.saveActiveRound(it) }
        }
    }

    private fun currentHandicap(): Double = uiState.value.settings.handicapIndex
    private fun currentAllowance(): Int = uiState.value.settings.handicapAllowancePercent

    /** Hole-number range allowed by the current round mode. */
    private fun activeRange(): IntRange {
        val total = courseFlow.value?.holes?.size ?: 18
        return uiState.value.settings.roundMode.range(total)
    }

    // ---- Lifecycle ----------------------------------------------------------

    fun onResume() {
        val mode = uiState.value.settings.gpsMode
        location.start(mode)
        viewModelScope.launch { location.requestBurst() }
    }

    fun onPause() {
        location.pause()
    }

    fun restartLocation() {
        location.start(uiState.value.settings.gpsMode)
    }

    // ---- Hole navigation ----------------------------------------------------

    fun nextHole() = changeHole(uiState.value.currentHole + 1)
    fun prevHole() = changeHole(uiState.value.currentHole - 1)
    fun selectHole(n: Int) = changeHole(n)

    private fun changeHole(n: Int) {
        val range = activeRange()
        val clamped = n.coerceIn(range.first, range.last)
        viewModelScope.launch { settingsRepo.setCurrentHole(clamped) }
    }

    // ---- Scoring ------------------------------------------------------------

    // The stepper opens on the hole's par (shown as a hint until confirmed), so a
    // par/near-par score is one or two taps. "+" / "-" nudge from there. Pressing
    // "-" past 1 (the slot "before 0") marks the hole picked up.
    fun incStrokes() = mutateScore {
        when {
            it.pickedUp -> it.copy(pickedUp = false, strokes = 1)
            it.strokes == 0 -> it.copy(strokes = (it.par + 1).coerceIn(1, 20)) // from par hint
            else -> it.copy(strokes = (it.strokes + 1).coerceAtMost(20))
        }
    }
    fun decStrokes() = mutateScore {
        when {
            it.pickedUp -> it // already at the far-left slot
            it.strokes == 0 -> it.copy(strokes = (it.par - 1).coerceAtLeast(1)) // from par hint
            it.strokes <= 1 -> it.copy(strokes = 0, pickedUp = true) // "left before 0" = pick up
            else -> it.copy(strokes = it.strokes - 1)
        }
    }
    /** Commit the displayed par hint as the score (player made par). */
    fun confirmStrokes() = mutateScore {
        if (it.strokes == 0 && !it.pickedUp) it.copy(strokes = it.par) else it
    }
    fun incPutts() = mutateScore { it.copy(putts = (it.putts + 1).coerceAtMost(10)) }
    fun decPutts() = mutateScore { it.copy(putts = (it.putts - 1).coerceAtLeast(0)) }
    fun cycleFairway() = mutateScore {
        val next = when (it.fairway) {
            FairwayResult.NONE -> FairwayResult.HIT
            FairwayResult.HIT -> FairwayResult.MISS
            FairwayResult.MISS -> FairwayResult.NONE
        }
        it.copy(fairway = next)
    }
    fun toggleGir() = mutateScore { it.copy(gir = !it.gir) }

    private fun mutateScore(transform: (HoleScore) -> HoleScore) {
        val round = roundFlow.value ?: return
        val hole = uiState.value.currentHole
        val updated = round.copy(
            holes = round.holes.map { if (it.holeNumber == hole) transform(it) else it },
        )
        roundFlow.value = updated
        scoreRepo.saveActiveRound(updated)
    }

    fun resetRound() {
        val course = courseFlow.value ?: return
        val fresh = scoreRepo.newRound(course, currentHandicap(), currentAllowance())
        roundFlow.value = fresh
        scoreRepo.saveActiveRound(fresh)
    }

    fun exportRound(): String? = roundFlow.value?.let { scoreRepo.exportRound(it) }

    /** Manually save the current round to history and queue it for the summary card. */
    fun saveRoundToHistory(): Boolean {
        val round = roundFlow.value ?: return false
        if (round.enteredHoles.isEmpty()) return false
        historyFlow.value = historyRepo.add(round)
        summaryFlow.value = round
        return true
    }

    fun deleteHistoryRound(startedEpochMillis: Long) {
        historyFlow.value = historyRepo.delete(startedEpochMillis)
    }

    // ---- Settings -----------------------------------------------------------

    fun setUnits(units: Units) = viewModelScope.launch { settingsRepo.setUnits(units) }
    fun toggleUnits() {
        val next = if (uiState.value.settings.units == Units.METERS) Units.YARDS else Units.METERS
        setUnits(next)
    }
    fun setGpsMode(mode: GpsUpdateMode) = viewModelScope.launch {
        settingsRepo.setGpsMode(mode)
        location.start(mode)
    }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { settingsRepo.setKeepScreenOn(value) }

    /** Toggle the hole-map base layer for this outing (in memory; resets to vector next launch). */
    fun toggleMapStyle() {
        mapStyleFlow.update { if (it == MapStyle.VECTOR) MapStyle.SATELLITE else MapStyle.VECTOR }
    }

    /** Adjust the decimal handicap index by [delta] (e.g. +0.1, -1.0), clamped 0..54. */
    fun adjustHandicap(delta: Double) = viewModelScope.launch {
        val next = ((uiState.value.settings.handicapIndex + delta) * 10).roundToInt() / 10.0
        val clamped = next.coerceIn(0.0, 54.0)
        settingsRepo.setHandicapIndex(clamped)
        // Reflect the new index in the active round's stroke allocation.
        roundFlow.update { it?.copy(handicapIndex = clamped) }
        roundFlow.value?.let { scoreRepo.saveActiveRound(it) }
    }

    fun setRoundMode(mode: RoundMode) = viewModelScope.launch {
        settingsRepo.setRoundMode(mode)
        // Jump to the first hole of the new range so navigation stays in bounds.
        val total = courseFlow.value?.holes?.size ?: 18
        settingsRepo.setCurrentHole(mode.range(total).first)
    }

    fun setScoringFormat(format: ScoringFormat) = viewModelScope.launch {
        settingsRepo.setScoringFormat(format)
    }

    /** Set the WHS handicap allowance (percent) and reflect it in the active round. */
    fun setHandicapAllowance(percent: Int) = viewModelScope.launch {
        val clamped = percent.coerceIn(50, 100)
        settingsRepo.setHandicapAllowance(clamped)
        roundFlow.update { it?.copy(handicapAllowancePercent = clamped) }
        roundFlow.value?.let { scoreRepo.saveActiveRound(it) }
    }

    fun setDebugGps(value: Boolean) = viewModelScope.launch { settingsRepo.setDebugGps(value) }

    /** Switch the active course. Resets to hole 1 of the current round mode. */
    fun selectCourse(file: String) = viewModelScope.launch {
        if (file == uiState.value.settings.selectedCourseFile) return@launch
        settingsRepo.setCourseFile(file)
        val total = 18
        settingsRepo.setCurrentHole(uiState.value.settings.roundMode.range(total).first)
    }

    // ---- Survey -------------------------------------------------------------

    /** Returns true if a point was captured (course loaded and a GPS fix exists). */
    fun captureSurveyPoint(kind: SurveyKind, label: String = ""): Boolean {
        val course = courseFlow.value ?: return false
        val gps = location.state.value
        val p = gps.point ?: return false
        val point = SurveyPoint(
            holeNumber = uiState.value.currentHole,
            kind = kind,
            lat = p.lat,
            lon = p.lon,
            accuracyMeters = gps.accuracyMeters,
            label = label,
            epochMillis = System.currentTimeMillis(),
        )
        val data = surveyRepo.add(course.id, point)
        // Re-overlay so captured points show immediately. Reload the selected base course first.
        viewModelScope.launch {
            when (val res = courseRepo.loadCourse(uiState.value.settings.selectedCourseFile)) {
                is CourseRepository.LoadResult.Success ->
                    courseFlow.value = surveyRepo.overlay(res.course, data)
                else -> {}
            }
        }
        return true
    }

    fun surveyExportPath(): String =
        courseFlow.value?.let { surveyRepo.exportPath(it.id) } ?: ""

    fun clearSurvey() {
        val course = courseFlow.value ?: return
        surveyRepo.clear(course.id)
        loadCourse(uiState.value.settings.selectedCourseFile)
    }

    override fun onCleared() {
        location.stop()
        super.onCleared()
    }
}
