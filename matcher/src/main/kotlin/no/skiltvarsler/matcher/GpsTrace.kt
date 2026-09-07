package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import kotlin.math.max

/**
 * Loads recorded GPS traces (`lat,lon` per line, `#` comments) into timed [GpsFix] samples.
 * Used for regression tests and on-device replay of fixed routes.
 */
object GpsTrace {
    data class Meta(
        val name: String = "unnamed",
        val fallbackSpeedKmh: Double = 50.0,
    )

    fun parse(
        text: String,
        startTimeMs: Long = 0L,
        defaultAccuracyMeters: Double = 6.0,
    ): Pair<Meta, List<GpsFix>> {
        var name = "unnamed"
        var fallbackSpeedKmh = 50.0
        val points = ArrayList<LatLon>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                continue
            }
            if (line.startsWith("#")) {
                val body = line.removePrefix("#").trim()
                when {
                    body.startsWith("name:", ignoreCase = true) -> {
                        name = body.substringAfter(':').trim()
                    }
                    body.startsWith("fallbackSpeedKmh:", ignoreCase = true) -> {
                        fallbackSpeedKmh = body.substringAfter(':').trim().toDoubleOrNull() ?: fallbackSpeedKmh
                    }
                }
                continue
            }
            val parts = line.split(',', ';', ' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) {
                continue
            }
            val latitude = parts[0].toDoubleOrNull() ?: continue
            val longitude = parts[1].toDoubleOrNull() ?: continue
            points.add(LatLon(latitude, longitude))
        }
        require(points.size >= 2) { "Trace needs at least two points" }
        val fallbackSpeed = max(1.0, fallbackSpeedKmh / 3.6)
        val fixes = ArrayList<GpsFix>(points.size)
        var timeMs = startTimeMs
        for (index in points.indices) {
            val point = points[index]
            val next = points.getOrNull(index + 1)
            val previous = points.getOrNull(index - 1)
            val bearing = when {
                next != null -> Geo.bearingDegrees(point, next)
                previous != null -> Geo.bearingDegrees(previous, point)
                else -> null
            }
            val segmentMeters = when {
                next != null -> Geo.distanceMeters(point, next)
                previous != null -> Geo.distanceMeters(previous, point)
                else -> fallbackSpeed
            }
            val speed = if (segmentMeters < 0.4) {
                max(1.0, fallbackSpeed * 0.35)
            } else {
                fallbackSpeed.coerceIn(1.0, 40.0)
            }
            if (index > 0 && previous != null) {
                val moved = Geo.distanceMeters(previous, point)
                val dtSeconds = max(0.4, moved / max(1.0, speed))
                timeMs += (dtSeconds * 1000.0).toLong()
            }
            fixes.add(
                GpsFix(
                    timeMs = timeMs,
                    position = point,
                    accuracyMeters = defaultAccuracyMeters,
                    speedMetersPerSecond = speed,
                    bearingDegrees = bearing,
                ),
            )
        }
        return Meta(name = name, fallbackSpeedKmh = fallbackSpeedKmh) to fixes
    }

    fun withTunnelMultipath(
        fixes: List<GpsFix>,
        fromIndex: Int,
        sampleCount: Int,
        offsetNorthMeters: Double,
        offsetEastMeters: Double,
        accuracyMeters: Double = 55.0,
    ): List<GpsFix> {
        require(fromIndex in fixes.indices)
        require(sampleCount > 0)
        return fixes.mapIndexed { index, fix ->
            if (index in fromIndex until (fromIndex + sampleCount)) {
                fix.copy(
                    position = Geo.offsetMeters(fix.position, offsetNorthMeters, offsetEastMeters),
                    accuracyMeters = accuracyMeters,
                )
            } else {
                fix
            }
        }
    }
}
