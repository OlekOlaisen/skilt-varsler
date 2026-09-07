package no.skiltvarsler.tracking

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.matcher.AlertEngine
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.matcher.GpsFix
import no.skiltvarsler.matcher.GpsTrace
import no.skiltvarsler.matcher.Replay
import no.skiltvarsler.matcher.SyntheticGraph
import no.skiltvarsler.matcher.TraceGraph
import no.skiltvarsler.matcher.e6NorthLink
import no.skiltvarsler.prefetch.TilePlanner
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.TravelDirection
import no.skiltvarsler.tilesource.AndroidTileLoader
import no.skiltvarsler.tilesource.GraphHolder
import org.json.JSONObject
import java.io.File

object ReplayRunner {
    @Volatile
    private var ownsTripRecording: Boolean = false

    suspend fun run(context: Context) {
        runE6Camera(context)
    }

    suspend fun runE6Camera(context: Context) {
        try {
            ensureTripRecording()
            LastAlertStore.setTracking("Replay E6 nord")
            DebugLog.append("REPLAY start E6 nord")
            val settings = SettingsStore(context).settings.first()
            val graph = SyntheticGraph.e6VestbyLike()
            val engine = AlertEngine(graph, settings)
            val fixes = Replay.alongLink(
                graph.e6NorthLink(),
                TravelDirection.MED,
                speedMetersPerSecond = 25.0,
            )
            for (fix in fixes) {
                val alerts = engine.update(fix)
                TripRecorder.recordFix(fix)
                TripRecorder.recordAlerts(alerts)
                val match = engine.currentMatch()
                if (match != null) {
                    LastAlertStore.setTracking(
                        "Replay lenke ${match.sequenceId}  ${"%.0f".format(match.position * SyntheticGraph.LENGTH_METERS)} m",
                    )
                }
                for (alert in alerts) {
                    withContext(Dispatchers.Main.immediate) {
                        AlertNotifier.publishAlert(context, alert)
                    }
                }
                delay(80)
            }
            val cameras = Replay.play(AlertEngine(graph, settings), fixes)
                .alertsOf(AlertKind.SPEED_CAMERA)
            if (cameras.isEmpty()) {
                LastAlertStore.setTracking("Replay ferdig — ingen fotoboks")
                DebugLog.append("REPLAY done cameras=0")
            } else {
                LastAlertStore.setTracking("Replay ferdig — fotoboks ${cameras.first().nvdbId}")
                DebugLog.append("REPLAY done cameras=${cameras.size} first=${cameras.first().nvdbId}")
            }
            finishOwnedTripIfNeeded()
        } catch (error: Exception) {
            val message = "Replay feilet: ${error.message ?: error.javaClass.simpleName}"
            LastAlertStore.setTracking(message)
            DebugLog.append("REPLAY $message")
            finishOwnedTripIfNeeded()
        }
    }

    suspend fun runGpsTrace(context: Context, assetPath: String) {
        try {
            ensureTripRecording()
            val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val (meta, clean) = GpsTrace.parse(text)
            LastAlertStore.setTracking("Replay ${meta.name}")
            DebugLog.append("REPLAY start ${meta.name} points=${clean.size}")
            val settings = SettingsStore(context).settings.first()
            val (graph, graphSource) = loadGraphForTrace(context, clean)
            val objectCount = graph.sequences.keys.sumOf { sequenceId -> graph.objectsOn(sequenceId).size }
            DebugLog.append(
                "REPLAY graph source=$graphSource links=${graph.links.size} objects=$objectCount",
            )
            if (objectCount == 0) {
                LastAlertStore.setTracking(
                    "Replay ${meta.name}: mangler NVDB-kart for ruten — bare distanse telles",
                )
            }
            val engine = AlertEngine(graph, settings)
            var holdSamples = 0
            var alertCount = 0
            for ((index, fix) in clean.withIndex()) {
                val alerts = engine.update(fix)
                TripRecorder.recordFix(fix)
                TripRecorder.recordAlerts(alerts)
                alertCount += alerts.size
                if (engine.isHoldingMatch()) {
                    holdSamples += 1
                }
                val match = engine.currentMatch()
                if (match != null) {
                    LastAlertStore.setTracking(
                        "Replay ${meta.name}: seq=${match.sequenceId} " +
                            "pos=${"%.2f".format(match.position)} " +
                            "alerts=$alertCount",
                    )
                }
                if (index % 35 == 34) {
                    reloadGraphAlongRoute(context, engine, fix)
                }
                for (alert in alerts) {
                    withContext(Dispatchers.Main.immediate) {
                        AlertNotifier.publishAlert(context, alert)
                    }
                }
                delay(20)
            }
            val finalMatch = engine.currentMatch()
            val message =
                "Replay ${meta.name} ferdig — seq=${finalMatch?.sequenceId} " +
                    "alerts=$alertCount hold=$holdSamples graph=$graphSource"
            LastAlertStore.setTracking(message)
            DebugLog.append("REPLAY done $message")
            finishOwnedTripIfNeeded()
        } catch (error: Exception) {
            val message = "Replay feilet: ${error.message ?: error.javaClass.simpleName}"
            LastAlertStore.setTracking(message)
            DebugLog.append("REPLAY $message")
            finishOwnedTripIfNeeded()
        }
    }

    /**
     * Prefer cached NVDB kommune tiles covering the GPS route so alerts (cameras, tolls, …)
     * are counted. TraceGraph is only a fallback when no map files are on disk.
     */
    private fun loadGraphForTrace(context: Context, fixes: List<GpsFix>): Pair<RoadGraph, String> {
        val cacheDir = File(context.filesDir, "tiles")
        ensureKnownTilesFromCache(cacheDir)
        val center = traceCenter(fixes)
        val radiusMeters = traceRadiusMeters(fixes)
        val files = tileFilesFor(cacheDir, center.latitude, center.longitude)
        if (files.isEmpty()) {
            return TraceGraph.fromFixes(fixes, withSideTemptation = false) to "trace-fallback"
        }
        return try {
            val loaded = AndroidTileLoader.loadNear(
                files = files,
                latitude = center.latitude,
                longitude = center.longitude,
                radiusMeters = radiusMeters,
            )
            if (loaded.links.isEmpty()) {
                TraceGraph.fromFixes(fixes, withSideTemptation = false) to "trace-empty-window"
            } else {
                loaded to "nvdb"
            }
        } catch (_: OutOfMemoryError) {
            val loaded = AndroidTileLoader.loadNear(
                files = files,
                latitude = center.latitude,
                longitude = center.longitude,
                radiusMeters = (radiusMeters / 2.0).coerceAtLeast(3_000.0),
            )
            loaded to "nvdb-reduced"
        }
    }

    private fun reloadGraphAlongRoute(context: Context, engine: AlertEngine, fix: GpsFix) {
        val cacheDir = File(context.filesDir, "tiles")
        val files = tileFilesFor(cacheDir, fix.position.latitude, fix.position.longitude)
        if (files.isEmpty()) {
            return
        }
        try {
            val next = AndroidTileLoader.loadNear(
                files = files,
                latitude = fix.position.latitude,
                longitude = fix.position.longitude,
                radiusMeters = 6_000.0,
            )
            if (next.links.isNotEmpty()) {
                engine.updateGraph(next)
            }
        } catch (_: OutOfMemoryError) {
            // Keep the previous replay graph.
        }
    }

    private fun tileFilesFor(cacheDir: File, latitude: Double, longitude: Double): List<File> {
        val fromWindow = GraphHolder.windowFilesFor(latitude, longitude)
        if (fromWindow.isNotEmpty()) {
            return fromWindow
        }
        return cacheDir.listFiles()
            ?.filter { file ->
                file.name.startsWith("kommune-") &&
                    file.extension == "sqlite" &&
                    AndroidTileLoader.isReadable(file)
            }
            .orEmpty()
            .sortedBy { file -> file.name }
    }

    private fun ensureKnownTilesFromCache(cacheDir: File) {
        val manifest = File(cacheDir, "manifest.json")
        if (!manifest.exists()) {
            return
        }
        try {
            val tiles = TilePlanner.parseManifest(JSONObject(manifest.readText()))
            GraphHolder.setKnownTiles(cacheDir, tiles.map { it.toCoverage() })
        } catch (_: Exception) {
            // Replay can still try raw sqlite files in the cache.
        }
    }

    private fun traceCenter(fixes: List<GpsFix>): LatLon {
        val latitudes = fixes.map { it.position.latitude }
        val longitudes = fixes.map { it.position.longitude }
        return LatLon(
            latitude = (latitudes.minOrNull()!! + latitudes.maxOrNull()!!) / 2.0,
            longitude = (longitudes.minOrNull()!! + longitudes.maxOrNull()!!) / 2.0,
        )
    }

    private fun traceRadiusMeters(fixes: List<GpsFix>): Double {
        val latitudes = fixes.map { it.position.latitude }
        val longitudes = fixes.map { it.position.longitude }
        val span = Geo.distanceMeters(
            LatLon(latitudes.minOrNull()!!, longitudes.minOrNull()!!),
            LatLon(latitudes.maxOrNull()!!, longitudes.maxOrNull()!!),
        )
        return (span / 2.0 + 2_500.0).coerceIn(4_000.0, 18_000.0)
    }

    private fun ensureTripRecording() {
        if (TripRecorder.isRecording()) {
            ownsTripRecording = false
            return
        }
        TripRecorder.start()
        ownsTripRecording = true
    }

    private fun finishOwnedTripIfNeeded() {
        if (!ownsTripRecording) {
            return
        }
        ownsTripRecording = false
        TripRecorder.finish()
    }
}
