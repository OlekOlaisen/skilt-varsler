package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadGraphBuilder
import no.skiltvarsler.tiles.RoadLink
import no.skiltvarsler.tiles.RoadNode

/**
 * Builds a minimal matchable graph from a recorded GPS polyline so real traces can be
 * replayed without shipping full kommune tiles into unit tests.
 */
object TraceGraph {
    const val SEQ_MAIN = 1L
    const val SEQ_SIDE = 2L

    fun fromFixes(
        fixes: List<GpsFix>,
        tileId: String = "gps-trace",
        withSideTemptation: Boolean = true,
    ): RoadGraph {
        require(fixes.size >= 2)
        val points = densify(fixes.map { it.position })
        val lengthMeters = (0 until points.lastIndex).sumOf {
            Geo.distanceMeters(points[it], points[it + 1])
        }.coerceAtLeast(1.0)
        val builder = RoadGraphBuilder().apply {
            this.tileId = tileId
            version = "test"
        }
        builder.addNode(RoadNode(1, points.first()))
        builder.addNode(RoadNode(2, points.last()))
        builder.addLink(
            RoadLink(
                id = 10,
                sequenceId = SEQ_MAIN,
                linkNumber = 1,
                startNodeId = 1,
                endNodeId = 2,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = lengthMeters,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = points,
            ),
        )
        builder.setSequenceLength(SEQ_MAIN, lengthMeters)

        if (withSideTemptation) {
            val midIndex = points.size / 2
            val mid = points[midIndex]
            val bearing = Geo.bearingDegrees(points[midIndex - 1], mid)
            val sideEnd = Geo.destination(mid, (bearing + 90.0) % 360.0, 80.0)
            builder.addNode(RoadNode(3, mid))
            builder.addNode(RoadNode(4, sideEnd))
            builder.addLink(
                RoadLink(
                    id = 20,
                    sequenceId = SEQ_SIDE,
                    linkNumber = 1,
                    startNodeId = 3,
                    endNodeId = 4,
                    startPos = 0.0,
                    endPos = 1.0,
                    lengthMeters = 80.0,
                    typeVeg = "Enkel bilveg",
                    matchable = true,
                    points = listOf(mid, sideEnd),
                ),
            )
            builder.setSequenceLength(SEQ_SIDE, 80.0)
        }
        return builder.build()
    }

    private fun densify(points: List<LatLon>): List<LatLon> {
        if (points.size <= 2) {
            return points
        }
        val out = ArrayList<LatLon>(points.size * 2)
        out.add(points.first())
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val length = Geo.distanceMeters(start, end)
            if (length > 40.0) {
                out.add(Geo.interpolate(start, end, 0.5))
            }
            out.add(end)
        }
        return out
    }
}
