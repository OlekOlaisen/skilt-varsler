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
    const val SEQ_EAST = 410L
    const val SEQ_CONNECTOR = 411L
    const val SEQ_SOUTH = 412L
    const val SIDE_HAZARD_ID = 910001L
    const val CONTINUE_HAZARD_ID = 910002L
    const val SIDE_PRIORITY_ID = 910003L
    const val MAIN_PRIORITY_SIGN_ID = 910004L
    const val SIDE_PRIORITY_REMINDER_ID = 910005L
    const val MAIN_PRIORITY_STRETCH_ID = 910008L
    const val CONTINUE_PRIORITY_STRETCH_ID = 910009L
    const val MAIN_LENGTH_METERS = 400.0
    const val SIDE_LENGTH_METERS = 300.0
    const val CONNECTOR_LENGTH_METERS = 40.0

    const val SIDE_STOP_ID = 910006L
    const val SIDE_YIELD_ID = 910007L
    const val APPROACH_ROUNDABOUT_ID = 910010L
    const val CONTINUE_ROUNDABOUT_ID = 910011L
    const val SIDE_ROUNDABOUT_ID = 910012L
    const val SEQ_SPEED_PLATE = 420L
    const val SPEED_PLATE_40_ID = 910040L
    const val CONTINUE_SPEED_PLATE_40_ID = 910041L
    const val SPEED_PLATE_POS = 0.55
    const val CONTINUE_SPEED_PLATE_POS = 0.20
    const val ANGLED_SIDE_DEGREES = 30.0

    fun mainRoadWithSideStreet(): RoadGraph {
        return junctionGraph(sideBearingDegrees = 90.0, includeJunctionControl = false)
    }

    /**
     * Approach (MAIN) plus other-arm 406 plates on CONTINUE and SIDE — models NVDB
     * putting one roundabout plate on every arm of the same junction.
     */
    fun mainRoadWithRoundaboutArms(): RoadGraph {
        return junctionGraph(
            sideBearingDegrees = 45.0,
            includeJunctionControl = false,
            includeRoundaboutArms = true,
        )
    }

    /** Same junction as [mainRoadWithSideStreet], plus a repeated 40 plate on the continue link. */
    fun mainRoadWithContinueSpeedPlate(): RoadGraph {
        return junctionGraph(
            sideBearingDegrees = 90.0,
            includeJunctionControl = false,
            continueSpeedPlate = true,
        )
    }

    /**
     * Stretch signed 30 in fartsdata but with a physical 362.40 plate mid-link — the Østensjø
     * mismatch case where the plate must win at the sign.
     */
    fun roadWithFartsdata30AndPlate40(): RoadGraph {
        val end = Geo.offsetMeters(origin, northMeters = MAIN_LENGTH_METERS, eastMeters = 0.0)
        val builder = RoadGraphBuilder().apply {
            tileId = "fixture-fartsdata-vs-plate"
            version = "test"
        }
        builder.addNode(RoadNode(1, origin))
        builder.addNode(RoadNode(2, end))
        builder.addLink(
            RoadLink(
                id = 70,
                sequenceId = SEQ_SPEED_PLATE,
                linkNumber = 1,
                startNodeId = 1,
                endNodeId = 2,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = MAIN_LENGTH_METERS,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(origin, end),
            ),
        )
        builder.setSequenceLength(SEQ_SPEED_PLATE, MAIN_LENGTH_METERS)
        builder.addSpeed(SpeedInterval(SEQ_SPEED_PLATE, 0.0, 1.0, 30, TravelDirection.MED))
        builder.addObject(
            RoadObject(
                nvdbId = SPEED_PLATE_40_ID,
                type = RoadObjectType.SPEED_LIMIT,
                sequenceId = SEQ_SPEED_PLATE,
                fromPos = SPEED_PLATE_POS,
                toPos = SPEED_PLATE_POS,
                direction = TravelDirection.MED,
                payload = "40",
            ),
        )
        return builder.build()
    }

    /**
     * Northbound trunk that bends east at the far end, with a side street leaving east mid-trunk.
     * Sequence-end bearing aligns with the side street; only the mid-trunk leave heading is a turn.
     */
    fun northRoadBendingEastWithMidSideStreet(): RoadGraph {
        val leaveNode = Geo.offsetMeters(origin, northMeters = MAIN_LENGTH_METERS, eastMeters = 0.0)
        val northFar = Geo.offsetMeters(leaveNode, northMeters = MAIN_LENGTH_METERS, eastMeters = 0.0)
        val eastEnd = Geo.offsetMeters(northFar, northMeters = 0.0, eastMeters = MAIN_LENGTH_METERS)
        val sideEnd = Geo.offsetMeters(leaveNode, northMeters = 0.0, eastMeters = SIDE_LENGTH_METERS)
        val segmentMeters = MAIN_LENGTH_METERS
        val builder = RoadGraphBuilder().apply {
            tileId = "fixture-bend-mid-side"
            version = "test"
        }
        builder.addNode(RoadNode(1, origin))
        builder.addNode(RoadNode(2, leaveNode))
        builder.addNode(RoadNode(3, northFar))
        builder.addNode(RoadNode(4, eastEnd))
        builder.addNode(RoadNode(5, sideEnd))
        builder.addLink(
            RoadLink(
                id = 60,
                sequenceId = SEQ_MAIN,
                linkNumber = 1,
                startNodeId = 1,
                endNodeId = 2,
                startPos = 0.0,
                endPos = 1.0 / 3.0,
                lengthMeters = segmentMeters,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(origin, leaveNode),
            ),
        )
        builder.addLink(
            RoadLink(
                id = 61,
                sequenceId = SEQ_MAIN,
                linkNumber = 2,
                startNodeId = 2,
                endNodeId = 3,
                startPos = 1.0 / 3.0,
                endPos = 2.0 / 3.0,
                lengthMeters = segmentMeters,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(leaveNode, northFar),
            ),
        )
        builder.addLink(
            RoadLink(
                id = 62,
                sequenceId = SEQ_MAIN,
                linkNumber = 3,
                startNodeId = 3,
                endNodeId = 4,
                startPos = 2.0 / 3.0,
                endPos = 1.0,
                lengthMeters = segmentMeters,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(northFar, eastEnd),
            ),
        )
        builder.addLink(
            RoadLink(
                id = 63,
                sequenceId = SEQ_SIDE,
                linkNumber = 1,
                startNodeId = 2,
                endNodeId = 5,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = SIDE_LENGTH_METERS,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(leaveNode, sideEnd),
            ),
        )
        builder.setSequenceLength(SEQ_MAIN, segmentMeters * 3.0)
        builder.setSequenceLength(SEQ_SIDE, SIDE_LENGTH_METERS)
        builder.addSpeed(SpeedInterval(SEQ_MAIN, 0.0, 1.0, 40, TravelDirection.MED))
        builder.addSpeed(SpeedInterval(SEQ_SIDE, 0.0, 1.0, 40, TravelDirection.MED))
        return builder.build()
    }

    /**
     * Eastbound signed 40 → short unsigned connector (roundabout-like) → southbound signed 40.
     * Models the Østmarkveien → roundabout → General Ruges vei speed gap.
     */
    fun eastRoadViaUnsignedConnectorToSouth(): RoadGraph {
        val eastEnd = Geo.offsetMeters(origin, northMeters = 0.0, eastMeters = MAIN_LENGTH_METERS)
        val southStart = Geo.offsetMeters(eastEnd, northMeters = -CONNECTOR_LENGTH_METERS, eastMeters = CONNECTOR_LENGTH_METERS)
        val southEnd = Geo.offsetMeters(southStart, northMeters = -MAIN_LENGTH_METERS, eastMeters = 0.0)
        val builder = RoadGraphBuilder().apply {
            tileId = "fixture-unsigned-connector"
            version = "test"
        }
        builder.addNode(RoadNode(1, origin))
        builder.addNode(RoadNode(2, eastEnd))
        builder.addNode(RoadNode(3, southStart))
        builder.addNode(RoadNode(4, southEnd))
        builder.addLink(
            RoadLink(
                id = 50,
                sequenceId = SEQ_EAST,
                linkNumber = 1,
                startNodeId = 1,
                endNodeId = 2,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = MAIN_LENGTH_METERS,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(origin, eastEnd),
            ),
        )
        builder.addLink(
            RoadLink(
                id = 51,
                sequenceId = SEQ_CONNECTOR,
                linkNumber = 1,
                startNodeId = 2,
                endNodeId = 3,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = CONNECTOR_LENGTH_METERS,
                typeVeg = "Rundkjøring",
                matchable = true,
                points = dense(eastEnd, southStart),
            ),
        )
        builder.addLink(
            RoadLink(
                id = 52,
                sequenceId = SEQ_SOUTH,
                linkNumber = 1,
                startNodeId = 3,
                endNodeId = 4,
                startPos = 0.0,
                endPos = 1.0,
                lengthMeters = MAIN_LENGTH_METERS,
                typeVeg = "Enkel bilveg",
                matchable = true,
                points = dense(southStart, southEnd),
            ),
        )
        builder.setSequenceLength(SEQ_EAST, MAIN_LENGTH_METERS)
        builder.setSequenceLength(SEQ_CONNECTOR, CONNECTOR_LENGTH_METERS)
        builder.setSequenceLength(SEQ_SOUTH, MAIN_LENGTH_METERS)
        builder.addSpeed(SpeedInterval(SEQ_EAST, 0.0, 1.0, 40, TravelDirection.MED))
        builder.addSpeed(SpeedInterval(SEQ_SOUTH, 0.0, 1.0, 40, TravelDirection.MED))
        return builder.build()
    }

    /** Through road north with a side street leaving at [sideBearingDegrees] from north. */
    fun mainRoadWithAngledSideStreet(sideBearingDegrees: Double = ANGLED_SIDE_DEGREES): RoadGraph {
        return junctionGraph(sideBearingDegrees = sideBearingDegrees, includeJunctionControl = true)
    }

    /**
     * Priority stretch on MAIN and SIDE (and optionally CONTINUE) for turn vs straight re-alert tests.
     */
    fun priorityRoadWithSideAndContinueStretches(continueHasPriority: Boolean): RoadGraph {
        return junctionGraph(
            sideBearingDegrees = 90.0,
            includeJunctionControl = false,
            mainPriorityStretch = true,
            continuePriorityPlate = false,
            continuePriorityStretch = continueHasPriority,
        )
    }

    /**
     * Side street with no NVDB fartsdata turning onto a signed 40 main road — common on small
     * residential streets that still have a physical 40 plate.
     */
    fun unsignedSideStreetOntoSignedMain(): RoadGraph {
        return junctionGraph(
            sideBearingDegrees = 90.0,
            includeJunctionControl = false,
            sideHasSpeed = false,
        )
    }

    private fun junctionGraph(
        sideBearingDegrees: Double,
        includeJunctionControl: Boolean,
        mainPriorityStretch: Boolean = false,
        continuePriorityPlate: Boolean = true,
        continuePriorityStretch: Boolean = false,
        sideHasSpeed: Boolean = true,
        continueSpeedPlate: Boolean = false,
        includeRoundaboutArms: Boolean = false,
    ): RoadGraph {
        val junction = Geo.offsetMeters(origin, northMeters = MAIN_LENGTH_METERS, eastMeters = 0.0)
        val northEnd = Geo.offsetMeters(junction, northMeters = MAIN_LENGTH_METERS, eastMeters = 0.0)
        val sideEnd = Geo.destination(junction, sideBearingDegrees, SIDE_LENGTH_METERS)
        val builder = RoadGraphBuilder().apply {
            tileId = if (sideBearingDegrees == 90.0) "fixture-main-side" else "fixture-main-angled-side"
            version = "test"
        }
        builder.addNode(RoadNode(1, origin))
        builder.addNode(RoadNode(2, junction))
        builder.addNode(RoadNode(3, northEnd))
        builder.addNode(RoadNode(4, sideEnd))
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
                points = dense(junction, sideEnd),
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
        if (continuePriorityPlate) {
            builder.addObject(
                RoadObject(
                    nvdbId = MAIN_PRIORITY_SIGN_ID,
                    type = RoadObjectType.PRIORITY_ROAD,
                    sequenceId = SEQ_CONTINUE,
                    fromPos = 50.0 / MAIN_LENGTH_METERS,
                    toPos = 50.0 / MAIN_LENGTH_METERS,
                    direction = TravelDirection.MED,
                    payload = "206",
                ),
            )
        }
        if (mainPriorityStretch) {
            builder.addObject(
                RoadObject(
                    nvdbId = MAIN_PRIORITY_STRETCH_ID,
                    type = RoadObjectType.PRIORITY_ROAD,
                    sequenceId = SEQ_MAIN,
                    fromPos = 0.0,
                    toPos = 1.0,
                    direction = TravelDirection.MED,
                    payload = "206",
                ),
            )
        }
        if (continuePriorityStretch) {
            builder.addObject(
                RoadObject(
                    nvdbId = CONTINUE_PRIORITY_STRETCH_ID,
                    type = RoadObjectType.PRIORITY_ROAD,
                    sequenceId = SEQ_CONTINUE,
                    fromPos = 0.0,
                    toPos = 1.0,
                    direction = TravelDirection.MED,
                    payload = "206",
                ),
            )
        }
        builder.addObject(
            RoadObject(
                nvdbId = SIDE_PRIORITY_ID,
                type = RoadObjectType.PRIORITY_ROAD,
                sequenceId = SEQ_SIDE,
                fromPos = 0.0,
                toPos = 1.0,
                direction = TravelDirection.MED,
                payload = "206",
            ),
        )
        builder.addObject(
            RoadObject(
                nvdbId = SIDE_PRIORITY_REMINDER_ID,
                type = RoadObjectType.PRIORITY_ROAD,
                sequenceId = SEQ_SIDE,
                fromPos = 120.0 / SIDE_LENGTH_METERS,
                toPos = 120.0 / SIDE_LENGTH_METERS,
                direction = TravelDirection.MED,
                payload = "206",
            ),
        )
        // Mis-tagged the way NVDB often is: plate facing out MED at the junction mouth.
        if (includeJunctionControl) {
            builder.addObject(
                RoadObject(
                    nvdbId = SIDE_STOP_ID,
                    type = RoadObjectType.STOP,
                    sequenceId = SEQ_SIDE,
                    fromPos = 5.0 / SIDE_LENGTH_METERS,
                    toPos = 5.0 / SIDE_LENGTH_METERS,
                    direction = TravelDirection.MED,
                    payload = "204",
                ),
            )
            builder.addObject(
                RoadObject(
                    nvdbId = SIDE_YIELD_ID,
                    type = RoadObjectType.YIELD,
                    sequenceId = SEQ_SIDE,
                    fromPos = 8.0 / SIDE_LENGTH_METERS,
                    toPos = 8.0 / SIDE_LENGTH_METERS,
                    direction = TravelDirection.MED,
                    payload = "202",
                ),
            )
        }
        if (includeRoundaboutArms) {
            builder.addObject(
                RoadObject(
                    nvdbId = APPROACH_ROUNDABOUT_ID,
                    type = RoadObjectType.HAZARD,
                    sequenceId = SEQ_MAIN,
                    fromPos = 350.0 / MAIN_LENGTH_METERS,
                    toPos = 350.0 / MAIN_LENGTH_METERS,
                    direction = TravelDirection.MED,
                    payload = "406",
                ),
            )
            builder.addObject(
                RoadObject(
                    nvdbId = CONTINUE_ROUNDABOUT_ID,
                    type = RoadObjectType.HAZARD,
                    sequenceId = SEQ_CONTINUE,
                    fromPos = 40.0 / MAIN_LENGTH_METERS,
                    toPos = 40.0 / MAIN_LENGTH_METERS,
                    direction = TravelDirection.MED,
                    payload = "406",
                ),
            )
            builder.addObject(
                RoadObject(
                    nvdbId = SIDE_ROUNDABOUT_ID,
                    type = RoadObjectType.HAZARD,
                    sequenceId = SEQ_SIDE,
                    fromPos = 40.0 / SIDE_LENGTH_METERS,
                    toPos = 40.0 / SIDE_LENGTH_METERS,
                    direction = TravelDirection.MED,
                    payload = "406",
                ),
            )
        }
        builder.addSpeed(SpeedInterval(SEQ_MAIN, 0.0, 1.0, 40, TravelDirection.MED))
        builder.addSpeed(SpeedInterval(SEQ_CONTINUE, 0.0, 1.0, 40, TravelDirection.MED))
        if (sideHasSpeed) {
            builder.addSpeed(SpeedInterval(SEQ_SIDE, 0.0, 1.0, 40, TravelDirection.MED))
        }
        if (continueSpeedPlate) {
            builder.addObject(
                RoadObject(
                    nvdbId = CONTINUE_SPEED_PLATE_40_ID,
                    type = RoadObjectType.SPEED_LIMIT,
                    sequenceId = SEQ_CONTINUE,
                    fromPos = CONTINUE_SPEED_PLATE_POS,
                    toPos = CONTINUE_SPEED_PLATE_POS,
                    direction = TravelDirection.MED,
                    payload = "40",
                ),
            )
        }
        return builder.build()
    }

    private fun dense(from: LatLon, to: LatLon, steps: Int = 20): List<LatLon> {
        return (0..steps).map { step ->
            Geo.interpolate(from, to, step.toDouble() / steps)
        }
    }
}
