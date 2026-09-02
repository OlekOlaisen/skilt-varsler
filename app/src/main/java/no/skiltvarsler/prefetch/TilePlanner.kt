package no.skiltvarsler.prefetch

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

data class ManifestTile(
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

    fun expanded(degrees: Double): ManifestTile = copy(
        minLon = minLon - degrees,
        minLat = minLat - degrees,
        maxLon = maxLon + degrees,
        maxLat = maxLat + degrees,
    )
}

object TilePlanner {
    fun parseManifest(json: JSONObject): List<ManifestTile> {
        val tiles = json.getJSONArray("tiles")
        return (0 until tiles.length()).map { index ->
            val tile = tiles.getJSONObject(index)
            ManifestTile(
                id = tile.getString("id"),
                version = tile.optString("version", json.optString("version")),
                file = tile.getString("file"),
                minLon = tile.optDouble("min_lon", -180.0),
                minLat = tile.optDouble("min_lat", -90.0),
                maxLon = tile.optDouble("max_lon", 180.0),
                maxLat = tile.optDouble("max_lat", 90.0),
            )
        }
    }

    fun select(
        tiles: List<ManifestTile>,
        latitude: Double?,
        longitude: Double?,
        bearingDegrees: Double?,
        lookaheadKm: Double = 40.0,
    ): List<ManifestTile> {
        if (tiles.size <= 2 || latitude == null || longitude == null) {
            return tiles
        }
        val here = tiles.filter { it.contains(latitude, longitude) }
        val neighborPad = 0.18
        val neighbors = tiles.filter { tile ->
            here.any { current ->
                boxesOverlap(current.expanded(neighborPad), tile)
            } || tile.contains(latitude, longitude)
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
        val chosen = (here + neighbors + ahead).distinctBy { it.id }
        return chosen.ifEmpty { tiles }
    }

    private fun boxesOverlap(a: ManifestTile, b: ManifestTile): Boolean {
        return max(a.minLon, b.minLon) <= min(a.maxLon, b.maxLon) &&
            max(a.minLat, b.minLat) <= min(a.maxLat, b.maxLat)
    }
}
