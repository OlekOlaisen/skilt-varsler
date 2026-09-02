package no.skiltvarsler.prefetch

import no.skiltvarsler.tiles.TileCoverage
import no.skiltvarsler.tiles.TileSelector
import org.json.JSONObject

data class ManifestTile(
    val id: String,
    val version: String,
    val file: String,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
) {
    fun toCoverage(): TileCoverage = TileCoverage(
        id = id,
        version = version,
        file = file,
        minLon = minLon,
        minLat = minLat,
        maxLon = maxLon,
        maxLat = maxLat,
    )

    companion object {
        fun from(coverage: TileCoverage): ManifestTile = ManifestTile(
            id = coverage.id,
            version = coverage.version,
            file = coverage.file,
            minLon = coverage.minLon,
            minLat = coverage.minLat,
            maxLon = coverage.maxLon,
            maxLat = coverage.maxLat,
        )
    }
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
                minLon = tile.optDouble("min_lon", 0.0),
                minLat = tile.optDouble("min_lat", 0.0),
                maxLon = tile.optDouble("max_lon", 0.0),
                maxLat = tile.optDouble("max_lat", 0.0),
            )
        }
    }

    fun select(
        tiles: List<ManifestTile>,
        latitude: Double?,
        longitude: Double?,
        bearingDegrees: Double?,
        lookaheadKm: Double = TileSelector.DEFAULT_LOOKAHEAD_KM,
    ): List<ManifestTile> {
        return TileSelector.select(
            tiles = tiles.map { it.toCoverage() },
            latitude = latitude,
            longitude = longitude,
            bearingDegrees = bearingDegrees,
            lookaheadKm = lookaheadKm,
        ).map { ManifestTile.from(it) }
    }

    fun containing(
        tiles: List<ManifestTile>,
        latitude: Double?,
        longitude: Double?,
    ): List<ManifestTile> {
        return TileSelector.containing(
            tiles = tiles.map { it.toCoverage() },
            latitude = latitude,
            longitude = longitude,
        ).map { ManifestTile.from(it) }
    }
}
