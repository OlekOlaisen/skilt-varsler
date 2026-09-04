package no.skiltvarsler.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.matcher.AlertCopy
import no.skiltvarsler.matcher.AlertEngine
import no.skiltvarsler.matcher.AlertSettings
import no.skiltvarsler.matcher.GpsFix
import no.skiltvarsler.matcher.toAlertKind
import no.skiltvarsler.prefetch.ManifestTile
import no.skiltvarsler.prefetch.TilePlanner
import no.skiltvarsler.prefetch.TilePrefetch
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.TileSelector
import no.skiltvarsler.tilesource.GraphHolder
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var engine: AlertEngine? = null
    private var graphIdentity: String? = null
    private var alertSettings: AlertSettings? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var lastCoverageKey: String? = null
    private var lastEmptyPrefetchMs: Long = 0L
    private var lastPrefetchEnqueuedMs: Long = 0L
    private var cachedManifest: List<ManifestTile> = emptyList()
    private var cachedManifestModified: Long = -1L
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            LastAlertStore.setFix(
                location.latitude,
                location.longitude,
                if (location.hasBearing()) location.bearing.toDouble() else null,
            )
            maybePrefetchForLocation()
            val fix = GpsFix(
                timeMs = location.time,
                position = LatLon(location.latitude, location.longitude),
                accuracyMeters = location.accuracy.toDouble(),
                speedMetersPerSecond = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                bearingDegrees = if (location.hasBearing()) location.bearing.toDouble() else null,
            )
            scope.launch { handleFix(fix) }
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
                startForegroundDriving()
                scope.launch {
                    try {
                        ReplayRunner.run(this@TrackingService)
                    } catch (error: Exception) {
                        val message = "Replay feilet: ${error.message ?: error.javaClass.simpleName}"
                        LastAlertStore.setTracking(message)
                        DebugLog.append("REPLAY $message")
                    }
                }
                return START_STICKY
            }
            else -> {
                startForegroundDriving()
                scope.launch { startTracking() }
            }
        }
        return START_STICKY
    }

    private fun startForegroundDriving() {
        LastAlertStore.setTracking("Starter sporing")
        LastAlertStore.setTrackingActive(true)
        DebugLog.append("TRACKING start")
        val notification = AlertNotifier.drivingNotification(this)
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
            LastAlertStore.setTrackingActive(false)
            DebugLog.append("TRACKING start failed: ${error.message}")
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startTracking() {
        alertSettings = SettingsStore(applicationContext).settings.first()
        LastAlertStore.setAlertsMuted(alertSettings?.alertsMuted == true)
        scope.launch(Dispatchers.Default) {
            SettingsStore(applicationContext).settings.collect { next ->
                alertSettings = next
                LastAlertStore.setAlertsMuted(next.alertsMuted)
                engine?.updateSettings(next)
            }
        }
        engineForCurrentGraph()
        seedLastLocation()
        if (LastAlertStore.latitude != null) {
            TilePrefetch.enqueueNow(this)
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(800L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private suspend fun seedLastLocation() {
        val last: Location? = suspendCancellableCoroutine { continuation ->
            fused.lastLocation
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }
        if (last == null) return
        LastAlertStore.setFix(
            last.latitude,
            last.longitude,
            if (last.hasBearing()) last.bearing.toDouble() else null,
        )
    }

    private suspend fun handleFix(fix: GpsFix) {
        GraphHolder.shiftWindowIfNeeded(fix.position.latitude, fix.position.longitude)
        val alerts = engineForCurrentGraph()?.update(fix).orEmpty()
        val match = engine?.currentMatch()
        val status = when {
            !GraphHolder.isReady() -> "Henter kommune-flis…"
            match == null -> "Ingen match"
            else -> "Lenke ${match.sequenceId}  pos ${"%.3f".format(match.position)}"
        }
        LastAlertStore.setTracking(status)
        LastAlertStore.setUpcomingSigns(upcomingSigns())
        DebugLog.appendFix(fix, match, LastAlertStore.tileStatus())
        withContext(Dispatchers.Main.immediate) {
            if (LastAlertStore.alertsMuted) {
                return@withContext
            }
            alerts.forEach { AlertNotifier.publishAlert(this@TrackingService, it) }
        }
    }

    private fun upcomingSigns(): List<UpcomingSign> {
        return engine?.currentHorizon().orEmpty().mapNotNull { candidate ->
            val kind = candidate.obj.type.toAlertKind() ?: return@mapNotNull null
            UpcomingSign(
                title = AlertCopy.titleFor(kind, candidate.obj.payload),
                metersAhead = roundUpcomingMeters(candidate.metersAhead).coerceAtLeast(10),
                kind = kind,
                payload = candidate.obj.payload,
                nvdbId = candidate.obj.nvdbId,
            )
        }
    }

    /**
     * The graph is swapped into the existing engine rather than rebuilding it, because the window
     * reloads every couple of kilometres. A fresh engine would drop the map match, the fired-alert
     * memory and the last known kommune, which re-fires passed signs and swallows the border alert.
     */
    private fun engineForCurrentGraph(): AlertEngine? {
        val settings = alertSettings ?: return engine
        val identity = GraphHolder.identity()
        val graph = GraphHolder.current()
        val existing = engine
        if (existing == null) {
            val created = AlertEngine(graph, settings)
            engine = created
            graphIdentity = identity
            DebugLog.append("GRAPH $identity links=${graph.links.size}")
            return created
        }
        existing.updateSettings(settings)
        if (graphIdentity != identity) {
            existing.updateGraph(graph)
            graphIdentity = identity
            DebugLog.append("GRAPH $identity links=${graph.links.size}")
        }
        return existing
    }

    private fun maybePrefetchForLocation() {
        val latitude = LastAlertStore.latitude ?: return
        val longitude = LastAlertStore.longitude ?: return
        val now = System.currentTimeMillis()
        val tiles = readCachedManifest()
        if (tiles == null) {
            if (lastCoverageKey == PENDING_MANIFEST && now - lastEmptyPrefetchMs < EMPTY_RETRY_MS) return
            if (!enqueuePrefetch(now)) return
            lastCoverageKey = PENDING_MANIFEST
            lastEmptyPrefetchMs = now
            return
        }
        val tilesDir = File(filesDir, "tiles")
        GraphHolder.setKnownTiles(tilesDir, tiles.map { it.toCoverage() })
        val needed = TilePlanner.select(tiles, latitude, longitude, LastAlertStore.bearingDegrees)
        if (needed.isEmpty()) {
            if (now - lastEmptyPrefetchMs < EMPTY_RETRY_MS) return
            if (!enqueuePrefetch(now)) return
            lastCoverageKey = TileSelector.coverageKey(emptyList())
            lastEmptyPrefetchMs = now
            return
        }
        val coverageKey = TileSelector.coverageKey(needed.map { it.toCoverage() })
        val neededFiles = needed.map { File(tilesDir, it.file) }
        val onDisk = neededFiles.all { it.exists() && it.length() > 0L }
        if (onDisk && GraphHolder.covers(GraphHolder.windowFilesFor(latitude, longitude))) {
            lastCoverageKey = coverageKey
            return
        }
        if (coverageKey == lastCoverageKey && now - lastEmptyPrefetchMs < EMPTY_RETRY_MS) return
        if (!enqueuePrefetch(now)) return
        lastCoverageKey = coverageKey
        lastEmptyPrefetchMs = now
    }

    /**
     * Prefetch now covers neighbouring kommuner, so the wanted tile set shifts every time the car
     * turns. The minimum interval keeps a winding road from re-running the worker on every fix,
     * unless there is no graph at all and alerting depends on it.
     */
    private fun enqueuePrefetch(nowMs: Long): Boolean {
        if (GraphHolder.isReady() && nowMs - lastPrefetchEnqueuedMs < PREFETCH_MIN_INTERVAL_MS) {
            return false
        }
        lastPrefetchEnqueuedMs = nowMs
        TilePrefetch.enqueueNow(this)
        return true
    }

    private fun readCachedManifest(): List<ManifestTile>? {
        val manifestFile = File(filesDir, "tiles/manifest.json")
        if (!manifestFile.exists()) return null
        val modified = manifestFile.lastModified()
        if (modified == cachedManifestModified && cachedManifest.isNotEmpty()) {
            return cachedManifest
        }
        return try {
            val parsed = TilePlanner.parseManifest(JSONObject(manifestFile.readText()))
            cachedManifest = parsed
            cachedManifestModified = modified
            parsed
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        scope.cancel()
        LastAlertStore.setTrackingActive(false)
        LastAlertStore.setTracking("Stoppet")
        DebugLog.append("TRACKING stop")
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "no.skiltvarsler.STOP"
        const val ACTION_REPLAY = "no.skiltvarsler.REPLAY"
        private const val PENDING_MANIFEST = "pending-manifest"
        private const val EMPTY_RETRY_MS = 120_000L
        private const val PREFETCH_MIN_INTERVAL_MS = 30_000L
    }
}
