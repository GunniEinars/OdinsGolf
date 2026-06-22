package com.odinsgolf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odinsgolf.data.AppSettings
import com.odinsgolf.data.CourseRepository
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
import com.odinsgolf.data.model.Round
import com.odinsgolf.data.model.Units
import com.odinsgolf.location.LocationEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
}

class RoundViewModel(app: Application) : AndroidViewModel(app) {

    private val courseRepo = CourseRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val scoreRepo = ScorecardRepository(app)
    private val surveyRepo = SurveyRepository(app)
    private val location = LocationEngine(app)

    val gpsState: StateFlow<GpsState> get() = location.state

    private val courseFlow = MutableStateFlow<Course?>(null)
    private val loadErrorFlow = MutableStateFlow<String?>(null)
    private val roundFlow = MutableStateFlow<Round?>(null)
    private val tickFlow = MutableStateFlow(SystemClock.elapsedRealtime())

    // Combine course + error so a load failure reliably triggers a UI emission.
    private val courseLoad: Flow<Pair<Course?, String?>> =
        combine(courseFlow, loadErrorFlow) { course, error -> course to error }

    val uiState: StateFlow<GolfUiState> =
        combine(
            settingsRepo.settings,
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

    init {
        loadCourse()
        // Slow ticker so "last updated" / stale state refreshes without new fixes.
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                tickFlow.value = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun loadCourse() {
        // Single course for now; selectedCourseFile is honored when more are added.
        when (val res = courseRepo.loadCourse()) {
            is CourseRepository.LoadResult.Success -> {
                val overlaid = surveyRepo.overlay(res.course, surveyRepo.load(res.course.id))
                courseFlow.value = overlaid
                loadErrorFlow.value = null
                ensureRound(overlaid)
            }
            is CourseRepository.LoadResult.Failure -> {
                loadErrorFlow.value = res.message
            }
        }
    }

    private fun ensureRound(course: Course) {
        val existing = scoreRepo.loadActiveRound()
        roundFlow.value = if (existing != null && existing.courseId == course.id) {
            existing
        } else {
            scoreRepo.newRound(course, currentHandicap()).also { scoreRepo.saveActiveRound(it) }
        }
    }

    private fun currentHandicap(): Int = uiState.value.settings.handicap

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
        val max = uiState.value.course?.holes?.size ?: 18
        val clamped = n.coerceIn(1, max)
        viewModelScope.launch { settingsRepo.setCurrentHole(clamped) }
    }

    // ---- Scoring ------------------------------------------------------------

    fun incStrokes() = mutateScore { it.copy(strokes = (it.strokes + 1).coerceAtMost(20)) }
    fun decStrokes() = mutateScore { it.copy(strokes = (it.strokes - 1).coerceAtLeast(0)) }
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
        val fresh = scoreRepo.newRound(course, currentHandicap())
        roundFlow.value = fresh
        scoreRepo.saveActiveRound(fresh)
    }

    fun exportRound(): String? = roundFlow.value?.let { scoreRepo.exportRound(it) }

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
    fun setHandicap(value: Int) = viewModelScope.launch {
        settingsRepo.setHandicap(value)
        // Reflect new handicap in the active round's stroke allocation.
        roundFlow.update { it?.copy(playerHandicap = value.coerceIn(0, 54)) }
        roundFlow.value?.let { scoreRepo.saveActiveRound(it) }
    }
    fun setDebugGps(value: Boolean) = viewModelScope.launch { settingsRepo.setDebugGps(value) }

    // ---- Survey -------------------------------------------------------------

    fun captureSurveyPoint(kind: SurveyKind, label: String = "") {
        val course = courseFlow.value ?: return
        val gps = location.state.value
        val p = gps.point ?: return
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
        // Re-overlay so captured points show immediately. Reload base course first.
        viewModelScope.launch {
            when (val res = courseRepo.loadCourse()) {
                is CourseRepository.LoadResult.Success ->
                    courseFlow.value = surveyRepo.overlay(res.course, data)
                else -> {}
            }
        }
    }

    fun surveyExportPath(): String =
        courseFlow.value?.let { surveyRepo.exportPath(it.id) } ?: ""

    fun clearSurvey() {
        val course = courseFlow.value ?: return
        surveyRepo.clear(course.id)
        viewModelScope.launch { loadCourse() }
    }

    override fun onCleared() {
        location.stop()
        super.onCleared()
    }
}
