package com.odinsgolf.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.odinsgolf.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cross-lifecycle idle tracking for Play mode: the activity stamps this on every
 * wrist-raise (ON_RESUME); the service auto-stops if you haven't glanced in a while,
 * so it never keeps GPS running while the watch sits in your bag.
 */
object PlayMode {
    @Volatile var lastActiveElapsedMillis: Long = SystemClock.elapsedRealtime()
    const val IDLE_TIMEOUT_MILLIS = 20 * 60 * 1000L // 20 min with no wrist-raise
}

/**
 * Foreground service that keeps the GPS receiver **warm** during a round, at a lean
 * interval, so raising your wrist at the ball reads a live distance in ~1–2 s instead
 * of a cold re-acquire. The screen still sleeps between glances (the big battery saver);
 * only the receiver stays engaged. Publishes to the shared [LocationBus] like the
 * in-activity engine, so the UI is unaffected by which one is driving.
 */
class PlayModeService : Service() {

    private val engine by lazy { LocationEngine(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlay()
            return START_NOT_STICKY
        }
        // A location-typed foreground service can't start without location permission (API 34+),
        // and we must never let a service exception crash the app process. Bail cleanly if either
        // the permission is gone or going foreground fails.
        if (!engine.hasPermission()) {
            persistPlayModeOff()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startAsForeground()
        } catch (e: Exception) {
            persistPlayModeOff()
            stopSelf()
            return START_NOT_STICKY
        }
        PlayMode.lastActiveElapsedMillis = SystemClock.elapsedRealtime()
        engine.start(LocationEngine.PLAY_INTERVAL_MILLIS, LocationEngine.PLAY_MIN_UPDATE_MILLIS)
        startIdleWatch()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Play mode", NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps GPS ready during your round"
                setShowBadge(false)
            }
            nm?.createNotificationChannel(channel)
        }
        val stopPending = PendingIntent.getService(
            this, 1,
            Intent(this, PlayModeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("OdinsGolf · Play mode")
            .setContentText("GPS staying ready for instant distances")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopPending)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startIdleWatch() {
        if (idleJob?.isActive == true) return
        idleJob = scope.launch {
            while (isActive) {
                delay(60_000)
                val idle = SystemClock.elapsedRealtime() - PlayMode.lastActiveElapsedMillis
                if (idle > PlayMode.IDLE_TIMEOUT_MILLIS) {
                    withContext(Dispatchers.Main) { stopPlay() }
                    break
                }
            }
        }
    }

    /** Turn Play mode off: persist the flag (so the UI reflects it), drop GPS, and stop. */
    private fun stopPlay() {
        persistPlayModeOff()
        engine.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Fire-and-forget on a scope that outlives this service, so the write commits even as
    // the service tears down (a service-scoped write could be cancelled mid-flight).
    @OptIn(DelicateCoroutinesApi::class)
    private fun persistPlayModeOff() {
        val app = applicationContext
        GlobalScope.launch(Dispatchers.IO) { runCatching { SettingsRepository(app).setPlayMode(false) } }
    }

    override fun onDestroy() {
        engine.stop()
        idleJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "play_mode"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.odinsgolf.action.STOP_PLAY"
    }
}
