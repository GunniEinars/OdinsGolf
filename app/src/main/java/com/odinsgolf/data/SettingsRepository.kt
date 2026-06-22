package com.odinsgolf.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.odinsgolf.data.model.GpsUpdateMode
import com.odinsgolf.data.model.Units
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User settings, persisted with Preferences DataStore. */
data class AppSettings(
    val units: Units = Units.METERS,
    val gpsMode: GpsUpdateMode = GpsUpdateMode.NORMAL,
    val keepScreenOn: Boolean = false,
    val handicap: Int = 0,
    val debugGps: Boolean = false,
    val selectedCourseFile: String = CourseRepository.DEFAULT_COURSE_FILE,
    val currentHole: Int = 1,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "odins_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val GPS_MODE = stringPreferencesKey("gps_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HANDICAP = intPreferencesKey("handicap")
        val DEBUG_GPS = booleanPreferencesKey("debug_gps")
        val COURSE_FILE = stringPreferencesKey("course_file")
        val CURRENT_HOLE = intPreferencesKey("current_hole")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            units = Units.parse(p[Keys.UNITS]),
            gpsMode = GpsUpdateMode.fromName(p[Keys.GPS_MODE]),
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
            handicap = p[Keys.HANDICAP] ?: 0,
            debugGps = p[Keys.DEBUG_GPS] ?: false,
            selectedCourseFile = p[Keys.COURSE_FILE] ?: CourseRepository.DEFAULT_COURSE_FILE,
            currentHole = p[Keys.CURRENT_HOLE] ?: 1,
        )
    }

    suspend fun setUnits(units: Units) = edit { it[Keys.UNITS] = units.name }
    suspend fun setGpsMode(mode: GpsUpdateMode) = edit { it[Keys.GPS_MODE] = mode.name }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setHandicap(value: Int) = edit { it[Keys.HANDICAP] = value.coerceIn(0, 54) }
    suspend fun setDebugGps(value: Boolean) = edit { it[Keys.DEBUG_GPS] = value }
    suspend fun setCourseFile(file: String) = edit { it[Keys.COURSE_FILE] = file }
    suspend fun setCurrentHole(hole: Int) = edit { it[Keys.CURRENT_HOLE] = hole }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
