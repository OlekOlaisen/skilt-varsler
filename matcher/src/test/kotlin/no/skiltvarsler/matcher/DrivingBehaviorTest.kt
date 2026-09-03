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
        val ahead = found.first { it.obj.nvdbId == SyntheticGraph.CONTINUE_HAZARD_ID }
        assertThat(ahead.metersAhead).isWithin(15.0).of(180.0)
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
    fun turningOntoSideStreetSwitchesOnFirstClearSample() {
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
        matcher.update(alongSide[1])
        assertThat(matcher.current()!!.sequenceId).isEqualTo(SyntheticGraph.SEQ_SIDE)
    }

    @Test
    fun mutedDrivingDoesNotFireCameraButKeepsHorizon() {
        val graph = SyntheticGraph.e6VestbyLike()
        val moving = Replay.alongLink(
            graph.e6NorthLink(),
            TravelDirection.MED,
            speedMetersPerSecond = 25.0,
        )
        val engine = AlertEngine(graph, AlertSettings(alertsMuted = true))
        val alerts = moving.take(16).flatMap { engine.update(it) }
        assertThat(alerts.filter { it.kind == AlertKind.SPEED_CAMERA }).isEmpty()
        assertThat(engine.currentHorizon().map { it.obj.nvdbId }).contains(SyntheticGraph.ATK_ID)
    }
}
