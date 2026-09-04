package no.skiltvarsler.tiles

import kotlin.math.abs

class RoadGraph(
    val tileId: String,
    val version: String,
    nodes: Collection<RoadNode>,
    links: Collection<RoadLink>,
    sequences: Collection<SequenceInfo>,
    speeds: Collection<SpeedInterval>,
    objects: Collection<RoadObject>,
    val kommunePolygons: List<KommunePolygon> = emptyList(),
) {
    val nodes: Map<Long, RoadNode> = nodes.associateBy { it.id }
    val links: Map<Long, RoadLink> = links.associateBy { it.id }
    val sequences: Map<Long, SequenceInfo> = sequences.associateBy { it.id }
    private val objectsBySequence: Map<Long, List<RoadObject>> = objects.groupBy { it.sequenceId }
    private val speedsBySequence: Map<Long, List<SpeedInterval>> = speeds.groupBy { it.sequenceId }
    private val outgoing: Map<Long, List<RoadLink>> = buildOutgoing(links)
    val spatialGrid: SpatialGrid = SpatialGrid(links)

    fun objectsOn(sequenceId: Long): List<RoadObject> = objectsBySequence[sequenceId].orEmpty()

    fun speedsOn(sequenceId: Long): List<SpeedInterval> = speedsBySequence[sequenceId].orEmpty()

    fun linksFromNode(nodeId: Long): List<RoadLink> = outgoing[nodeId].orEmpty()

    fun speedAt(sequenceId: Long, position: Double, direction: TravelDirection): Int? {
        val matches = coveringSpeeds(sequenceId, position, direction)
        if (matches.isEmpty()) {
            return null
        }
        if (matches.size == 1) {
            return matches.first().kmh
        }
        val entering = if (direction == TravelDirection.MOT) {
            matches.filter { abs(it.toPos - position) <= 1e-9 }
        } else {
            matches.filter { abs(it.fromPos - position) <= 1e-9 }
        }
        return (entering.ifEmpty { matches }).minByOrNull { it.toPos - it.fromPos }?.kmh
    }

    fun upcomingSpeedChange(
        sequenceId: Long,
        position: Double,
        direction: TravelDirection,
        maxMeters: Double,
    ): UpcomingSpeedChange? {
        val currentKmh = speedAt(sequenceId, position, direction)
        var metersUsed = 0.0
        var remaining = maxMeters
        var seqId = sequenceId
        var pos = position
        var travel = direction
        val visited = HashSet<Long>()
        while (remaining > 0.0 && visited.add(seqId)) {
            val sequence = sequences[seqId] ?: break
            if (sequence.lengthMeters <= 0.0) {
                break
            }
            val change = firstSpeedChangeOnSequence(seqId, pos, travel, currentKmh, sequence.lengthMeters)
            if (change != null && change.metersAhead <= remaining) {
                return change.copy(metersAhead = metersUsed + change.metersAhead)
            }
            val exitPos = if (travel == TravelDirection.MED) 1.0 else 0.0
            val metersToEnd = abs(exitPos - pos) * sequence.lengthMeters
            if (metersToEnd >= remaining) {
                break
            }
            val continued = uniqueContinuation(seqId, travel) ?: break
            metersUsed += metersToEnd
            remaining -= metersToEnd
            seqId = continued.sequenceId
            pos = continued.position
            travel = continued.direction
            val nextKmh = speedAt(seqId, pos, travel)
            if (nextKmh != null && nextKmh != currentKmh) {
                return UpcomingSpeedChange(nextKmh, metersUsed, seqId, pos)
            }
        }
        return null
    }

    fun sequencePositionToMeters(sequenceId: Long, fromPos: Double, toPos: Double): Double {
        val sequence = sequences[sequenceId] ?: return 0.0
        return abs(toPos - fromPos) * sequence.lengthMeters
    }

    private fun coveringSpeeds(
        sequenceId: Long,
        position: Double,
        direction: TravelDirection,
    ): List<SpeedInterval> {
        return speedsOn(sequenceId).filter { interval ->
            position >= interval.fromPos - 1e-9 &&
                position <= interval.toPos + 1e-9 &&
                interval.direction.matches(direction)
        }
    }

    private fun firstSpeedChangeOnSequence(
        sequenceId: Long,
        position: Double,
        direction: TravelDirection,
        currentKmh: Int?,
        lengthMeters: Double,
    ): UpcomingSpeedChange? {
        val ahead = speedsOn(sequenceId).filter { interval ->
            interval.direction.matches(direction) &&
                if (direction == TravelDirection.MOT) {
                    interval.toPos < position - 1e-9
                } else {
                    interval.fromPos > position + 1e-9
                }
        }
        val ordered = if (direction == TravelDirection.MOT) {
            ahead.sortedByDescending { it.toPos }
        } else {
            ahead.sortedBy { it.fromPos }
        }
        for (next in ordered) {
            if (currentKmh != null && next.kmh == currentKmh) {
                continue
            }
            val atPos = if (direction == TravelDirection.MOT) next.toPos else next.fromPos
            val metersAhead = abs(atPos - position) * lengthMeters
            return UpcomingSpeedChange(next.kmh, metersAhead, sequenceId, atPos)
        }
        return null
    }

    private data class Continuation(
        val sequenceId: Long,
        val position: Double,
        val direction: TravelDirection,
    )

    private fun uniqueContinuation(sequenceId: Long, direction: TravelDirection): Continuation? {
        val sequence = sequences[sequenceId] ?: return null
        val nodeId = if (direction == TravelDirection.MED) sequence.endNodeId else sequence.startNodeId
        val candidates = linksFromNode(nodeId).filter { it.sequenceId != sequenceId }
        if (candidates.size != 1) {
            return null
        }
        val next = candidates.first()
        val arrivingAtStart = next.startNodeId == nodeId
        return Continuation(
            sequenceId = next.sequenceId,
            position = if (arrivingAtStart) next.startPos else next.endPos,
            direction = if (arrivingAtStart) TravelDirection.MED else TravelDirection.MOT,
        )
    }

    companion object {
        private fun buildOutgoing(links: Collection<RoadLink>): Map<Long, List<RoadLink>> {
            val map = HashMap<Long, MutableList<RoadLink>>()
            for (link in links) {
                map.getOrPut(link.startNodeId) { mutableListOf() }.add(link)
                if (link.endNodeId != link.startNodeId) {
                    map.getOrPut(link.endNodeId) { mutableListOf() }.add(link)
                }
            }
            return map
        }
    }
}
