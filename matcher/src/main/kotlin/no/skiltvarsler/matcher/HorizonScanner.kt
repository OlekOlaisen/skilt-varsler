package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadLink
import no.skiltvarsler.tiles.RoadObject
import no.skiltvarsler.tiles.RoadObjectType
import no.skiltvarsler.tiles.SequenceInfo
import no.skiltvarsler.tiles.TravelDirection
import kotlin.math.abs

class HorizonScanner(
    private var graph: RoadGraph,
    /** Max heading change to treat an outgoing link as the same through road. */
    private val continueHeadingDegrees: Double = JunctionPolicy.CONTINUE_HEADING_DEGREES,
    private val bestContinuationMarginDegrees: Double = JunctionPolicy.BEST_CONTINUATION_MARGIN_DEGREES,
    private val junctionControlMaxTurnDegrees: Double = 20.0,
) {
    fun updateGraph(next: RoadGraph) {
        graph = next
    }

    fun scan(match: Match, speedMetersPerSecond: Double): List<HorizonCandidate> {
        val maxMeters = AlertWindows.maxLookaheadMeters(speedMetersPerSecond)
        val found = ArrayList<HorizonCandidate>()
        val visited = HashSet<Visit>()
        val startHeading = headingOnSequence(match.sequenceId, match.position, match.direction)
        val queue = ArrayDeque<Frame>()
        queue.add(Frame(match.sequenceId, match.position, match.direction, 0.0, startHeading))

        while (queue.isNotEmpty()) {
            val frame = queue.removeFirst()
            val visit = Visit(frame.sequenceId, frame.direction, (frame.position * 1000).toInt())
            if (!visited.add(visit)) continue
            val sequence = graph.sequences[frame.sequenceId] ?: continue
            val remaining = maxMeters - frame.metersUsed
            if (remaining <= 0.0 || sequence.lengthMeters <= 0.0) continue

            val posDelta = remaining / sequence.lengthMeters
            val rangeStart: Double
            val rangeEnd: Double
            if (frame.direction == TravelDirection.MED) {
                rangeStart = frame.position
                rangeEnd = (frame.position + posDelta).coerceAtMost(1.0)
            } else {
                rangeStart = (frame.position - posDelta).coerceAtLeast(0.0)
                rangeEnd = frame.position
            }

            for (obj in graph.objectsOn(frame.sequenceId)) {
                if (!obj.direction.matches(frame.direction)) continue
                if (!includeObject(obj, match.sequenceId, frame, startHeading)) continue
                val hitPos = if (obj.isPoint) obj.fromPos else intervalEntry(obj, frame.direction)
                if (hitPos + 1e-9 < rangeStart || hitPos - 1e-9 > rangeEnd) continue
                val ahead = abs(hitPos - frame.position) * sequence.lengthMeters + frame.metersUsed
                if (ahead < 0.5) continue
                found.add(HorizonCandidate(obj, ahead))
            }

            val exitPos = if (frame.direction == TravelDirection.MED) 1.0 else 0.0
            val metersToEnd = abs(exitPos - frame.position) * sequence.lengthMeters
            if (metersToEnd < remaining) {
                val nodeId = if (frame.direction == TravelDirection.MED) {
                    sequence.endNodeId
                } else {
                    sequence.startNodeId
                }
                val nextLinks = graph.linksFromNode(nodeId)
                    .filter { it.sequenceId != frame.sequenceId }
                val travelHeading = frame.travelHeading
                    ?: headingOnSequence(frame.sequenceId, exitPos, frame.direction)
                for (next in bestContinuations(nextLinks, nodeId, travelHeading)) {
                    queue.add(
                        Frame(
                            sequenceId = next.link.sequenceId,
                            position = next.position,
                            direction = next.direction,
                            metersUsed = frame.metersUsed + metersToEnd,
                            travelHeading = next.heading,
                        ),
                    )
                }
            }
        }
        return found.sortedBy { it.metersAhead }
    }

    /**
     * Stop and yield apply to the approach you are on. Following a side street into the
     * horizon must not surface those plates while the car stays on the through road.
     */
    private fun includeObject(
        obj: RoadObject,
        matchedSequenceId: Long,
        frame: Frame,
        startHeading: Double?,
    ): Boolean {
        if (obj.type != RoadObjectType.STOP && obj.type != RoadObjectType.YIELD) {
            return true
        }
        if (frame.sequenceId == matchedSequenceId) {
            return true
        }
        val pathHeading = frame.travelHeading ?: return false
        val origin = startHeading ?: return false
        return Geo.headingDeltaDegrees(origin, pathHeading) <= junctionControlMaxTurnDegrees
    }

    /**
     * Among outgoing links within [continueHeadingDegrees], keep only those close to the
     * straightest option so a 30° side street is ignored when the through road continues.
     */
    private fun bestContinuations(
        nextLinks: List<RoadLink>,
        nodeId: Long,
        travelHeading: Double?,
    ): List<Continuation> {
        if (nextLinks.isEmpty()) return emptyList()
        val scored = ArrayList<Continuation>()
        for (nextLink in nextLinks) {
            val arrivingAtStart = nextLink.startNodeId == nodeId
            val nextDirection = if (arrivingAtStart) TravelDirection.MED else TravelDirection.MOT
            val nextHeading = entryHeading(nextLink, arrivingAtStart)
            val nextPos = if (arrivingAtStart) nextLink.startPos else nextLink.endPos
            scored.add(
                Continuation(
                    link = nextLink,
                    direction = nextDirection,
                    position = nextPos,
                    heading = nextHeading,
                ),
            )
        }
        if (travelHeading == null) {
            return if (scored.size == 1) scored else emptyList()
        }
        val withDelta = scored.mapNotNull { continuation ->
            val heading = continuation.heading ?: return@mapNotNull null
            continuation to Geo.headingDeltaDegrees(travelHeading, heading)
        }
        if (withDelta.isEmpty()) {
            return if (scored.size == 1) scored else emptyList()
        }
        val bestDelta = withDelta.minOf { it.second }
        if (bestDelta > continueHeadingDegrees) {
            return emptyList()
        }
        val cutoff = (bestDelta + bestContinuationMarginDegrees).coerceAtMost(continueHeadingDegrees)
        return withDelta.filter { it.second <= cutoff }.map { it.first }
    }

    private fun headingOnSequence(
        sequenceId: Long,
        position: Double,
        direction: TravelDirection,
    ): Double? {
        val sequence = graph.sequences[sequenceId] ?: return null
        val link = linkAt(sequence, position) ?: return null
        return headingOnLink(link, position, direction)
    }

    private fun linkAt(sequence: SequenceInfo, position: Double): RoadLink? {
        return sequence.links.firstOrNull { link ->
            position >= link.startPos - 1e-9 && position <= link.endPos + 1e-9
        } ?: sequence.links.minByOrNull { link ->
            abs(((link.startPos + link.endPos) / 2.0) - position)
        }
    }

    private fun headingOnLink(
        link: RoadLink,
        sequencePosition: Double,
        travel: TravelDirection,
    ): Double? {
        if (link.points.size < 2) return null
        val span = (link.endPos - link.startPos).let { if (it == 0.0) 1.0 else it }
        val fraction = ((sequencePosition - link.startPos) / span).coerceIn(0.0, 1.0)
        val total = (0 until link.points.lastIndex).sumOf { index ->
            Geo.distanceMeters(link.points[index], link.points[index + 1])
        }
        if (total <= 0.0) {
            val bearing = Geo.bearingDegrees(link.points.first(), link.points.last())
            return if (travel == TravelDirection.MED) bearing else (bearing + 180.0) % 360.0
        }
        var remaining = fraction * total
        var start = link.points[0]
        var end = link.points[1]
        for (index in 0 until link.points.lastIndex) {
            start = link.points[index]
            end = link.points[index + 1]
            val length = Geo.distanceMeters(start, end)
            if (remaining <= length || index == link.points.lastIndex - 1) break
            remaining -= length
        }
        val bearing = Geo.bearingDegrees(start, end)
        return if (travel == TravelDirection.MED) bearing else (bearing + 180.0) % 360.0
    }

    private fun entryHeading(link: RoadLink, arrivingAtStart: Boolean): Double? {
        if (link.points.size < 2) return null
        return if (arrivingAtStart) {
            Geo.bearingDegrees(link.points.first(), link.points[1])
        } else {
            Geo.bearingDegrees(link.points.last(), link.points[link.points.lastIndex - 1])
        }
    }

    private fun intervalEntry(obj: RoadObject, travel: TravelDirection): Double {
        return if (travel == TravelDirection.MED) {
            minOf(obj.fromPos, obj.toPos)
        } else {
            maxOf(obj.fromPos, obj.toPos)
        }
    }

    private data class Continuation(
        val link: RoadLink,
        val direction: TravelDirection,
        val position: Double,
        val heading: Double?,
    )

    private data class Frame(
        val sequenceId: Long,
        val position: Double,
        val direction: TravelDirection,
        val metersUsed: Double,
        val travelHeading: Double?,
    )

    private data class Visit(
        val sequenceId: Long,
        val direction: TravelDirection,
        val posMillis: Int,
    )
}

fun RoadObjectType.toAlertKind(): AlertKind? = when (this) {
    RoadObjectType.SPEED_CAMERA -> AlertKind.SPEED_CAMERA
    RoadObjectType.SECTION_ATK -> AlertKind.SECTION_ATK_START
    RoadObjectType.TOLL -> AlertKind.TOLL
    RoadObjectType.WILDLIFE -> AlertKind.WILDLIFE
    RoadObjectType.RAILWAY -> AlertKind.RAILWAY
    RoadObjectType.FERRY -> AlertKind.FERRY
    RoadObjectType.STOP -> AlertKind.STOP
    RoadObjectType.YIELD -> AlertKind.YIELD
    RoadObjectType.HAZARD -> AlertKind.HAZARD
    RoadObjectType.PRIORITY_ROAD -> AlertKind.PRIORITY_ROAD
    RoadObjectType.MUNICIPALITY -> AlertKind.MUNICIPALITY
}
