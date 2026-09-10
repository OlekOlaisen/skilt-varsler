package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import no.skiltvarsler.tiles.TravelDirection
import org.junit.Test

class DrivingBehaviorTest {
    @Test
    fun horizonFollowsStraightContinuationNotSideStreet() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN)
        val match = Match(
            linkId = main.links.first().id,
            sequenceId = main.id,
            position = 0.75,
            direction = TravelDirection.MED,
            snapped = main.links.first().points.last(),
            distanceToLinkMeters = 0.0,
        )
        val found = HorizonScanner(graph).scan(match, speedMetersPerSecond = 25.0)
        val ids = found.map { it.obj.nvdbId }
        assertThat(ids).contains(SyntheticGraph.CONTINUE_HAZARD_ID)
        assertThat(ids).doesNotContain(SyntheticGraph.SIDE_HAZARD_ID)
        assertThat(ids).doesNotContain(SyntheticGraph.SIDE_STOP_ID)
        assertThat(ids).doesNotContain(SyntheticGraph.SIDE_YIELD_ID)
        val ahead = found.first { it.obj.nvdbId == SyntheticGraph.CONTINUE_HAZARD_ID }
        assertThat(ahead.metersAhead).isWithin(15.0).of(180.0)
    }

    @Test
    fun horizonFromSideStreetDoesNotSeeThroughRoadHazard() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE)
        val match = Match(
            linkId = side.links.first().id,
            sequenceId = side.id,
            position = 0.15,
            direction = TravelDirection.MOT,
            snapped = side.links.first().points.first(),
            distanceToLinkMeters = 0.0,
        )
        val found = HorizonScanner(graph).scan(match, speedMetersPerSecond = 12.0)
        val ids = found.map { it.obj.nvdbId }
        assertThat(ids).doesNotContain(SyntheticGraph.CONTINUE_HAZARD_ID)
        assertThat(ids).doesNotContain(SyntheticGraph.MAIN_PRIORITY_SIGN_ID)
    }

    @Test
    fun approachingJunctionFromSideStreetDoesNotAlertContinueHazard() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE).links.first()
        val result = Replay.play(
            AlertEngine(graph),
            Replay.alongLink(side, TravelDirection.MOT, speedMetersPerSecond = 12.0),
        )
        assertThat(result.alertsOf(AlertKind.HAZARD).map { it.nvdbId })
            .doesNotContain(SyntheticGraph.CONTINUE_HAZARD_ID)
    }

    @Test
    fun angledSideStreetStopAndYieldAreIgnoredWhileDrivingPast() {
        val graph = SyntheticGraph.mainRoadWithAngledSideStreet(sideBearingDegrees = 30.0)
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN)
        val match = Match(
            linkId = main.links.first().id,
            sequenceId = main.id,
            position = 0.90,
            direction = TravelDirection.MED,
            snapped = main.links.first().points.last(),
            distanceToLinkMeters = 0.0,
        )
        val found = HorizonScanner(graph).scan(match, speedMetersPerSecond = 12.0)
        val ids = found.map { it.obj.nvdbId }
        assertThat(ids).contains(SyntheticGraph.CONTINUE_HAZARD_ID)
        assertThat(ids).doesNotContain(SyntheticGraph.SIDE_HAZARD_ID)
        assertThat(ids).doesNotContain(SyntheticGraph.SIDE_STOP_ID)
        assertThat(ids).doesNotContain(SyntheticGraph.SIDE_YIELD_ID)
    }

    @Test
    fun drivingPastAngledSideStreetDoesNotFireStopAlert() {
        val graph = SyntheticGraph.mainRoadWithAngledSideStreet(sideBearingDegrees = 30.0)
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val continueLink = graph.sequences.getValue(SyntheticGraph.SEQ_CONTINUE).links.first()
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 12.0)
        val alongContinue = Replay.alongLink(
            continueLink,
            TravelDirection.MED,
            speedMetersPerSecond = 12.0,
            startTimeMs = alongMain.last().timeMs + 1_000L,
        )
        val result = Replay.play(AlertEngine(graph), alongMain + alongContinue)
        assertThat(result.alertsOf(AlertKind.STOP)).isEmpty()
        assertThat(result.alertsOf(AlertKind.YIELD)).isEmpty()
        assertThat(result.alertsOf(AlertKind.HAZARD).map { it.nvdbId })
            .contains(SyntheticGraph.CONTINUE_HAZARD_ID)
        assertThat(result.alertsOf(AlertKind.HAZARD).map { it.nvdbId })
            .doesNotContain(SyntheticGraph.SIDE_HAZARD_ID)
        val continueHazard = result.alertsOf(AlertKind.HAZARD)
            .first { it.nvdbId == SyntheticGraph.CONTINUE_HAZARD_ID }
        assertThat(continueHazard.metersAhead).isAtLeast(50.0)
    }

    @Test
    fun turningOntoSideStreetStillSeesYieldOnThatRoad() {
        val graph = SyntheticGraph.mainRoadWithAngledSideStreet(sideBearingDegrees = 30.0)
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE).links.first()
        val result = Replay.play(
            AlertEngine(graph),
            Replay.alongLink(side, TravelDirection.MED, speedMetersPerSecond = 8.0),
        )
        assertThat(result.alertsOf(AlertKind.YIELD).map { it.nvdbId })
            .contains(SyntheticGraph.SIDE_YIELD_ID)
    }

    @Test
    fun standingStillDoesNotFireCameraOnTheRoadAhead() {
        val graph = SyntheticGraph.e6VestbyLike()
        val moving = Replay.alongLink(
            graph.e6NorthLink(),
            TravelDirection.MED,
            speedMetersPerSecond = 25.0,
        )
        val standing = moving[moving.size / 4].copy(speedMetersPerSecond = 0.0)
        val engine = AlertEngine(graph)
        val alerts = (0..8).flatMap { second ->
            engine.update(standing.copy(timeMs = standing.timeMs + second * 1000L))
        }
        assertThat(alerts.filter { it.kind == AlertKind.SPEED_CAMERA }).isEmpty()
    }

    @Test
    fun poorGpsWhileStationaryDoesNotDeadReckonAlongTheRoad() {
        val graph = SyntheticGraph.e6VestbyLike()
        val moving = Replay.alongLink(
            graph.e6NorthLink(),
            TravelDirection.MED,
            speedMetersPerSecond = 25.0,
        )
        val matcher = MapMatcher(graph)
        val first = moving[6]
        matcher.update(first)
        val before = matcher.current()!!
        matcher.update(
            first.copy(
                timeMs = first.timeMs + 2_000L,
                accuracyMeters = 40.0,
                speedMetersPerSecond = 0.0,
            ),
        )
        val after = matcher.current()!!
        assertThat(after.sequenceId).isEqualTo(before.sequenceId)
        assertThat(after.position).isWithin(1e-6).of(before.position)
    }

    @Test
    fun tunnelMultipathKeepsMainRoadUntilGpsRecovers() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE).links.first()
        val matcher = MapMatcher(graph)
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 15.0)
        alongMain.take(10).forEach { matcher.update(it) }
        assertThat(matcher.current()!!.sequenceId).isEqualTo(SyntheticGraph.SEQ_MAIN)

        val mid = alongMain[10]
        val sidePoint = side.points[side.points.size / 2]
        repeat(5) { sample ->
            matcher.update(
                mid.copy(
                    timeMs = mid.timeMs + (sample + 1) * 1_000L,
                    position = sidePoint,
                    accuracyMeters = 60.0,
                    speedMetersPerSecond = 15.0,
                    bearingDegrees = mid.bearingDegrees,
                ),
            )
            assertThat(matcher.isHolding()).isTrue()
            assertThat(matcher.current()!!.sequenceId).isEqualTo(SyntheticGraph.SEQ_MAIN)
        }

        val recovered = Replay.alongLink(
            main,
            TravelDirection.MED,
            speedMetersPerSecond = 15.0,
            startTimeMs = mid.timeMs + 8_000L,
        ).drop(12).take(6)
        recovered.forEach { matcher.update(it) }
        assertThat(matcher.current()!!.sequenceId).isEqualTo(SyntheticGraph.SEQ_MAIN)
        assertThat(matcher.isHolding()).isFalse()
    }

    @Test
    fun gpsJumpWithClaimedGoodAccuracyStillHoldsThroughRoad() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE).links.first()
        val matcher = MapMatcher(graph)
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 14.0)
        alongMain.take(8).forEach { matcher.update(it) }
        val mid = alongMain[8]
        matcher.update(
            mid.copy(
                timeMs = mid.timeMs + 1_000L,
                position = side.points.last(),
                accuracyMeters = 8.0,
                speedMetersPerSecond = 14.0,
                bearingDegrees = mid.bearingDegrees,
            ),
        )
        assertThat(matcher.isHolding()).isTrue()
        assertThat(matcher.current()!!.sequenceId).isEqualTo(SyntheticGraph.SEQ_MAIN)
    }

    @Test
    fun standingStillStillListsCameraOnHorizon() {
        val graph = SyntheticGraph.e6VestbyLike()
        val moving = Replay.alongLink(
            graph.e6NorthLink(),
            TravelDirection.MED,
            speedMetersPerSecond = 25.0,
        )
        val engine = AlertEngine(graph)
        moving.take(14).forEach { engine.update(it) }
        val standing = moving[14].copy(speedMetersPerSecond = 0.0)
        val alerts = (0..3).flatMap { second ->
            engine.update(standing.copy(timeMs = standing.timeMs + second * 1000L))
        }
        assertThat(alerts.filter { it.kind == AlertKind.SPEED_CAMERA }).isEmpty()
        assertThat(engine.currentHorizon().map { it.obj.nvdbId }).contains(SyntheticGraph.ATK_ID)
    }

    @Test
    fun turningOntoSideStreetSwitchesAfterClearSamples() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE).links.first()
        val matcher = MapMatcher(graph)
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 15.0)
        alongMain.forEach { matcher.update(it) }
        assertThat(matcher.current()!!.sequenceId).isEqualTo(SyntheticGraph.SEQ_MAIN)

        val alongSide = Replay.alongLink(
            side,
            TravelDirection.MED,
            speedMetersPerSecond = 12.0,
            startTimeMs = alongMain.last().timeMs + 1_000L,
        )
        alongSide.take(4).forEach { matcher.update(it) }
        assertThat(matcher.current()!!.sequenceId).isEqualTo(SyntheticGraph.SEQ_SIDE)
    }

    @Test
    fun drivingPastAngledSideStreetKeepsMainSequenceMatch() {
        val graph = SyntheticGraph.mainRoadWithAngledSideStreet(sideBearingDegrees = 30.0)
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val continueLink = graph.sequences.getValue(SyntheticGraph.SEQ_CONTINUE).links.first()
        val matcher = MapMatcher(graph)
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 12.0)
        val alongContinue = Replay.alongLink(
            continueLink,
            TravelDirection.MED,
            speedMetersPerSecond = 12.0,
            startTimeMs = alongMain.last().timeMs + 1_000L,
        )
        (alongMain + alongContinue).forEach { matcher.update(it) }
        val matched = matcher.current()!!
        assertThat(matched.sequenceId).isAnyOf(SyntheticGraph.SEQ_MAIN, SyntheticGraph.SEQ_CONTINUE)
        assertThat(matched.sequenceId).isNotEqualTo(SyntheticGraph.SEQ_SIDE)
    }

    @Test
    fun continuingStraightThroughJunctionMatchesContinueSequence() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val continueLink = graph.sequences.getValue(SyntheticGraph.SEQ_CONTINUE).links.first()
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 15.0)
        val alongContinue = Replay.alongLink(
            continueLink,
            TravelDirection.MED,
            speedMetersPerSecond = 15.0,
            startTimeMs = alongMain.last().timeMs + 1_000L,
        )
        val result = Replay.play(AlertEngine(graph), alongMain + alongContinue)
        assertThat(result.matches.any { it.sequenceId == SyntheticGraph.SEQ_CONTINUE }).isTrue()
        assertThat(result.matches.last().sequenceId).isEqualTo(SyntheticGraph.SEQ_CONTINUE)
    }

    @Test
    fun turningOntoSideStreetAlertsPriorityRoadOnce() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE).links.first()
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 15.0)
        val beforeTurn = alongMain.takeWhile { fix -> fix.timeMs <= 18_000L }
        val alongSide = Replay.alongLink(
            side,
            TravelDirection.MED,
            speedMetersPerSecond = 12.0,
            startTimeMs = beforeTurn.last().timeMs + 1_000L,
        )
        val result = Replay.play(AlertEngine(graph), beforeTurn + alongSide)
        val priority = result.alertsOf(AlertKind.PRIORITY_ROAD)
        assertThat(priority).hasSize(1)
        assertThat(priority.single().nvdbId).isEqualTo(SyntheticGraph.SIDE_PRIORITY_ID)
        assertThat(priority.single().metersAhead).isEqualTo(0.0)
    }

    @Test
    fun turningFromPriorityOntoSidePriorityRealerts() {
        val graph = SyntheticGraph.priorityRoadWithSideAndContinueStretches(continueHasPriority = false)
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE).links.first()
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 15.0)
        val beforeTurn = alongMain.takeWhile { fix -> fix.timeMs <= 18_000L }
        val alongSide = Replay.alongLink(
            side,
            TravelDirection.MED,
            speedMetersPerSecond = 12.0,
            startTimeMs = beforeTurn.last().timeMs + 1_000L,
        )
        val result = Replay.play(AlertEngine(graph), beforeTurn + alongSide)
        val priorityIds = result.alertsOf(AlertKind.PRIORITY_ROAD).map { it.nvdbId }
        assertThat(priorityIds).containsExactly(
            SyntheticGraph.MAIN_PRIORITY_STRETCH_ID,
            SyntheticGraph.SIDE_PRIORITY_ID,
        ).inOrder()
    }

    @Test
    fun continuingStraightOnPriorityDoesNotRealertAtSequenceSplit() {
        val graph = SyntheticGraph.priorityRoadWithSideAndContinueStretches(continueHasPriority = true)
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val continueLink = graph.sequences.getValue(SyntheticGraph.SEQ_CONTINUE).links.first()
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 15.0)
        val alongContinue = Replay.alongLink(
            continueLink,
            TravelDirection.MED,
            speedMetersPerSecond = 15.0,
            startTimeMs = alongMain.last().timeMs + 1_000L,
        )
        val result = Replay.play(AlertEngine(graph), alongMain + alongContinue)
        val priority = result.alertsOf(AlertKind.PRIORITY_ROAD)
        assertThat(priority).hasSize(1)
        assertThat(priority.single().nvdbId).isEqualTo(SyntheticGraph.MAIN_PRIORITY_STRETCH_ID)
        assertThat(priority.map { it.nvdbId }).doesNotContain(SyntheticGraph.CONTINUE_PRIORITY_STRETCH_ID)
    }

    @Test
    fun turningOntoSideStreetReconfirmsSameSpeedLimit() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val side = graph.sequences.getValue(SyntheticGraph.SEQ_SIDE).links.first()
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 12.0)
        val beforeTurn = alongMain.takeWhile { fix -> fix.timeMs <= 18_000L }
        val alongSide = Replay.alongLink(
            side,
            TravelDirection.MED,
            speedMetersPerSecond = 12.0,
            startTimeMs = beforeTurn.last().timeMs + 1_000L,
        )
        val result = Replay.play(AlertEngine(graph), beforeTurn + alongSide)
        val limits = result.alertsOf(AlertKind.SPEED_LIMIT)
        assertThat(limits.map { it.payload }).contains("40")
        assertThat(limits.any { it.sequenceId == SyntheticGraph.SEQ_SIDE && it.metersAhead == 0.0 }).isTrue()
    }

    @Test
    fun unsignedConnectorDoesNotSwallowSpeedReconfirmOnExit() {
        val graph = SyntheticGraph.eastRoadViaUnsignedConnectorToSouth()
        val east = graph.sequences.getValue(SyntheticGraph.SEQ_EAST).links.first()
        val connector = graph.sequences.getValue(SyntheticGraph.SEQ_CONNECTOR).links.first()
        val south = graph.sequences.getValue(SyntheticGraph.SEQ_SOUTH).links.first()
        val alongEast = Replay.alongLink(east, TravelDirection.MED, speedMetersPerSecond = 12.0)
        val alongConnector = Replay.alongLink(
            connector,
            TravelDirection.MED,
            speedMetersPerSecond = 10.0,
            startTimeMs = alongEast.last().timeMs + 1_000L,
        )
        val alongSouth = Replay.alongLink(
            south,
            TravelDirection.MED,
            speedMetersPerSecond = 12.0,
            startTimeMs = alongConnector.last().timeMs + 1_000L,
        )
        val result = Replay.play(AlertEngine(graph), alongEast + alongConnector + alongSouth)
        val limits = result.alertsOf(AlertKind.SPEED_LIMIT)
        assertThat(limits.none { it.sequenceId == SyntheticGraph.SEQ_CONNECTOR }).isTrue()
        assertThat(limits.any { it.sequenceId == SyntheticGraph.SEQ_SOUTH && it.payload == "40" }).isTrue()
    }

    @Test
    fun continuingStraightThroughJunctionDoesNotReconfirmSameSpeed() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val main = graph.sequences.getValue(SyntheticGraph.SEQ_MAIN).links.first()
        val continueLink = graph.sequences.getValue(SyntheticGraph.SEQ_CONTINUE).links.first()
        val alongMain = Replay.alongLink(main, TravelDirection.MED, speedMetersPerSecond = 12.0)
        val alongContinue = Replay.alongLink(
            continueLink,
            TravelDirection.MED,
            speedMetersPerSecond = 12.0,
            startTimeMs = alongMain.last().timeMs + 1_000L,
        )
        val result = Replay.play(AlertEngine(graph), alongMain + alongContinue)
        val limitsOnContinue = result.alertsOf(AlertKind.SPEED_LIMIT)
            .filter { it.sequenceId == SyntheticGraph.SEQ_CONTINUE }
        assertThat(limitsOnContinue).isEmpty()
    }

    @Test
    fun mutedDrivingStillProducesCameraAlertForTripStats() {
        val graph = SyntheticGraph.e6VestbyLike()
        val moving = Replay.alongLink(
            graph.e6NorthLink(),
            TravelDirection.MED,
            speedMetersPerSecond = 25.0,
        )
        val engine = AlertEngine(graph, AlertSettings(alertsMuted = true))
        val alerts = moving.take(16).flatMap { engine.update(it) }
        assertThat(alerts.filter { it.kind == AlertKind.SPEED_CAMERA }).isNotEmpty()
        assertThat(engine.currentHorizon().map { it.obj.nvdbId }).contains(SyntheticGraph.ATK_ID)
    }
}
