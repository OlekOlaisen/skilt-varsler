package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.closestPointOnPolyline
import kotlin.math.abs

/**
 * Live traffic situation from DATEX (or a fixture), independent of NVDB skiltplater.
 * Geometry is a point or polyline in WGS84; the phone never talks to DATEX itself.
 */
data class TrafficSituation(
    val id: String,
    val type: SituationType,
    val title: String,
    val description: String = "",
    val points: List<LatLon>,
) {
    init {
        require(points.isNotEmpty()) { "situation needs at least one point" }
    }

    val alertId: Long
        get() = stableAlertId(id)

    val payload: String
        get() {
            val label = title.ifBlank { type.defaultTitle }
            return when {
                description.isBlank() -> "${type.wireCode}|$label"
                else -> "${type.wireCode}|$label|$description"
            }
        }
}

enum class SituationType(val wireCode: String, val defaultTitle: String) {
    ROADWORK(wireCode = "roadwork", defaultTitle = "Veiarbeid"),
    CLOSURE(wireCode = "closure", defaultTitle = "Stengt veg"),
    ACCIDENT(wireCode = "accident", defaultTitle = "Trafikkulykke"),
    ;

    companion object {
        fun fromWire(raw: String): SituationType? {
            val key = raw.trim().lowercase()
            return entries.firstOrNull { it.wireCode == key || it.name.equals(key, ignoreCase = true) }
        }
    }
}

data class SituationHit(
    val situation: TrafficSituation,
    val metersAway: Double,
    val approachPoint: LatLon,
    val segmentBearing: Double?,
)

object SituationIndex {
    /** Max cross-track distance to treat a situation as "on our road corridor". */
    const val CORRIDOR_METERS = 90.0

    fun nearestAhead(
        position: LatLon,
        bearingDegrees: Double?,
        situations: List<TrafficSituation>,
        maxMeters: Double = 600.0,
    ): SituationHit? {
        var best: SituationHit? = null
        for (situation in situations) {
            val hit = hitFor(position, situation) ?: continue
            if (hit.metersAway > maxMeters) {
                continue
            }
            if (bearingDegrees != null) {
                val toPoint = Geo.bearingDegrees(position, hit.approachPoint)
                if (Geo.headingDeltaDegrees(bearingDegrees, toPoint) > 95.0) {
                    continue
                }
            }
            if (best == null || hit.metersAway < best.metersAway) {
                best = hit
            }
        }
        return best
    }

    fun near(
        latitude: Double,
        longitude: Double,
        situations: List<TrafficSituation>,
        radiusMeters: Double,
    ): List<TrafficSituation> {
        val origin = LatLon(latitude, longitude)
        return situations.filter { situation ->
            situation.points.any { point ->
                Geo.distanceMeters(origin, point) <= radiusMeters
            }
        }
    }

    private fun hitFor(position: LatLon, situation: TrafficSituation): SituationHit? {
        if (situation.points.size == 1) {
            val point = situation.points.first()
            return SituationHit(
                situation = situation,
                metersAway = Geo.distanceMeters(position, point),
                approachPoint = point,
                segmentBearing = null,
            )
        }
        val polylineHit = closestPointOnPolyline(position, situation.points)
        if (polylineHit.distanceMeters > CORRIDOR_METERS) {
            return null
        }
        return SituationHit(
            situation = situation,
            metersAway = Geo.distanceMeters(position, polylineHit.point),
            approachPoint = polylineHit.point,
            segmentBearing = polylineHit.segmentBearing,
        )
    }
}

fun stableAlertId(id: String): Long {
    var hash = 0xcbf29ce484222325uL
    for (character in id) {
        hash = hash xor character.code.toULong()
        hash *= 0x100000001b3uL
    }
    val signed = hash.toLong()
    return if (signed == Long.MIN_VALUE) abs(signed + 1) else abs(signed)
}
