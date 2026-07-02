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
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.data.model.GpsUpdateMode
import com.odinsgolf.data.model.HoleScore
import com.odinsgolf.data.model.MapStyle
import com.odinsgolf.data.model.Round
import com.odinsgolf.data.model.RoundMode
import com.odinsgolf.data.model.ScoringFormat
import com.odinsgolf.data.model.Units
import com.odinsgolf.location.LocationEngine
import com.odinsgolf.location.effectiveStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    /** GPS status with interval-aware staleness applied (the mode's threshold, not a flat 30 s). */
    val gpsStatus: GpsStatus get() = gps.effectiveStatus(nowElapsed, settings.gpsMode.staleAfterMillis)

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

    // Loaded off the main thread in init (parsing history JSON at construction would
    // block startup on the watch).
    private val historyFlow = MutableStateFlow<List<Round>>(emptyList())
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

    // Bundled courses for the picker, loaded off the main thread in init. Parsing every
    // course JSON (~120 KB each) on the UI thread froze the watch (same class of bug as the
    // old startup hang), so it never runs on the main thread.
    private val coursesFlow = MutableStateFlow<List<CourseRepository.CourseSummary>>(emptyList())
    val courses: StateFlow<List<CourseRepository.CourseSummary>> = coursesFlow.asStateFlow()

    init {
        // Read saved history + the course-picker list off the main thread (JSON parse
        // would block launch / freeze the picker on the watch).
        viewModelScope.launch(Dispatchers.Default) { historyFlow.value = historyRepo.load() }
        viewModelScope.launch(Dispatchers.Default) { coursesFlow.value = courseRepo.listCourses() }
        // Load (and reload) the course whenever the selected file changes. The heavy
        // parse happens inside loadCourse on a background dispatcher.
        viewModelScope.launch {
            settingsRepo.settings
                .map { it.selectedCourseFile }
                .distinctUntilChanged()
                .collect { loadCourse(it) }
        }
        // Persist the active round off the main thread whenever it changes. One collector
        // serialises the writes (no concurrent writeText) and StateFlow conflation means only
        // the latest state is written — so rapid score taps never race the file or jank the UI.
        viewModelScope.launch(Dispatchers.Default) {
            roundFlow.collect { r -> if (r != null) runCatching { scoreRepo.saveActiveRound(r) } }
        }
        // The stale/age ticker is started on resume and stopped on pause (see onResume/onPause),
        // so it never wakes the CPU while the wrist is down.
    }

    // Refreshes "age"/stale display every 5 s while the screen is on. Off while paused.
    private var tickerJob: Job? = null
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                tickFlow.value = SystemClock.elapsedRealtime()
            }
        }
    }
    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // Parses course JSON (~120 KB) + survey/active-round files off the main thread; only
    // the resulting StateFlow updates happen back on the caller's context. Doing this on
    // the main thread blocked startup for ~10 s on the watch and the OS killed the app.
    private suspend fun loadCourse(file: String) {
        val outcome = withContext(Dispatchers.Default) {
            when (val res = courseRepo.loadCourse(file)) {
                is CourseRepository.LoadResult.Success -> {
                    val overlaid = surveyRepo.overlay(res.course, surveyRepo.load(res.course.id))
                    Triple<Course?, String?, Round?>(overlaid, null, resolveRound(overlaid))
                }
                is CourseRepository.LoadResult.Failure ->
                    Triple<Course?, String?, Round?>(null, res.message, null)
            }
        }
        courseFlow.value = outcome.first
        loadErrorFlow.value = outcome.second
        outcome.third?.let { roundFlow.value = it }
    }

    /** Load the persisted active round, or start a fresh one. Does file I/O — call off-main. */
    private fun resolveRound(course: Course): Round {
        val existing = scoreRepo.loadActiveRound()
        return if (existing != null && existing.courseId == course.id) {
            existing
        } else {
            scoreRepo.newRound(course, currentHandicap(), currentAllowance())
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
        tickFlow.value = SystemClock.elapsedRealtime() // refresh age/stale immediately on glance
        startTicker()
        viewModelScope.launch { location.requestBurst() }
    }

    fun onPause() {
        location.pause()
        stopTicker()
        // Flush the card before the app backgrounds, so a kill can't lose the last score
        // (the off-main collector handles the smooth per-tap saves during play).
        roundFlow.value?.let { scoreRepo.saveActiveRound(it) }
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
        roundFlow.value = updated // persisted off-main by the roundFlow collector in init
    }

    fun resetRound() {
        val course = courseFlow.value ?: return
        roundFlow.value = scoreRepo.newRound(course, currentHandicap(), currentAllowance())
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
        // Reflect the new index in the active round's stroke allocation (collector persists it).
        roundFlow.update { it?.copy(handicapIndex = clamped) }
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
        roundFlow.update { it?.copy(handicapAllowancePercent = clamped) } // collector persists it
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
        // Re-overlay so captured points show immediately. Reload + parse off the main thread.
        viewModelScope.launch {
            val overlaid = withContext(Dispatchers.Default) {
                when (val res = courseRepo.loadCourse(uiState.value.settings.selectedCourseFile)) {
                    is CourseRepository.LoadResult.Success -> surveyRepo.overlay(res.course, data)
                    else -> null
                }
            }
            overlaid?.let { courseFlow.value = it }
        }
        return true
    }

    fun surveyExportPath(): String =
        courseFlow.value?.let { surveyRepo.exportPath(it.id) } ?: ""

    fun clearSurvey() {
        val course = courseFlow.value ?: return
        surveyRepo.clear(course.id)
        viewModelScope.launch { loadCourse(uiState.value.settings.selectedCourseFile) }
    }

    override fun onCleared() {
        location.stop()
        super.onCleared()
    }
}
