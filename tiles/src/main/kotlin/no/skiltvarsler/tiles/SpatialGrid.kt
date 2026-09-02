package no.skiltvarsler.tiles

import kotlin.math.cos
import kotlin.math.floor

class SpatialGrid(
    links: Collection<RoadLink>,
    private val cellMeters: Double = 200.0,
) {
    private val cells = HashMap<Long, MutableList<RoadLink>>()

    init {
        for (link in links) {
            if (!link.matchable || link.points.size < 2) continue
            for (point in link.points) {
                val bucket = cells.getOrPut(cellKey(point.latitude, point.longitude)) { mutableListOf() }
                if (bucket.lastOrNull()?.id != link.id) {
                    bucket.add(link)
                }
            }
        }
    }

    fun query(latitude: Double, longitude: Double, radiusMeters: Double): List<RoadLink> {
        val latSpan = radiusMeters / 111_320.0
        val lonSpan = radiusMeters / (111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.2))
        val minX = xIndex(longitude - lonSpan)
        val maxX = xIndex(longitude + lonSpan)
        val minY = yIndex(latitude - latSpan)
        val maxY = yIndex(latitude + latSpan)
        val seen = HashSet<Long>()
        val result = ArrayList<RoadLink>()
        var x = minX
        while (x <= maxX) {
            var y = minY
            while (y <= maxY) {
                val bucket = cells[pack(x, y)]
                if (bucket != null) {
                    for (link in bucket) {
                        if (seen.add(link.id)) result.add(link)
                    }
                }
                y += 1
            }
            x += 1
        }
        return result
    }

    private fun cellKey(latitude: Double, longitude: Double): Long = pack(xIndex(longitude), yIndex(latitude))

    private fun xIndex(longitude: Double): Long = floor(longitude * 111_320.0 / cellMeters).toLong()

    private fun yIndex(latitude: Double): Long = floor(latitude * 111_320.0 / cellMeters).toLong()

    private fun pack(x: Long, y: Long): Long = (x shl 32) xor (y and 0xffffffffL)
}
