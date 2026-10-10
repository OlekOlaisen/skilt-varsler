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
    fun hasUsableBbox(): Boolean {
        val lonSpan = maxLon - minLon
        val latSpan = maxLat - minLat
        return lonSpan in 0.001..6.0 && latSpan in 0.001..6.0
    }

    fun contains(latitude: Double, longitude: Double): Boolean {
        if (!hasUsableBbox()) return false
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
    /** ~6–7 km — enough for a border kommune, not half of Viken. */
    const val NEIGHBOR_PAD_DEGREES = 0.06
    const val DEFAULT_LOOKAHEAD_KM = 15.0
    const val DEFAULT_WINDOW_PAD_METERS = 1_500.0
    const val MAX_NEIGHBOR_TILES = 4

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
        val here = tiles.filter { tile -> tile.contains(latitude, longitude) }
        val neighborCandidates = tiles.filter { tile ->
            here.any { current -> boxesOverlap(current.expanded(NEIGHBOR_PAD_DEGREES), tile) }
        }
        val hereIds = here.map { tile -> tile.id }.toSet()
        val neighbors = neighborCandidates
            .filter { tile -> tile.id !in hereIds }
            .sortedBy { tile -> distanceMetersToTile(latitude, longitude, tile) }
            .take(MAX_NEIGHBOR_TILES)
        val ahead = if (bearingDegrees == null) {
            emptyList()
        } else {
            val origin = LatLon(latitude, longitude)
            val sampleKm = listOf(
                min(5.0, lookaheadKm),
                min(10.0, lookaheadKm),
                lookaheadKm,
            ).distinct()
            val samples = sampleKm.map { km ->
                Geo.destination(origin, bearingDegrees, km * 1000.0)
            }
            tiles.filter { tile ->
                samples.any { sample -> tile.contains(sample.latitude, sample.longitude) }
            }
        }
        return (here + neighbors + ahead).distinctBy { tile -> tile.id }
    }

    private fun distanceMetersToTile(latitude: Double, longitude: Double, tile: TileCoverage): Double {
        val centerLat = (tile.minLat + tile.maxLat) / 2.0
        val centerLon = (tile.minLon + tile.maxLon) / 2.0
        return Geo.distanceMeters(LatLon(latitude, longitude), LatLon(centerLat, centerLon))
    }

    fun containing(
        tiles: List<TileCoverage>,
        latitude: Double?,
        longitude: Double?,
    ): List<TileCoverage> {
        if (latitude == null || longitude == null) return emptyList()
        return tiles.filter { it.contains(latitude, longitude) }
    }

    /**
     * Tiles that reach into the match window around the position, so the graph can hold roads
     * from a neighbouring kommune before the border is crossed. The pad makes a tile join the
     * window slightly before its roads are needed; loading a tile without roads in range is cheap.
     */
    fun intersectingWindow(
        tiles: List<TileCoverage>,
        latitude: Double?,
        longitude: Double?,
        radiusMeters: Double,
        padMeters: Double = DEFAULT_WINDOW_PAD_METERS,
    ): List<TileCoverage> {
        if (latitude == null || longitude == null) return emptyList()
        val reachMeters = radiusMeters + padMeters
        val origin = LatLon(latitude, longitude)
        val southWest = Geo.offsetMeters(origin, northMeters = -reachMeters, eastMeters = -reachMeters)
        val northEast = Geo.offsetMeters(origin, northMeters = reachMeters, eastMeters = reachMeters)
        return tiles.filter { tile ->
            tile.hasUsableBbox() &&
                boundsOverlap(
                    tile = tile,
                    minLon = southWest.longitude,
                    minLat = southWest.latitude,
                    maxLon = northEast.longitude,
                    maxLat = northEast.latitude,
                )
        }
    }

    fun coverageKey(tiles: List<TileCoverage>): String {
        return tiles.map { it.id }.sorted().joinToString("+").ifEmpty { "empty" }
    }

    private fun boxesOverlap(left: TileCoverage, right: TileCoverage): Boolean {
        return boundsOverlap(
            tile = right,
            minLon = left.minLon,
            minLat = left.minLat,
            maxLon = left.maxLon,
            maxLat = left.maxLat,
        )
    }

    private fun boundsOverlap(
        tile: TileCoverage,
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double,
    ): Boolean {
        return max(tile.minLon, minLon) <= min(tile.maxLon, maxLon) &&
            max(tile.minLat, minLat) <= min(tile.maxLat, maxLat)
    }
}
