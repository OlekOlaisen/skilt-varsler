package no.skiltvarsler.tiles

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
        val matches = speedsOn(sequenceId).filter { interval ->
            position >= interval.fromPos - 1e-9 &&
                position <= interval.toPos + 1e-9 &&
                interval.direction.matches(direction)
        }
        return matches.maxByOrNull { it.toPos - it.fromPos }?.kmh ?: matches.firstOrNull()?.kmh
    }

    fun sequencePositionToMeters(sequenceId: Long, fromPos: Double, toPos: Double): Double {
        val sequence = sequences[sequenceId] ?: return 0.0
        return kotlin.math.abs(toPos - fromPos) * sequence.lengthMeters
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
