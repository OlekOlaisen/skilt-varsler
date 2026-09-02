package no.skiltvarsler.tiles

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val haversine = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

    fun bearingDegrees(from: LatLon, to: LatLon): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun headingDeltaDegrees(a: Double, b: Double): Double {
        val raw = ((a - b + 540.0) % 360.0) - 180.0
        return kotlin.math.abs(raw)
    }

    fun offsetMeters(origin: LatLon, northMeters: Double, eastMeters: Double): LatLon {
        val dLat = northMeters / 111_320.0
        val dLon = eastMeters / (111_320.0 * cos(Math.toRadians(origin.latitude)))
        return LatLon(origin.latitude + dLat, origin.longitude + dLon)
    }

    fun interpolate(from: LatLon, to: LatLon, fraction: Double): LatLon {
        return LatLon(
            latitude = from.latitude + (to.latitude - from.latitude) * fraction,
            longitude = from.longitude + (to.longitude - from.longitude) * fraction,
        )
    }

    fun destination(from: LatLon, bearingDegrees: Double, distanceMeters: Double): LatLon {
        val bearing = Math.toRadians(bearingDegrees)
        val angular = distanceMeters / EARTH_RADIUS_METERS
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = kotlin.math.asin(
            sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing),
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
}

data class PolylineHit(
    val distanceMeters: Double,
    val fractionAlong: Double,
    val point: LatLon,
    val segmentBearing: Double,
)

fun closestPointOnPolyline(point: LatLon, polyline: List<LatLon>): PolylineHit {
    require(polyline.size >= 2) { "polyline needs at least two points" }
    var best: PolylineHit? = null
    var traveled = 0.0
    var total = 0.0
    for (index in 0 until polyline.lastIndex) {
        total += Geo.distanceMeters(polyline[index], polyline[index + 1])
    }
    if (total == 0.0) {
        return PolylineHit(Geo.distanceMeters(point, polyline.first()), 0.0, polyline.first(), 0.0)
    }
    for (index in 0 until polyline.lastIndex) {
        val start = polyline[index]
        val end = polyline[index + 1]
        val segmentLength = Geo.distanceMeters(start, end)
        val hit = closestPointOnSegment(point, start, end)
        val fraction = if (total == 0.0) 0.0 else (traveled + hit.second * segmentLength) / total
        val candidate = PolylineHit(
            distanceMeters = hit.first,
            fractionAlong = fraction.coerceIn(0.0, 1.0),
            point = interpolateLatLon(start, end, hit.second),
            segmentBearing = Geo.bearingDegrees(start, end),
        )
        if (best == null || candidate.distanceMeters < best.distanceMeters) {
            best = candidate
        }
        traveled += segmentLength
    }
    return best!!
}

private fun interpolateLatLon(start: LatLon, end: LatLon, t: Double): LatLon {
    return LatLon(
        latitude = start.latitude + (end.latitude - start.latitude) * t,
        longitude = start.longitude + (end.longitude - start.longitude) * t,
    )
}

private fun closestPointOnSegment(
    point: LatLon,
    start: LatLon,
    end: LatLon,
): Pair<Double, Double> {
    val midLat = Math.toRadians((start.latitude + end.latitude) / 2.0)
    val metersPerDegLat = 111_320.0
    val metersPerDegLon = 111_320.0 * cos(midLat)
    val ax = start.longitude * metersPerDegLon
    val ay = start.latitude * metersPerDegLat
    val bx = end.longitude * metersPerDegLon
    val by = end.latitude * metersPerDegLat
    val px = point.longitude * metersPerDegLon
    val py = point.latitude * metersPerDegLat
    val abx = bx - ax
    val aby = by - ay
    val lengthSq = abx * abx + aby * aby
    val t = if (lengthSq == 0.0) {
        0.0
    } else {
        ((px - ax) * abx + (py - ay) * aby) / lengthSq
    }.coerceIn(0.0, 1.0)
    val cx = ax + t * abx
    val cy = ay + t * aby
    val dx = px - cx
    val dy = py - cy
    return sqrt(dx * dx + dy * dy) to t
}
