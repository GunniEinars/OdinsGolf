package com.odinsgolf.data.model

/** Coarse GPS state surfaced to the UI. */
enum class GpsStatus {
    PERMISSION_NEEDED,
    SEARCHING,
    GOOD_FIX,
    WEAK_FIX,
    STALE_FIX,
    PAUSED,
    UNAVAILABLE,
}

/**
 * Snapshot of the current location situation.
 *
 * @param point last known position, or null if none yet.
 * @param accuracyMeters horizontal accuracy of [point], or null if unknown.
 * @param fixElapsedRealtimeMillis the fix's own elapsed-realtime clock (Location.elapsedRealtimeNanos),
 *        i.e. when the GPS computed it — so a stale/cached fix ages correctly, not receipt time.
 */
data class GpsState(
    val status: GpsStatus = GpsStatus.SEARCHING,
    val point: GeoPoint? = null,
    val accuracyMeters: Float? = null,
    val fixElapsedRealtimeMillis: Long? = null,
) {
    val hasUsableFix: Boolean
        get() = point != null && (status == GpsStatus.GOOD_FIX || status == GpsStatus.WEAK_FIX || status == GpsStatus.STALE_FIX)
}
