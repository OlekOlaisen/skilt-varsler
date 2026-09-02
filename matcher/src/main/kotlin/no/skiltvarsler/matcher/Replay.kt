package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadLink
import no.skiltvarsler.tiles.TravelDirection

object Replay {
    fun alongLink(
        link: RoadLink,
        direction: TravelDirection,
        speedMetersPerSecond: Double,
        startTimeMs: Long = 0L,
        hopOffsets: Map<Int, Pair<Double, Double>> = emptyMap(),
    ): List<GpsFix> {
        require(link.points.size >= 2)
        val total = (0 until link.points.lastIndex).sumOf {
            Geo.distanceMeters(link.points[it], link.points[it + 1])
        }
        val bearing = if (direction == TravelDirection.MED) {
            Geo.bearingDegrees(link.points.first(), link.points.last())
        } else {
            Geo.bearingDegrees(link.points.last(), link.points.first())
        }
        val duration = (total / speedMetersPerSecond).toInt().coerceAtLeast(2)
        return (0..duration).map { second ->
            val distance = (second * speedMetersPerSecond).coerceAtMost(total)
            val along = if (direction == TravelDirection.MED) distance else total - distance
            var remaining = along
            var point = link.points.first()
            for (index in 0 until link.points.lastIndex) {
                val start = link.points[index]
                val end = link.points[index + 1]
                val length = Geo.distanceMeters(start, end)
                if (remaining <= length || index == link.points.lastIndex - 1) {
                    val t = if (length == 0.0) 0.0 else (remaining / length).coerceIn(0.0, 1.0)
                    point = Geo.interpolate(start, end, t)
                    break
                }
                remaining -= length
            }
            val hop = hopOffsets[second]
            val hopped = if (hop != null) Geo.offsetMeters(point, hop.first, hop.second) else point
            GpsFix(
                timeMs = startTimeMs + second * 1000L,
                position = hopped,
                accuracyMeters = if (hop != null) 12.0 else 4.0,
                speedMetersPerSecond = speedMetersPerSecond,
                bearingDegrees = bearing,
            )
        }
    }

    fun play(engine: AlertEngine, fixes: List<GpsFix>): ReplayResult {
        engine.reset()
        val alerts = ArrayList<Pair<GpsFix, Alert>>()
        val matches = ArrayList<Match>()
        for (fix in fixes) {
            val emitted = engine.update(fix)
            engine.currentMatch()?.let { matches.add(it) }
            for (alert in emitted) {
                alerts.add(fix to alert)
            }
        }
        return ReplayResult(matches, alerts)
    }
}

data class ReplayResult(
    val matches: List<Match>,
    val alerts: List<Pair<GpsFix, Alert>>,
) {
    fun alertsOf(kind: AlertKind): List<Alert> = alerts.map { it.second }.filter { it.kind == kind }
}

fun RoadGraph.e6NorthLink(): RoadLink =
    sequences.getValue(SyntheticGraph.SEQ_E6_NORTH).links.first()

fun RoadGraph.e6SouthLink(): RoadLink =
    sequences.getValue(SyntheticGraph.SEQ_E6_SOUTH).links.first()
