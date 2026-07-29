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
 * Process-wide GPS state. Both the in-activity [LocationEngine] and the Play-mode
 * foreground service publish here, so the UI reads a single source no matter which
 * one is currently driving the receiver.
 */
object LocationBus {
    val state = MutableStateFlow(GpsState(status = GpsStatus.SEARCHING))

    /** Receipt time (real elapsed clock) of the last fix we accepted — the anti-freeze anchor. */
    @Volatile private var lastAcceptedElapsed = 0L

    /**
     * Publish a new *fix* through the [shouldAccept] filter. On a wrist-raise several sources
     * (warm service, resume burst, restarted engine) fire almost at once and the receiver's
     * first fixes after refocusing are often low-accuracy — showing every one made the number
     * jump. [isBetterFix] keeps a good recent fix instead of hopping onto a much-worse one, so the
     * yardage settles quickly. But it must never *freeze*: a stuck warm fix that keeps its own clock
     * fresh could get its worse-accuracy successors rejected forever, pinning you at the wrong spot
     * for a whole round. [shouldAccept] therefore force-takes the newest fix after a short real-time
     * gap. Synchronised because the burst can resolve off the main thread.
     */
    @Synchronized
    fun publishFix(candidate: GpsState) {
        val now = SystemClock.elapsedRealtime()
        if (shouldAccept(candidate, state.value, now - lastAcceptedElapsed)) {
            state.value = candidate
            lastAcceptedElapsed = now
        }
    }
}

/** How long a reject-streak may hold the position before we force-take the newest fix (ms). Bounds
 *  any freeze to a few seconds of real time, whatever a stuck provider claims on the fix's own clock. */
private const val FORCE_ACCEPT_AFTER_MS = 5_000L

/**
 * Accept a candidate fix if it's genuinely better ([isBetterFix]) **or** if it's been longer than
 * [FORCE_ACCEPT_AFTER_MS] of real time since we last accepted anything — so movement always wins
 * within a few seconds and the position can never freeze at a stale spot. Pure, so it is unit-tested.
 */
fun shouldAccept(new: GpsState, cur: GpsState, msSinceLastAccept: Long): Boolean =
    msSinceLastAccept > FORCE_ACCEPT_AFTER_MS || isBetterFix(new, cur)

/** A fix no older than this (ms) than the current one is treated as "current enough" to replace it
 *  even if less accurate — so we never get stuck on a stale-but-accurate fix while you move. */
private const val FIX_SIGNIFICANT_NEWER_MS = 8_000L
/** A newer fix up to this much (m) worse in accuracy is still accepted; worse than this is a spike. */
private const val FIX_ACC_SLACK_M = 10f

/**
 * The classic "is this a better location" decision, on the fix's own clock and accuracy:
 * accept a more-accurate fix always; accept a newer fix that's only slightly worse; reject a fix
 * that is much less accurate (unless the current one has aged out). Pure, so it is unit-tested.
 */
fun isBetterFix(new: GpsState, cur: GpsState): Boolean {
    if (cur.point == null) return true
    val curT = cur.fixElapsedRealtimeMillis ?: return true
    val newT = new.fixElapsedRealtimeMillis ?: return false
    val dt = newT - curT
    if (dt > FIX_SIGNIFICANT_NEWER_MS) return true // current has aged out — take the new one
    val newAcc = new.accuracyMeters ?: return dt >= 0
    val curAcc = cur.accuracyMeters ?: return true
    val accDelta = newAcc - curAcc
    return when {
        accDelta <= 0f -> true                     // as good or better accuracy → always take
        dt >= 0 && accDelta <= FIX_ACC_SLACK_M -> true // newer and only slightly worse
        else -> false                              // worse (and older, or a big spike) → reject
    }
}

/**
 * Thin wrapper over FusedLocationProviderClient. Always requests HIGH_ACCURACY
 * (golf needs real GPS); the update interval is the battery lever. Lifecycle is
 * driven by the caller via [start]/[stop]; on resume the caller also fires
 * [requestBurst] for an immediate fix. All output goes to the shared [LocationBus].
 */
class LocationEngine(private val context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    val state: StateFlow<GpsState> = LocationBus.state.asStateFlow()

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

    /** Whether location services are switched on system-wide. Off ⇒ we get no fixes at all. */
    fun isLocationEnabled(): Boolean =
        runCatching {
            context.getSystemService(android.location.LocationManager::class.java)?.isLocationEnabled == true
        }.getOrDefault(true)

    fun start(mode: GpsUpdateMode) = start(mode.intervalMillis, mode.minUpdateMillis)

    @SuppressLint("MissingPermission")
    fun start(intervalMillis: Long, minUpdateMillis: Long) {
        if (!hasPermission()) {
            LocationBus.state.value = LocationBus.state.value.copy(status = GpsStatus.PERMISSION_NEEDED)
            return
        }
        // Location switched off system-wide ⇒ no fixes will ever arrive. Surface it plainly instead
        // of spinning on "Searching" forever (the failure that made a whole round unusable).
        if (!isLocationEnabled()) {
            LocationBus.state.value = LocationBus.state.value.copy(status = GpsStatus.LOCATION_DISABLED)
            return
        }
        // Always rebuild the request so a mode change takes effect.
        stopUpdates()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(minUpdateMillis)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, callback, context.mainLooper)
            started = true
            val s = LocationBus.state.value
            if (s.status == GpsStatus.PAUSED || s.point == null) {
                LocationBus.state.value = s.copy(status = GpsStatus.SEARCHING)
            }
        } catch (e: SecurityException) {
            LocationBus.state.value = LocationBus.state.value.copy(status = GpsStatus.UNAVAILABLE)
        }
    }

    /** Pause updates (app not visible). Keeps last fix so resume shows it immediately. */
    fun pause() {
        stopUpdates()
        LocationBus.state.value = LocationBus.state.value.copy(status = GpsStatus.PAUSED)
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
            LocationBus.state.value = LocationBus.state.value.copy(status = GpsStatus.PERMISSION_NEEDED)
            return
        }
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            // Accept a very recent fix (e.g. one the Play-mode service just produced while the
            // receiver was warm) instead of forcing a brand-new compute — that makes a glance
            // instant when a current fix already exists, while still being fresh enough that it
            // reflects where you now stand.
            .setMaxUpdateAgeMillis(FRESH_ENOUGH_MILLIS)
            .build()
        try {
            client.getCurrentLocation(req, null).await()?.let { publish(it) }
        } catch (_: SecurityException) {
            LocationBus.state.value = LocationBus.state.value.copy(status = GpsStatus.UNAVAILABLE)
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
        // Age the fix by when the GPS *computed* it (the Location's own elapsed-realtime
        // clock), NOT when we received it. The fused provider can deliver a stale/cached
        // "last known" fix — it arrives now but the position may be seconds-to-minutes old
        // (GPS lost lock, throttling). Stamping receipt-time let a frozen position pose as a
        // live number (the "stuck at 120 m greenside" bug). Using the fix's own clock means a
        // stale/stuck fix correctly ages past the stale threshold, dims and flags "stale".
        val nanos = location.elapsedRealtimeNanos
        val fixMillis = if (nanos > 0L) nanos / 1_000_000L else SystemClock.elapsedRealtime()
        LocationBus.publishFix(
            GpsState(
                status = status,
                point = GeoPoint(location.latitude, location.longitude),
                accuracyMeters = accuracy,
                fixElapsedRealtimeMillis = fixMillis,
            ),
        )
    }

    companion object {
        const val GOOD_ACCURACY_M = 10f
        const val STALE_AFTER_MILLIS = 30_000L

        /**
         * A burst may reuse a fix no older than this (ms) before forcing a fresh compute. Kept short
         * on purpose: **accuracy beats speed on a glance.** An earlier 8 s window let a wrist-raise
         * reuse a warm fix that could be seconds stale (or, in marginal signal, a bad cached one) —
         * which showed yardages well off from where you actually stood. At 2 s a reused fix is
         * essentially your current position; otherwise the burst computes fresh (~1–2 s) so the number
         * is right. The foreground stream then keeps it current.
         */
        const val FRESH_ENOUGH_MILLIS = 2_000L

        /**
         * Play-mode continuous tracking: warm and reasonably fresh. Keeping the receiver *powered*
         * is what kills the cold start, but a fresher warm fix also lets the resume burst reuse it
         * outright (see [FRESH_ENOUGH_MILLIS]) instead of re-computing — so glances feel instant.
         */
        const val PLAY_INTERVAL_MILLIS = 8_000L
        const val PLAY_MIN_UPDATE_MILLIS = 4_000L
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
        status == GpsStatus.LOCATION_DISABLED ||
        status == GpsStatus.UNAVAILABLE ||
        status == GpsStatus.PAUSED ||
        point == null
    ) return status
    val fixAt = fixElapsedRealtimeMillis ?: return status
    return if (nowElapsedMillis - fixAt > staleAfterMillis) GpsStatus.STALE_FIX else status
}
