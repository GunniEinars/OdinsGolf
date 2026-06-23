package com.odinsgolf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches and disk-caches satellite map tiles for the hole map. Source is Esri
 * "World Imagery" (free, no API key) — fine for low-volume personal use. Tiles
 * are cached under cacheDir/tiles so a hole viewed once works offline after.
 *
 * Attribution (shown on the map): Source: Esri, Maxar, Earthstar Geographics.
 */
class TileRepository(context: Context) {

    private val dir = File(context.cacheDir, "tiles")

    /** Decoded tile bitmap, or null if it isn't cached and can't be fetched. */
    suspend fun tile(z: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = File(dir, "$z/${x}_$y.png")
        if (f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)?.let { return@withContext it }
        }
        try {
            // Esri tile path is /tile/{z}/{row=y}/{col=x}.
            val url = URL("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "OdinsGolf/1.0 (personal golf app)")
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.isEmpty()) return@withContext null
            runCatching {
                f.parentFile?.mkdirs()
                f.writeBytes(bytes)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
