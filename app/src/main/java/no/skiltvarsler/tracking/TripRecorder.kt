package no.skiltvarsler.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertCopy
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.matcher.GpsFix
import no.skiltvarsler.matcher.ObjectPayload
import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import kotlin.math.roundToInt

data class TollPassage(
    val name: String,
    val kroner: Double?,
    val nvdbId: Long,
)

data class TripSummary(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceMeters: Double,
    val speedCameras: Int,
    val sectionAtkStarts: Int,
    val tolls: List<TollPassage>,
    val wildlife: Int,
    val railways: Int,
    val ferries: Int,
    val stops: Int,
    val yields: Int,
    val hazards: Int,
    val speedLimits: Int,
    val municipalities: Int,
    val priorityRoads: Int,
    val roadworks: Int,
    val accidents: Int,
    val totalAlerts: Int,
) {
    val durationMs: Long
        get() = (endedAtMs - startedAtMs).coerceAtLeast(0L)

    val tollCount: Int
        get() = tolls.size

    val tollKronerTotal: Double
        get() = tolls.mapNotNull { it.kroner }.sum()
}

/**
 * Accumulates what happened during an active drive for the end-of-trip summary screen.
 */
object TripRecorder {
    private val lock = Any()
    private var active: TripBuilder? = null
    private val lastSummaryState = MutableStateFlow<TripSummary?>(null)
    private val pendingDisplayState = MutableStateFlow(false)

    val lastSummary: StateFlow<TripSummary?> = lastSummaryState.asStateFlow()

    /** True after a trip ends until the summary screen has been opened once. */
    val pendingDisplay: StateFlow<Boolean> = pendingDisplayState.asStateFlow()

    fun currentSummary(): TripSummary? = lastSummaryState.value

    fun isRecording(): Boolean = synchronized(lock) { active != null }

    fun markSummarySeen() {
        pendingDisplayState.value = false
    }

    fun clearSummary() {
        lastSummaryState.value = null
        pendingDisplayState.value = false
    }

    fun start(nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            active = TripBuilder(startedAtMs = nowMs)
        }
    }

    /** Aborts an in-progress trip without publishing a summary (e.g. start failed). */
    fun cancel() {
        synchronized(lock) {
            active = null
        }
    }

    fun recordFix(fix: GpsFix) {
        synchronized(lock) {
            active?.recordFix(fix)
        }
    }

    fun recordAlerts(alerts: List<Alert>) {
        if (alerts.isEmpty()) {
            return
        }
        synchronized(lock) {
            active?.recordAlerts(alerts)
        }
    }

    fun finish(nowMs: Long = System.currentTimeMillis()): TripSummary? {
        val summary = synchronized(lock) {
            val builder = active ?: return@synchronized null
            active = null
            builder.build(endedAtMs = nowMs)
        }
        if (summary != null) {
            lastSummaryState.value = summary
            pendingDisplayState.value = true
        }
        return summary
    }

    private class TripBuilder(
        val startedAtMs: Long,
    ) {
        private var lastPosition: LatLon? = null
        private var firstFixTimeMs: Long? = null
        private var lastFixTimeMs: Long? = null
        private var distanceMeters: Double = 0.0
        private var speedCameras = 0
        private var sectionAtkStarts = 0
        private var wildlife = 0
        private var railways = 0
        private var ferries = 0
        private var stops = 0
        private var yields = 0
        private var hazards = 0
        private var speedLimits = 0
        private var municipalities = 0
        private var priorityRoads = 0
        private var roadworks = 0
        private var accidents = 0
        private var totalAlerts = 0
        private val tolls = ArrayList<TollPassage>()
        private val seenNvdbIds = HashSet<Long>()

        fun recordFix(fix: GpsFix) {
            if (firstFixTimeMs == null) {
                firstFixTimeMs = fix.timeMs
            }
            lastFixTimeMs = fix.timeMs
            val previous = lastPosition
            lastPosition = fix.position
            if (previous == null) {
                return
            }
            if (fix.speedMetersPerSecond < 1.0) {
                return
            }
            val step = Geo.distanceMeters(previous, fix.position)
            if (step in 0.5..80.0) {
                distanceMeters += step
            }
        }

        fun recordAlerts(alerts: List<Alert>) {
            for (alert in alerts) {
                if (!seenNvdbIds.add(alert.nvdbId)) {
                    continue
                }
                totalAlerts += 1
                when (alert.kind) {
                    AlertKind.SPEED_CAMERA -> speedCameras += 1
                    AlertKind.SECTION_ATK_START -> sectionAtkStarts += 1
                    AlertKind.SECTION_ATK_END -> Unit
                    AlertKind.TOLL -> tolls.add(tollPassage(alert))
                    AlertKind.WILDLIFE -> wildlife += 1
                    AlertKind.RAILWAY -> railways += 1
                    AlertKind.FERRY -> ferries += 1
                    AlertKind.STOP -> stops += 1
                    AlertKind.YIELD -> yields += 1
                    AlertKind.HAZARD -> hazards += 1
                    AlertKind.SPEED_LIMIT -> speedLimits += 1
                    AlertKind.MUNICIPALITY -> municipalities += 1
                    AlertKind.PRIORITY_ROAD -> priorityRoads += 1
                    AlertKind.ROADWORK -> roadworks += 1
                    AlertKind.ACCIDENT -> accidents += 1
                }
            }
        }

        fun build(endedAtMs: Long): TripSummary {
            // Prefer GPS timeline for duration (replay wall-clock is much shorter than the drive).
            val gpsStart = firstFixTimeMs
            val gpsEnd = lastFixTimeMs
            val resolvedEndedAtMs = if (gpsStart != null && gpsEnd != null && gpsEnd > gpsStart) {
                startedAtMs + (gpsEnd - gpsStart)
            } else {
                endedAtMs
            }
            return TripSummary(
                startedAtMs = startedAtMs,
                endedAtMs = resolvedEndedAtMs,
                distanceMeters = distanceMeters,
                speedCameras = speedCameras,
                sectionAtkStarts = sectionAtkStarts,
                tolls = tolls.toList(),
                wildlife = wildlife,
                railways = railways,
                ferries = ferries,
                stops = stops,
                yields = yields,
                hazards = hazards,
                speedLimits = speedLimits,
                municipalities = municipalities,
                priorityRoads = priorityRoads,
                roadworks = roadworks,
                accidents = accidents,
                totalAlerts = totalAlerts,
            )
        }

        private fun tollPassage(alert: Alert): TollPassage {
            val parsed = ObjectPayload.parse(alert.payload)
            val name = AlertCopy.titleFor(AlertKind.TOLL, alert.payload)
            val kronerText = AlertCopy.skyttelpassKroner(parsed.extra)
            val kroner = kronerText
                ?.replace(',', '.')
                ?.toDoubleOrNull()
            return TollPassage(name = name, kroner = kroner, nvdbId = alert.nvdbId)
        }
    }
}

fun TripSummary.distanceLabel(): String {
    return if (distanceMeters >= 1000.0) {
        val km = distanceMeters / 1000.0
        String.format(java.util.Locale("nb", "NO"), "%.1f km", km)
    } else {
        "${distanceMeters.roundToInt()} m"
    }
}

fun TripSummary.durationLabel(): String {
    val totalSeconds = (durationMs / 1000L).toInt().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(java.util.Locale("nb", "NO"), "%d t %d min", hours, minutes)
        minutes > 0 -> String.format(java.util.Locale("nb", "NO"), "%d min %d s", minutes, seconds)
        else -> "$seconds s"
    }
}

fun TripSummary.tollKronerLabel(): String {
    if (tolls.none { it.kroner != null }) {
        return "—"
    }
    val amount = tollKronerTotal
    val ore = (amount * 100.0).toLong()
    val kroner = ore / 100
    val remainder = (ore % 100).toInt()
    return if (remainder == 0) {
        "$kroner kr"
    } else {
        String.format(java.util.Locale("nb", "NO"), "%d,%02d kr", kroner, remainder)
    }
}
