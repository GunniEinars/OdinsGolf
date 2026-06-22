package com.odinsgolf.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.GpsState
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.data.model.GpsUpdateMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper over FusedLocationProviderClient. Always requests HIGH_ACCURACY
 * (golf needs real GPS); the [GpsUpdateMode] interval is the battery lever.
 * Lifecycle is driven by the caller via [start]/[stop]; on resume the caller
 * also fires [requestBurst] for an immediate fix.
 */
class LocationEngine(private val context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _state = MutableStateFlow(GpsState(status = GpsStatus.SEARCHING))
    val state: StateFlow<GpsState> = _state.asStateFlow()

    private var started = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { publish(it) }
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(mode: GpsUpdateMode) {
        if (!hasPermission()) {
            _state.value = _state.value.copy(status = GpsStatus.PERMISSION_NEEDED)
            return
        }
        // Always rebuild the request so a mode change takes effect.
        stopUpdates()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, mode.intervalMillis)
            .setMinUpdateIntervalMillis(mode.minUpdateMillis)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, callback, context.mainLooper)
            started = true
            if (_state.value.status == GpsStatus.PAUSED || _state.value.point == null) {
                _state.value = _state.value.copy(status = GpsStatus.SEARCHING)
            }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(status = GpsStatus.UNAVAILABLE)
        }
    }

    /** Pause updates (app not visible). Keeps last fix so resume shows it immediately. */
    fun pause() {
        stopUpdates()
        _state.value = _state.value.copy(status = GpsStatus.PAUSED)
    }

    fun stop() = stopUpdates()

    private fun stopUpdates() {
        if (started) {
            client.removeLocationUpdates(callback)
            started = false
        }
    }

    /** One-shot high-accuracy fix, used on resume to refresh quickly. */
    @SuppressLint("MissingPermission")
    suspend fun requestBurst() {
        if (!hasPermission()) {
            _state.value = _state.value.copy(status = GpsStatus.PERMISSION_NEEDED)
            return
        }
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .build()
        try {
            client.getCurrentLocation(req, null).await()?.let { publish(it) }
        } catch (_: SecurityException) {
            _state.value = _state.value.copy(status = GpsStatus.UNAVAILABLE)
        } catch (_: Exception) {
            // Ignore; periodic updates will catch up.
        }
    }

    private fun publish(location: Location) {
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val status = when {
            accuracy == null -> GpsStatus.WEAK_FIX
            accuracy <= GOOD_ACCURACY_M -> GpsStatus.GOOD_FIX
            else -> GpsStatus.WEAK_FIX
        }
        _state.value = GpsState(
            status = status,
            point = GeoPoint(location.latitude, location.longitude),
            accuracyMeters = accuracy,
            fixElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    companion object {
        const val GOOD_ACCURACY_M = 10f
        const val STALE_AFTER_MILLIS = 30_000L
    }
}

/**
 * Effective status considering staleness: if we have a fix but it is older than
 * [staleAfterMillis], surface STALE_FIX instead of GOOD/WEAK.
 */
fun GpsState.effectiveStatus(
    nowElapsedMillis: Long,
    staleAfterMillis: Long = LocationEngine.STALE_AFTER_MILLIS,
): GpsStatus {
    if (status == GpsStatus.PERMISSION_NEEDED ||
        status == GpsStatus.UNAVAILABLE ||
        status == GpsStatus.PAUSED ||
        point == null
    ) return status
    val fixAt = fixElapsedRealtimeMillis ?: return status
    return if (nowElapsedMillis - fixAt > staleAfterMillis) GpsStatus.STALE_FIX else status
}
