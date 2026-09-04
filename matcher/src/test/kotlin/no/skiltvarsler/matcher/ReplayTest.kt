package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import no.skiltvarsler.tiles.JdbcTileStore
import no.skiltvarsler.tiles.TravelDirection
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ReplayTest {
    @Test
    fun cameraEightyMetersAheadOnE6North() {
        val graph = SyntheticGraph.e6VestbyLike()
        val sequence = graph.sequences.getValue(SyntheticGraph.SEQ_E6_NORTH)
        val position = SyntheticGraph.ATK_POS - (80.0 / sequence.lengthMeters)
        val match = Match(
            linkId = sequence.links.first().id,
            sequenceId = sequence.id,
            position = position,
            direction = TravelDirection.MED,
            snapped = sequence.links.first().points.first(),
            distanceToLinkMeters = 0.0,
        )
        val candidates = HorizonScanner(graph).scan(match, speedMetersPerSecond = 25.0)
        val camera = candidates.first { it.obj.nvdbId == SyntheticGraph.ATK_ID }
        assertThat(camera.metersAhead).isWithin(8.0).of(80.0)
        assertThat(camera.obj.sequenceId).isEqualTo(SyntheticGraph.SEQ_E6_NORTH)
    }

    @Test
    fun kommuneChangeFiresWhenCrossingLinkBoundary() {
        val graph = SyntheticGraph.e6VestbyLike()
        val northLinks = graph.sequences.getValue(SyntheticGraph.SEQ_E6_NORTH).links.sortedBy { it.startPos }
        assertThat(northLinks).hasSize(2)
        val firstHalf = Replay.alongLink(northLinks[0], TravelDirection.MED, speedMetersPerSecond = 25.0)
        val secondHalf = Replay.alongLink(
            northLinks[1],
            TravelDirection.MED,
            speedMetersPerSecond = 25.0,
            startTimeMs = firstHalf.size * 1000L,
        )
        val result = Replay.play(AlertEngine(graph), firstHalf + secondHalf)
        val municipalities = result.alertsOf(AlertKind.MUNICIPALITY)
        assertThat(municipalities).hasSize(1)
        assertThat(municipalities.single().title).isEqualTo("Ås")
        assertThat(municipalities.single().body).isEqualTo("Kommunegrense")
    }

    @Test
    fun kommuneAlertStillFiresWhenTheWindowReloadsAtTheBorder() {
        val graph = SyntheticGraph.e6VestbyLike()
        val northLinks = graph.sequences.getValue(SyntheticGraph.SEQ_E6_NORTH).links.sortedBy { it.startPos }
        val beforeBorder = Replay.alongLink(northLinks[0], TravelDirection.MED, speedMetersPerSecond = 25.0)
        val afterBorder = Replay.alongLink(
            northLinks[1],
            TravelDirection.MED,
            speedMetersPerSecond = 25.0,
            startTimeMs = beforeBorder.size * 1000L,
        )
        val engine = AlertEngine(graph)
        beforeBorder.forEach { fix -> engine.update(fix) }
        engine.updateGraph(SyntheticGraph.e6VestbyLike())
        val municipalities = afterBorder
            .flatMap { fix -> engine.update(fix) }
            .filter { it.kind == AlertKind.MUNICIPALITY }
        assertThat(municipalities).hasSize(1)
        assertThat(municipalities.single().title).isEqualTo("Ås")
    }

    @Test
    fun reloadingTheWindowOnEveryFixFiresTheCameraOnlyOnce() {
        val graph = SyntheticGraph.e6VestbyLike()
        val engine = AlertEngine(graph)
        val fixes = Replay.alongLink(graph.e6NorthLink(), TravelDirection.MED, speedMetersPerSecond = 25.0)
        val cameras = fixes
            .flatMap { fix ->
                engine.updateGraph(SyntheticGraph.e6VestbyLike())
                engine.update(fix)
            }
            .filter { it.kind == AlertKind.SPEED_CAMERA }
        assertThat(cameras.map { it.nvdbId }).containsExactly(SyntheticGraph.ATK_ID)
    }

    @Test
    fun replayFiresCameraOnceAndNeverOnOppositeCarriageway() {
        val graph = SyntheticGraph.e6VestbyLike()
        val north = Replay.play(
            AlertEngine(graph),
            Replay.alongLink(graph.e6NorthLink(), TravelDirection.MED, speedMetersPerSecond = 25.0),
        )
        val cameras = north.alertsOf(AlertKind.SPEED_CAMERA)
        assertThat(cameras).hasSize(1)
        assertThat(cameras.single().nvdbId).isEqualTo(SyntheticGraph.ATK_ID)
        assertThat(north.matches.any { it.sequenceId == SyntheticGraph.SEQ_E6_NORTH }).isTrue()
        assertThat(north.matches.none { it.sequenceId == SyntheticGraph.SEQ_E6_SOUTH }).isTrue()

        val south = Replay.play(
            AlertEngine(graph),
            Replay.alongLink(graph.e6SouthLink(), TravelDirection.MED, speedMetersPerSecond = 25.0),
        )
        assertThat(south.alertsOf(AlertKind.SPEED_CAMERA)).isEmpty()
        assertThat(south.matches.all { it.sequenceId == SyntheticGraph.SEQ_E6_SOUTH }).isTrue()
    }

    @Test
    fun gpsHopsOntoParallelLocalRoadStillStayOnE6AndFireCamera() {
        val graph = SyntheticGraph.e6VestbyLike()
        val hops = mapOf(
            8 to (0.0 to 30.0),
            9 to (0.0 to 32.0),
            10 to (0.0 to 28.0),
        )
        val result = Replay.play(
            AlertEngine(graph),
            Replay.alongLink(
                graph.e6NorthLink(),
                TravelDirection.MED,
                speedMetersPerSecond = 25.0,
                hopOffsets = hops,
            ),
        )
        val hopMatches = result.matches.drop(7).take(4)
        assertThat(hopMatches.all { it.sequenceId == SyntheticGraph.SEQ_E6_NORTH }).isTrue()
        assertThat(result.alertsOf(AlertKind.SPEED_CAMERA).map { it.nvdbId })
            .containsExactly(SyntheticGraph.ATK_ID)
    }

    @Test
    fun speedLimitFiresAheadOfTheZoneChange() {
        val graph = SyntheticGraph.e6VestbyLike()
        val result = Replay.play(
            AlertEngine(graph),
            Replay.alongLink(
                graph.sequences.getValue(SyntheticGraph.SEQ_E6_NORTH).links.first(),
                TravelDirection.MED,
                speedMetersPerSecond = 25.0,
            ),
        )
        val limits = result.alertsOf(AlertKind.SPEED_LIMIT)
        assertThat(limits).hasSize(1)
        assertThat(limits.single().payload).isEqualTo("60")
        assertThat(limits.single().metersAhead).isAtLeast(50.0)
        assertThat(limits.single().metersAhead).isAtMost(170.0)
        val (fix, _) = result.alerts.first { it.second.kind == AlertKind.SPEED_LIMIT }
        val matchAtAlert = result.matches[(fix.timeMs / 1000L).toInt()]
        assertThat(matchAtAlert.position).isLessThan(0.5)
        assertThat(graph.speedAt(matchAtAlert.sequenceId, matchAtAlert.position, matchAtAlert.direction))
            .isEqualTo(80)
        assertThat(graph.speedAt(SyntheticGraph.SEQ_E6_NORTH, 0.5, TravelDirection.MED)).isEqualTo(60)
    }

    @Test
    fun prioritySignFiresAheadOnTheRoadYouStayOn() {
        val graph = SyntheticGraph.mainRoadWithSideStreet()
        val continueLink = graph.sequences.getValue(SyntheticGraph.SEQ_CONTINUE).links.first()
        val result = Replay.play(
            AlertEngine(graph),
            Replay.alongLink(continueLink, TravelDirection.MED, speedMetersPerSecond = 15.0),
        )
        val priority = result.alertsOf(AlertKind.PRIORITY_ROAD)
        assertThat(priority).hasSize(1)
        assertThat(priority.single().nvdbId).isEqualTo(SyntheticGraph.MAIN_PRIORITY_SIGN_ID)
        assertThat(priority.single().metersAhead).isGreaterThan(0.0)
    }

    @Test
    fun sqliteRoundTripKeepsCamera() {
        val graph = SyntheticGraph.e6VestbyLike()
        val dir = createTempDirectory("tile").toFile()
        val file = File(dir, "fixture.sqlite")
        JdbcTileStore.open(file.absolutePath).use { connection ->
            JdbcTileStore.write(connection, graph, mapOf("pipeline_warnings" to "0"))
        }
        val loaded = JdbcTileStore.open(file.absolutePath).use { JdbcTileStore.read(it) }
        val cameras = loaded.objectsOn(SyntheticGraph.SEQ_E6_NORTH)
            .filter { it.nvdbId == SyntheticGraph.ATK_ID }
        assertThat(cameras).hasSize(1)
        file.delete()
        dir.delete()
    }
}
