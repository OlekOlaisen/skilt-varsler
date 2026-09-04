package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadLink
import no.skiltvarsler.tiles.TravelDirection
import no.skiltvarsler.tiles.closestPointOnPolyline
import kotlin.math.abs
import kotlin.math.max

class MapMatcher(
    private var graph: RoadGraph,
    private val hysteresisMeters: Double = 18.0,
    private val switchSamplesRequired: Int = 4,
    private val deadReckonAccuracyMeters: Double = 28.0,
    private val headingAlignDegrees: Double = 55.0,
    private val stayOnSequenceMeters: Double = 22.0,
    private val headingTurnDegrees: Double = 40.0,
) {
    private var last: Match? = null
    private var lastTimeMs: Long = 0L
    private var switchVotes: Int = 0

    fun reset() {
        last = null
        lastTimeMs = 0L
        switchVotes = 0
    }

    /** Swaps in a reloaded window. The current match is kept; link ids are stable across tiles. */
    fun updateGraph(next: RoadGraph) {
        graph = next
    }

    fun current(): Match? = last

    fun update(fix: GpsFix): Match? {
        val dtSeconds = if (lastTimeMs == 0L) 1.0 else ((fix.timeMs - lastTimeMs) / 1000.0).coerceIn(0.2, 3.0)
        lastTimeMs = fix.timeMs
        val previous = last

        if (previous != null && fix.accuracyMeters > deadReckonAccuracyMeters) {
            last = deadReckon(previous, fix, dtSeconds)
            switchVotes = 0
            return last
        }

        val searchRadius = max(55.0, fix.accuracyMeters * 2.5)
        val nearby = graph.spatialGrid.query(fix.position.latitude, fix.position.longitude, searchRadius)
        val scored = nearby.mapNotNull { score(it, fix) }.sortedBy { it.cost }

        val best = scored.firstOrNull()
        if (best == null) {
            last = previous?.let { deadReckon(it, fix, dtSeconds) }
            return last
        }

        if (previous == null) {
            last = best.toMatch()
            switchVotes = 0
            return last
        }

        val sameLink = best.link.id == previous.linkId
        val sameSequence = best.link.sequenceId == previous.sequenceId &&
            best.direction == previous.direction

        if (sameLink || (sameSequence && best.distanceMeters <= stayOnSequenceMeters)) {
            last = best.toMatch()
            switchVotes = 0
            return last
        }

        val projected = deadReckon(previous, fix, dtSeconds)
        val projectedCost = projectedScore(projected, fix)
        val betterBy = projectedCost - best.cost
        val headingTurned = headingTurnedAway(previous, fix)
        val votesNeeded = if (headingTurned && betterBy > 8.0) 1 else switchSamplesRequired

        if (betterBy > hysteresisMeters || (headingTurned && best.distanceMeters + 8.0 < projectedCost)) {
            switchVotes += 1
            if (switchVotes >= votesNeeded) {
                last = best.toMatch()
                switchVotes = 0
                return last
            }
        } else {
            switchVotes = 0
        }

        last = projected
        return last
    }

    private fun headingTurnedAway(previous: Match, fix: GpsFix): Boolean {
        val heading = fix.bearingDegrees ?: return false
        val travel = travelBearing(previous) ?: return false
        return Geo.headingDeltaDegrees(heading, travel) > headingTurnDegrees
    }

    private fun travelBearing(match: Match): Double? {
        val link = graph.links[match.linkId] ?: return null
        if (link.points.size < 2) {
            return null
        }
        val along = Geo.bearingDegrees(link.points.first(), link.points.last())
        return if (match.direction == TravelDirection.MED) {
            along
        } else {
            (along + 180.0) % 360.0
        }
    }

    private fun score(link: RoadLink, fix: GpsFix): Scored? {
        if (link.points.size < 2) return null
        val hit = closestPointOnPolyline(fix.position, link.points)
        if (hit.distanceMeters > max(60.0, fix.accuracyMeters * 3.0)) return null

        val heading = fix.bearingDegrees
        val direction: TravelDirection
        var headingPenalty = 0.0
        if (heading == null) {
            direction = TravelDirection.MED
        } else {
            val delta = Geo.headingDeltaDegrees(heading, hit.segmentBearing)
            val reverseDelta = Geo.headingDeltaDegrees(heading, (hit.segmentBearing + 180.0) % 360.0)
            if (delta <= headingAlignDegrees && delta <= reverseDelta) {
                direction = TravelDirection.MED
                headingPenalty = delta * 0.15
            } else if (reverseDelta <= headingAlignDegrees) {
                direction = TravelDirection.MOT
                headingPenalty = reverseDelta * 0.15
            } else {
                return null
            }
        }

        val position = interpolateSequencePosition(link, hit.fractionAlong)
        val cost = hit.distanceMeters + headingPenalty
        return Scored(link, direction, position, hit.point, hit.distanceMeters, cost)
    }

    private fun interpolateSequencePosition(
        link: RoadLink,
        fractionAlongLink: Double,
    ): Double {
        return (link.startPos + (link.endPos - link.startPos) * fractionAlongLink).coerceIn(0.0, 1.0)
    }

    private fun deadReckon(previous: Match, fix: GpsFix, dtSeconds: Double): Match {
        val sequence = graph.sequences[previous.sequenceId] ?: return previous
        val speed = fix.speedMetersPerSecond
        if (speed <= 0.5) {
            return previous.copy(
                distanceToLinkMeters = Geo.distanceMeters(fix.position, previous.snapped),
            )
        }
        val deltaMeters = speed * dtSeconds
        val deltaPos = if (sequence.lengthMeters <= 0.0) 0.0 else deltaMeters / sequence.lengthMeters
        val signed = if (previous.direction == TravelDirection.MED) deltaPos else -deltaPos
        var nextPos = (previous.position + signed).coerceIn(0.0, 1.0)
        var nextSequenceId = previous.sequenceId
        var nextDirection = previous.direction
        var nextLinkId = previous.linkId

        if (nextPos == 0.0 || nextPos == 1.0) {
            val continued = continueUnique(previous, nextPos)
            if (continued != null) {
                nextSequenceId = continued.sequenceId
                nextDirection = continued.direction
                nextPos = continued.position
                nextLinkId = continued.linkId
            }
        } else {
            val link = sequence.links.firstOrNull { nextPos >= it.startPos && nextPos <= it.endPos }
            if (link != null) nextLinkId = link.id
        }

        val snapped = snapPosition(nextSequenceId, nextPos) ?: previous.snapped
        return Match(
            linkId = nextLinkId,
            sequenceId = nextSequenceId,
            position = nextPos,
            direction = nextDirection,
            snapped = snapped,
            distanceToLinkMeters = Geo.distanceMeters(fix.position, snapped),
        )
    }

    private fun continueUnique(previous: Match, atPos: Double): Match? {
        val sequence = graph.sequences[previous.sequenceId] ?: return null
        val nodeId = if (atPos >= 1.0) sequence.endNodeId else sequence.startNodeId
        val candidates = graph.linksFromNode(nodeId).filter { it.sequenceId != previous.sequenceId }
        if (candidates.size != 1) return null
        val next = candidates.first()
        val arrivingAtStart = next.startNodeId == nodeId
        val direction = if (arrivingAtStart) TravelDirection.MED else TravelDirection.MOT
        val position = if (arrivingAtStart) next.startPos else next.endPos
        return Match(
            linkId = next.id,
            sequenceId = next.sequenceId,
            position = position,
            direction = direction,
            snapped = snapPosition(next.sequenceId, position) ?: previous.snapped,
            distanceToLinkMeters = previous.distanceToLinkMeters,
        )
    }

    private fun snapPosition(sequenceId: Long, position: Double): LatLon? {
        val sequence = graph.sequences[sequenceId] ?: return null
        val link = sequence.links.firstOrNull { position >= it.startPos - 1e-9 && position <= it.endPos + 1e-9 }
            ?: sequence.links.minByOrNull { abs(((it.startPos + it.endPos) / 2.0) - position) }
            ?: return null
        val span = (link.endPos - link.startPos).let { if (it == 0.0) 1.0 else it }
        val fraction = ((position - link.startPos) / span).coerceIn(0.0, 1.0)
        if (link.points.size < 2) return link.points.firstOrNull()
        val total = (0 until link.points.lastIndex).sumOf {
            Geo.distanceMeters(link.points[it], link.points[it + 1])
        }
        var remaining = fraction * total
        for (index in 0 until link.points.lastIndex) {
            val start = link.points[index]
            val end = link.points[index + 1]
            val length = Geo.distanceMeters(start, end)
            if (remaining <= length || index == link.points.lastIndex - 1) {
                val t = if (length == 0.0) 0.0 else (remaining / length).coerceIn(0.0, 1.0)
                return Geo.interpolate(start, end, t)
            }
            remaining -= length
        }
        return link.points.last()
    }

    private fun projectedScore(match: Match, fix: GpsFix): Double {
        return Geo.distanceMeters(match.snapped, fix.position)
    }

    private data class Scored(
        val link: RoadLink,
        val direction: TravelDirection,
        val position: Double,
        val snapped: LatLon,
        val distanceMeters: Double,
        val cost: Double,
    ) {
        fun toMatch(): Match = Match(
            linkId = link.id,
            sequenceId = link.sequenceId,
            position = position,
            direction = direction,
            snapped = snapped,
            distanceToLinkMeters = distanceMeters,
        )
    }
}
