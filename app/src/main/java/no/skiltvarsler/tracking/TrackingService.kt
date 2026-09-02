package no.skiltvarsler.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import no.skiltvarsler.matcher.AlertEngine
import no.skiltvarsler.matcher.AlertSettings
import no.skiltvarsler.matcher.GpsFix
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tilesource.GraphHolder

class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: AlertEngine? = null
    private var graphIdentity: String? = null
    private var alertSettings: AlertSettings? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            LastAlertStore.setFix(location.latitude, location.longitude, if (location.hasBearing()) location.bearing.toDouble() else null)
            val fix = GpsFix(
                timeMs = location.time,
                position = LatLon(location.latitude, location.longitude),
                accuracyMeters = location.accuracy.toDouble(),
                speedMetersPerSecond = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                bearingDegrees = if (location.hasBearing()) location.bearing.toDouble() else null,
            )
            val alerts = engineForCurrentGraph()?.update(fix).orEmpty()
            val match = engine?.currentMatch()
            val status = if (match == null) {
                "Ingen match"
            } else {
                "Lenke ${match.sequenceId}  pos ${"%.3f".format(match.position)}"
            }
            LastAlertStore.setTracking(status)
            try {
                startForeground(
                    AlertNotifier.DRIVING_NOTIFICATION_ID,
                    AlertNotifier.drivingNotification(this@TrackingService, status),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } catch (_: SecurityException) {
                return
            }
            alerts.forEach { AlertNotifier.publishAlert(this@TrackingService, it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REPLAY -> {
                startForegroundDriving("Replay")
                scope.launch {
                    try {
                        ReplayRunner.run(this@TrackingService)
                    } catch (error: Exception) {
                        LastAlertStore.setTracking(
                            "Replay feilet: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                }
                return START_STICKY
            }
            else -> {
                startForegroundDriving("Starter sporing")
                scope.launch { startTracking() }
            }
        }
        return START_STICKY
    }

    private fun startForegroundDriving(status: String) {
        LastAlertStore.setTracking(status)
        val notification = AlertNotifier.drivingNotification(this, status)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    AlertNotifier.DRIVING_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(AlertNotifier.DRIVING_NOTIFICATION_ID, notification)
            }
        } catch (error: SecurityException) {
            LastAlertStore.setTracking("Kan ikke starte sporing: ${error.message}")
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startTracking() {
        alertSettings = SettingsStore(applicationContext).settings.first()
        engineForCurrentGraph()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(800L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun engineForCurrentGraph(): AlertEngine? {
        val settings = alertSettings ?: return engine
        val identity = GraphHolder.identity()
        val existing = engine
        if (existing != null && graphIdentity == identity) return existing
        val next = AlertEngine(GraphHolder.current(), settings)
        engine = next
        graphIdentity = identity
        return next
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "no.skiltvarsler.STOP"
        const val ACTION_REPLAY = "no.skiltvarsler.REPLAY"
    }
}
