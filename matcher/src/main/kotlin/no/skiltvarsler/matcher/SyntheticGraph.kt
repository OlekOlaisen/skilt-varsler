package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.KommunePolygon
import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadGraphBuilder
import no.skiltvarsler.tiles.RoadLink
import no.skiltvarsler.tiles.RoadNode
import no.skiltvarsler.tiles.RoadObject
import no.skiltvarsler.tiles.RoadObjectType
import no.skiltvarsler.tiles.SpeedInterval
import no.skiltvarsler.tiles.TravelDirection

object SyntheticGraph {
    const val SEQ_E6_NORTH = 100L
    const val SEQ_E6_SOUTH = 200L
    const val SEQ_LOCAL = 300L
    const val ATK_ID = 900001L
    const val ATK_POS = 0.25
    const val LENGTH_METERS = 2000.0

    private val origin = LatLon(latitude = 59.5800, longitude = 10.7500)

    fun e6VestbyLike(): RoadGraph {
        val northMid = Geo.offsetMeters(origin, northMeters = LENGTH_METERS / 2, eastMeters = 0.0)
        val northEnd = Geo.offsetMeters(origin, northMeters = LENGTH_METERS, eastMeters = 0.0)
        val southOrigin = Geo.offsetMeters(origin, northMeters = 0.0, eastMeters = -30.0)
        val southEnd = Geo.offsetMeters(southOrigin, northMeters = LENGTH_METERS, eastMeters = 0.0)
        val localOrigin = Geo.offsetMeters(origin, northMeters = 0.0, eastMeters = 30.0)
        val localEnd = Geo.offsetMeters(localOrigin, northMeters = LENGTH_METERS, eastMeters = 0.0)

        val builder = RoadGraphBuilder().apply {
            tileId = "fixture-e6-vestby-like"
            version = "test"
        }
        builder.addNode(RoadNode(1, origin))
        builder.addNode(RoadNode(7, northMid))
        builder.addNode(RoadNode(2, northEnd))
        builder.addNode(RoadNode(3, southEnd))
        builder.addNode(RoadNode(4, southOrigin))
        builder.addNode(RoadNode(5, localOrigin))
        builder.addNode(RoadNode(6, localEnd))

        builder.addLink(
            RoadLink(
                id = 10,
                sequenceId = SEQ_E6_NORTH,
                linkNumber = 1,
                startNodeId = 1,
                endNodeId = 7,
                startPos = 0.0,
                endPos = 0.5,
                lengthMeters = LENGTH_METERS / 2,
                typeVeg = "Kanalisert veg",
                matchable = true,
                points = dense(origin, northMid),
                kommune = 3216,
            ),
        )
        builder.addLink(
            RoadLink(
                id = 11,
                sequenceId = SEQ_E6_NORTH,
                linkNumber = 2,
                startNodeId = 7,
                endNodeId = 2,
                startPos = 0.5,
                endPos = 1.0,
                lengthMeters = LENGTH_METERS / 2,
                typeVeg = "Kanalisert veg",
                matchable = true,
                points = dense(northMid, northEnd),
                kommune = 3220,
            ),
        )
        builder.addLink(
            RoadLink(
                id = 20,
                sequenceId = SEQ_E6_SOUTH,
                linkNumber = 1,
                startNodeId = 3,
                endNodeId = 4,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = LENGTH_METERS,
                typeVeg = "Kanalisert veg",
                matchable = true,
                points = dense(southEnd, southOrigin),
                kommune = 3216,
            ),
        )
        builder.addLink(
            RoadLink(
                id = 30,
                sequenceId = SEQ_LOCAL,
                linkNumber = 1,
                startNodeId = 5,
                endNodeId = 6,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = LENGTH_METERS,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(localOrigin, localEnd),
                kommune = 3216,
            ),
        )
        builder.setSequenceLength(SEQ_E6_NORTH, LENGTH_METERS)
        builder.setSequenceLength(SEQ_E6_SOUTH, LENGTH_METERS)
        builder.setSequenceLength(SEQ_LOCAL, LENGTH_METERS)
        builder.addKommune(
            KommunePolygon(
                kommune = 3216,
                name = "Vestby",
                ring = listOf(origin, northEnd, localEnd, origin),
            ),
        )
        builder.addKommune(
            KommunePolygon(
                kommune = 3220,
                name = "Ås",
                ring = listOf(northMid, northEnd, Geo.offsetMeters(northEnd, 0.0, 40.0), northMid),
            ),
        )
        builder.addSpeed(
            SpeedInterval(SEQ_E6_NORTH, 0.0, 0.5, 80, TravelDirection.MED),
        )
        builder.addSpeed(
            SpeedInterval(SEQ_E6_NORTH, 0.5, 1.0, 60, TravelDirection.MED),
        )
        builder.addObject(
            RoadObject(
                nvdbId = ATK_ID,
                type = RoadObjectType.SPEED_CAMERA,
                sequenceId = SEQ_E6_NORTH,
                fromPos = ATK_POS,
                toPos = ATK_POS,
                direction = TravelDirection.MED,
                payload = "E6 Vestby nord",
            ),
        )
        return builder.build()
    }

    const val SEQ_MAIN = 400L
    const val SEQ_SIDE = 401L
    const val SEQ_CONTINUE = 402L
    const val SIDE_HAZARD_ID = 910001L
    const val CONTINUE_HAZARD_ID = 910002L
    const val MAIN_LENGTH_METERS = 400.0
    const val SIDE_LENGTH_METERS = 300.0

    fun mainRoadWithSideStreet(): RoadGraph {
        val junction = Geo.offsetMeters(origin, northMeters = MAIN_LENGTH_METERS, eastMeters = 0.0)
        val northEnd = Geo.offsetMeters(junction, northMeters = MAIN_LENGTH_METERS, eastMeters = 0.0)
        val eastEnd = Geo.offsetMeters(junction, northMeters = 0.0, eastMeters = SIDE_LENGTH_METERS)
        val builder = RoadGraphBuilder().apply {
            tileId = "fixture-main-side"
            version = "test"
        }
        builder.addNode(RoadNode(1, origin))
        builder.addNode(RoadNode(2, junction))
        builder.addNode(RoadNode(3, northEnd))
        builder.addNode(RoadNode(4, eastEnd))
        builder.addLink(
            RoadLink(
                id = 40,
                sequenceId = SEQ_MAIN,
                linkNumber = 1,
                startNodeId = 1,
                endNodeId = 2,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = MAIN_LENGTH_METERS,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(origin, junction),
            ),
        )
        builder.addLink(
            RoadLink(
                id = 41,
                sequenceId = SEQ_CONTINUE,
                linkNumber = 1,
                startNodeId = 2,
                endNodeId = 3,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = MAIN_LENGTH_METERS,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(junction, northEnd),
            ),
        )
        builder.addLink(
            RoadLink(
                id = 42,
                sequenceId = SEQ_SIDE,
                linkNumber = 1,
                startNodeId = 2,
                endNodeId = 4,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = SIDE_LENGTH_METERS,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(junction, eastEnd),
            ),
        )
        builder.setSequenceLength(SEQ_MAIN, MAIN_LENGTH_METERS)
        builder.setSequenceLength(SEQ_CONTINUE, MAIN_LENGTH_METERS)
        builder.setSequenceLength(SEQ_SIDE, SIDE_LENGTH_METERS)
        builder.addObject(
            RoadObject(
                nvdbId = SIDE_HAZARD_ID,
                type = RoadObjectType.HAZARD,
                sequenceId = SEQ_SIDE,
                fromPos = 80.0 / SIDE_LENGTH_METERS,
                toPos = 80.0 / SIDE_LENGTH_METERS,
                direction = TravelDirection.MED,
                payload = "106.1",
            ),
        )
        builder.addObject(
            RoadObject(
                nvdbId = CONTINUE_HAZARD_ID,
                type = RoadObjectType.HAZARD,
                sequenceId = SEQ_CONTINUE,
                fromPos = 80.0 / MAIN_LENGTH_METERS,
                toPos = 80.0 / MAIN_LENGTH_METERS,
                direction = TravelDirection.MED,
                payload = "108",
            ),
        )
        return builder.build()
    }

    private fun dense(from: LatLon, to: LatLon, steps: Int = 20): List<LatLon> {
        return (0..steps).map { step ->
            Geo.interpolate(from, to, step.toDouble() / steps)
        }
    }
}
