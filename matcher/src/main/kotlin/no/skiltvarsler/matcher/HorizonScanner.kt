package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadObject
import no.skiltvarsler.tiles.RoadObjectType
import no.skiltvarsler.tiles.TravelDirection
import kotlin.math.abs

class HorizonScanner(
    private val graph: RoadGraph,
) {
    fun scan(match: Match, speedMetersPerSecond: Double): List<HorizonCandidate> {
        val maxMeters = AlertWindows.maxLookaheadMeters(speedMetersPerSecond)
        val found = ArrayList<HorizonCandidate>()
        val visited = HashSet<Visit>()
        val queue = ArrayDeque<Frame>()
        queue.add(Frame(match.sequenceId, match.position, match.direction, 0.0))

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
                val hitPos = if (obj.isPoint) obj.fromPos else intervalEntry(obj, frame.direction)
                if (hitPos + 1e-9 < rangeStart || hitPos - 1e-9 > rangeEnd) continue
                val ahead = abs(hitPos - frame.position) * sequence.lengthMeters + frame.metersUsed
                if (ahead < 0.5) continue
                found.add(HorizonCandidate(obj, ahead))
            }

            val exitPos = if (frame.direction == TravelDirection.MED) 1.0 else 0.0
            val metersToEnd = abs(exitPos - frame.position) * sequence.lengthMeters
            if (metersToEnd < remaining) {
                val nodeId = if (frame.direction == TravelDirection.MED) sequence.endNodeId else sequence.startNodeId
                for (nextLink in graph.linksFromNode(nodeId)) {
                    if (nextLink.sequenceId == frame.sequenceId) continue
                    val arrivingAtStart = nextLink.startNodeId == nodeId
                    val nextDirection = if (arrivingAtStart) TravelDirection.MED else TravelDirection.MOT
                    val nextPos = if (arrivingAtStart) nextLink.startPos else nextLink.endPos
                    queue.add(
                        Frame(
                            sequenceId = nextLink.sequenceId,
                            position = nextPos,
                            direction = nextDirection,
                            metersUsed = frame.metersUsed + metersToEnd,
                        ),
                    )
                }
            }
        }
        return found.sortedBy { it.metersAhead }
    }

    private fun intervalEntry(obj: RoadObject, travel: TravelDirection): Double {
        return if (travel == TravelDirection.MED) {
            minOf(obj.fromPos, obj.toPos)
        } else {
            maxOf(obj.fromPos, obj.toPos)
        }
    }

    private data class Frame(
        val sequenceId: Long,
        val position: Double,
        val direction: TravelDirection,
        val metersUsed: Double,
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
