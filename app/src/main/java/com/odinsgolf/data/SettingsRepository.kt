package com.odinsgolf.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.odinsgolf.data.model.GpsUpdateMode
import com.odinsgolf.data.model.MapStyle
import com.odinsgolf.data.model.RoundMode
import com.odinsgolf.data.model.ScoringFormat
import com.odinsgolf.data.model.Units
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User settings, persisted with Preferences DataStore. */
data class AppSettings(
    val units: Units = Units.METERS,
    val gpsMode: GpsUpdateMode = GpsUpdateMode.NORMAL,
    val keepScreenOn: Boolean = false,
    // Whole-number course handicap is no longer stored; this is the decimal index (e.g. 15.7).
    val handicapIndex: Double = 0.0,
    val roundMode: RoundMode = RoundMode.FULL_18,
    val debugGps: Boolean = false,
    val selectedCourseFile: String = CourseRepository.DEFAULT_COURSE_FILE,
    val currentHole: Int = 1,
    val mapStyle: MapStyle = MapStyle.VECTOR,
    val scoringFormat: ScoringFormat = ScoringFormat.STABLEFORD,
    /** WHS handicap allowance as a percent (95 = singles standard, 100 = full course handicap). */
    val handicapAllowancePercent: Int = 95,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "odins_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val GPS_MODE = stringPreferencesKey("gps_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HANDICAP_INDEX = doublePreferencesKey("handicap_index")
        val ROUND_MODE = stringPreferencesKey("round_mode")
        val DEBUG_GPS = booleanPreferencesKey("debug_gps")
        val COURSE_FILE = stringPreferencesKey("course_file")
        val CURRENT_HOLE = intPreferencesKey("current_hole")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val SCORING_FORMAT = stringPreferencesKey("scoring_format")
        val HCP_ALLOWANCE = intPreferencesKey("handicap_allowance_percent")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            units = Units.parse(p[Keys.UNITS]),
            gpsMode = GpsUpdateMode.fromName(p[Keys.GPS_MODE]),
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
            handicapIndex = p[Keys.HANDICAP_INDEX] ?: 0.0,
            roundMode = RoundMode.fromName(p[Keys.ROUND_MODE]),
            debugGps = p[Keys.DEBUG_GPS] ?: false,
            selectedCourseFile = p[Keys.COURSE_FILE] ?: CourseRepository.DEFAULT_COURSE_FILE,
            currentHole = p[Keys.CURRENT_HOLE] ?: 1,
            mapStyle = MapStyle.fromName(p[Keys.MAP_STYLE]),
            scoringFormat = ScoringFormat.fromName(p[Keys.SCORING_FORMAT]),
            handicapAllowancePercent = p[Keys.HCP_ALLOWANCE] ?: 95,
        )
    }

    suspend fun setUnits(units: Units) = edit { it[Keys.UNITS] = units.name }
    suspend fun setGpsMode(mode: GpsUpdateMode) = edit { it[Keys.GPS_MODE] = mode.name }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setHandicapIndex(value: Double) =
        edit { it[Keys.HANDICAP_INDEX] = value.coerceIn(0.0, 54.0) }
    suspend fun setRoundMode(mode: RoundMode) = edit { it[Keys.ROUND_MODE] = mode.name }
    suspend fun setDebugGps(value: Boolean) = edit { it[Keys.DEBUG_GPS] = value }
    suspend fun setCourseFile(file: String) = edit { it[Keys.COURSE_FILE] = file }
    suspend fun setCurrentHole(hole: Int) = edit { it[Keys.CURRENT_HOLE] = hole }
    suspend fun setMapStyle(style: MapStyle) = edit { it[Keys.MAP_STYLE] = style.name }
    suspend fun setScoringFormat(format: ScoringFormat) = edit { it[Keys.SCORING_FORMAT] = format.name }
    suspend fun setHandicapAllowance(percent: Int) =
        edit { it[Keys.HCP_ALLOWANCE] = percent.coerceIn(50, 100) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
