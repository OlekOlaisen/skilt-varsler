package no.skiltvarsler.tiles

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TileSelectorTest {
    private val vestby = tile("kommune-3216", minLon = 10.65, minLat = 59.48, maxLon = 10.86, maxLat = 59.69)
    private val asKommune = tile("kommune-3220", minLon = 10.70, minLat = 59.64, maxLon = 10.90, maxLat = 59.78)
    private val bergen = tile("kommune-4601", minLon = 5.20, minLat = 60.25, maxLon = 5.50, maxLat = 60.50)

    @Test
    fun selectsCurrentKommuneAndNeighborNotTheWholeCountry() {
        val chosen = TileSelector.select(
            tiles = listOf(vestby, asKommune, bergen),
            latitude = 59.58,
            longitude = 10.75,
            bearingDegrees = null,
        )
        assertThat(chosen.map { it.id }).containsExactly("kommune-3216", "kommune-3220")
        assertThat(chosen.map { it.id }).doesNotContain("kommune-4601")
    }

    @Test
    fun emptyWhenGpsIsOutsideAllTiles() {
        val chosen = TileSelector.select(
            tiles = listOf(vestby, bergen),
            latitude = 63.43,
            longitude = 10.40,
            bearingDegrees = 270.0,
        )
        assertThat(chosen).isEmpty()
    }

    @Test
    fun emptyWithoutGpsSoPeriodicJobsDoNotDownloadNorway() {
        val chosen = TileSelector.select(
            tiles = listOf(vestby, asKommune, bergen),
            latitude = null,
            longitude = null,
            bearingDegrees = null,
        )
        assertThat(chosen).isEmpty()
    }

    @Test
    fun containingIgnoresNeighborsAndWorldSizedBoxes() {
        val world = tile("broken", minLon = -180.0, minLat = -90.0, maxLon = 180.0, maxLat = 90.0)
        val chosen = TileSelector.containing(
            tiles = listOf(vestby, asKommune, bergen, world),
            latitude = 59.58,
            longitude = 10.75,
        )
        assertThat(chosen.map { it.id }).containsExactly("kommune-3216")
    }

    @Test
    fun lookaheadAddsKommuneAlongBearing() {
        val west = tile("kommune-west", minLon = 10.20, minLat = 59.50, maxLon = 10.40, maxLat = 59.70)
        val chosen = TileSelector.select(
            tiles = listOf(vestby, west, bergen),
            latitude = 59.58,
            longitude = 10.75,
            bearingDegrees = 270.0,
        )
        assertThat(chosen.map { it.id }).contains("kommune-3216")
        assertThat(chosen.map { it.id }).contains("kommune-west")
        assertThat(chosen.map { it.id }).doesNotContain("kommune-4601")
    }

    private fun tile(
        id: String,
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double,
    ): TileCoverage = TileCoverage(
        id = id,
        version = "1",
        file = "$id.sqlite",
        minLon = minLon,
        minLat = minLat,
        maxLon = maxLon,
        maxLat = maxLat,
    )
}
