package com.odinsgolf.data

import android.content.Context
import com.odinsgolf.data.model.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Current conditions from **Open-Meteo** — free, no API key, so nothing sensitive ships in the APK.
 * The last result is cached to files/weather.json so the round keeps its numbers if the signal
 * drops. One current-conditions call per fetch; the caller decides when (see RoundViewModel).
 */
class WeatherRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file: File get() = File(context.filesDir, WEATHER_FILE)

    fun cached(): Weather? = runCatching {
        if (file.exists()) json.decodeFromString<Weather>(file.readText()) else null
    }.getOrNull()

    suspend fun fetch(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,precipitation,wind_speed_10m,wind_direction_10m" +
                    "&wind_speed_unit=ms",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "OdinsGolf/1.0 (personal golf app)")
            }
            val text = conn.inputStream.use { it.readBytes().decodeToString() }
            conn.disconnect()
            val current = json.decodeFromString<OpenMeteoResponse>(text).current ?: return@withContext null
            val weather = Weather(
                windSpeedMps = current.wind_speed_10m ?: 0.0,
                windFromDeg = current.wind_direction_10m ?: 0.0,
                precipMm = current.precipitation ?: 0.0,
                tempC = current.temperature_2m ?: 0.0,
                fetchedEpochMillis = System.currentTimeMillis(),
            )
            runCatching { file.writeText(json.encodeToString(weather)) }
            weather
        } catch (e: Exception) {
            null
        }
    }

    @Serializable
    private data class OpenMeteoResponse(val current: OpenMeteoCurrent? = null)

    @Serializable
    private data class OpenMeteoCurrent(
        val temperature_2m: Double? = null,
        val precipitation: Double? = null,
        val wind_speed_10m: Double? = null,
        val wind_direction_10m: Double? = null,
    )

    companion object {
        const val WEATHER_FILE = "weather.json"
    }
}
