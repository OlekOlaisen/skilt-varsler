package no.skiltvarsler.tiles

import kotlin.math.max
import kotlin.math.min

data class TileCoverage(
    val id: String,
    val version: String,
    val file: String,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        return longitude >= minLon && longitude <= maxLon && latitude >= minLat && latitude <= maxLat
    }

    fun expanded(degrees: Double): TileCoverage = copy(
        minLon = minLon - degrees,
        minLat = minLat - degrees,
        maxLon = maxLon + degrees,
        maxLat = maxLat + degrees,
    )
}

object TileSelector {
    const val NEIGHBOR_PAD_DEGREES = 0.18
    const val DEFAULT_LOOKAHEAD_KM = 40.0

    fun select(
        tiles: List<TileCoverage>,
        latitude: Double?,
        longitude: Double?,
        bearingDegrees: Double?,
        lookaheadKm: Double = DEFAULT_LOOKAHEAD_KM,
    ): List<TileCoverage> {
        if (latitude == null || longitude == null || tiles.isEmpty()) {
            return emptyList()
        }
        val here = tiles.filter { it.contains(latitude, longitude) }
        val neighbors = tiles.filter { tile ->
            here.any { current -> boxesOverlap(current.expanded(NEIGHBOR_PAD_DEGREES), tile) } ||
                tile.contains(latitude, longitude)
        }
        val ahead = if (bearingDegrees == null) {
            emptyList()
        } else {
            val origin = LatLon(latitude, longitude)
            val samples = listOf(10.0, 25.0, lookaheadKm).map { km ->
                Geo.destination(origin, bearingDegrees, km * 1000.0)
            }
            tiles.filter { tile -> samples.any { tile.contains(it.latitude, it.longitude) } }
        }
        return (here + neighbors + ahead).distinctBy { it.id }
    }

    fun coverageKey(tiles: List<TileCoverage>): String {
        return tiles.map { it.id }.sorted().joinToString("+").ifEmpty { "empty" }
    }

    private fun boxesOverlap(left: TileCoverage, right: TileCoverage): Boolean {
        return max(left.minLon, right.minLon) <= min(left.maxLon, right.maxLon) &&
            max(left.minLat, right.minLat) <= min(left.maxLat, right.maxLat)
    }
}
