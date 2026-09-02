package no.skiltvarsler.tiles

enum class TravelDirection {
    MED,
    MOT,
    BOTH,
    ;

    fun matches(travel: TravelDirection): Boolean {
        if (this == BOTH || travel == BOTH) return true
        return this == travel
    }

    fun reverse(): TravelDirection = when (this) {
        MED -> MOT
        MOT -> MED
        BOTH -> BOTH
    }

    companion object {
        fun fromNvdb(value: String?): TravelDirection = when (value?.uppercase()) {
            "MOT" -> MOT
            "MED" -> MED
            else -> BOTH
        }
    }
}

enum class RoadObjectType {
    SPEED_CAMERA,
    SECTION_ATK,
    TOLL,
    WILDLIFE,
    RAILWAY,
    FERRY,
    STOP,
    YIELD,
    HAZARD,
    PRIORITY_ROAD,
    MUNICIPALITY,
    ;

    companion object {
        fun fromWire(value: String): RoadObjectType = valueOf(value)
    }
}

data class LatLon(
    val latitude: Double,
    val longitude: Double,
)

data class RoadNode(
    val id: Long,
    val position: LatLon,
)

data class RoadLink(
    val id: Long,
    val sequenceId: Long,
    val linkNumber: Int,
    val startNodeId: Long,
    val endNodeId: Long,
    val startPos: Double,
    val endPos: Double,
    val lengthMeters: Double,
    val typeVeg: String,
    val matchable: Boolean,
    val points: List<LatLon>,
    val kommune: Int = 0,
)

data class SequenceInfo(
    val id: Long,
    val lengthMeters: Double,
    val startNodeId: Long,
    val endNodeId: Long,
    val links: List<RoadLink>,
)

data class SpeedInterval(
    val sequenceId: Long,
    val fromPos: Double,
    val toPos: Double,
    val kmh: Int,
    val direction: TravelDirection,
)

data class RoadObject(
    val nvdbId: Long,
    val type: RoadObjectType,
    val sequenceId: Long,
    val fromPos: Double,
    val toPos: Double,
    val direction: TravelDirection,
    val payload: String,
) {
    val isPoint: Boolean get() = fromPos == toPos
}

data class KommunePolygon(
    val kommune: Int,
    val name: String,
    val ring: List<LatLon>,
)
