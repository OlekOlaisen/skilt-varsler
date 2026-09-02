package no.skiltvarsler.tiles

data class TileMeta(
    val tileId: String,
    val version: String,
    val warnings: Int = 0,
)

class RoadGraphBuilder {
    private val nodes = LinkedHashMap<Long, RoadNode>()
    private val links = ArrayList<RoadLink>()
    private val sequenceLengths = HashMap<Long, Double>()
    private val speeds = ArrayList<SpeedInterval>()
    private val objects = ArrayList<RoadObject>()
    private val polygons = ArrayList<KommunePolygon>()
    var tileId: String = "unknown"
    var version: String = "0"

    fun addNode(node: RoadNode) {
        nodes[node.id] = node
    }

    fun addLink(link: RoadLink) {
        links.add(link)
        sequenceLengths[link.sequenceId] = (sequenceLengths[link.sequenceId] ?: 0.0) + link.lengthMeters
    }

    fun setSequenceLength(sequenceId: Long, lengthMeters: Double) {
        sequenceLengths[sequenceId] = lengthMeters
    }

    fun addSpeed(interval: SpeedInterval) {
        speeds.add(interval)
    }

    fun addObject(obj: RoadObject) {
        objects.add(obj)
    }

    fun addKommune(polygon: KommunePolygon) {
        polygons.add(polygon)
    }

    fun build(): RoadGraph {
        val sequences = links.groupBy { it.sequenceId }.map { (id, seqLinks) ->
            val ordered = seqLinks.sortedBy { it.startPos }
            SequenceInfo(
                id = id,
                lengthMeters = sequenceLengths[id] ?: ordered.sumOf { it.lengthMeters },
                startNodeId = ordered.first().startNodeId,
                endNodeId = ordered.last().endNodeId,
                links = ordered,
            )
        }
        return RoadGraph(
            tileId = tileId,
            version = version,
            nodes = nodes.values,
            links = links,
            sequences = sequences,
            speeds = speeds,
            objects = objects,
            kommunePolygons = polygons,
        )
    }
}
