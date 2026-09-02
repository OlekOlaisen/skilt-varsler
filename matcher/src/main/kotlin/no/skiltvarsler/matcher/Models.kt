package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadObject
import no.skiltvarsler.tiles.RoadObjectType
import no.skiltvarsler.tiles.TravelDirection

data class GpsFix(
    val timeMs: Long,
    val position: LatLon,
    val accuracyMeters: Double,
    val speedMetersPerSecond: Double,
    val bearingDegrees: Double?,
)

data class Match(
    val linkId: Long,
    val sequenceId: Long,
    val position: Double,
    val direction: TravelDirection,
    val snapped: LatLon,
    val distanceToLinkMeters: Double,
)

data class HorizonCandidate(
    val obj: RoadObject,
    val metersAhead: Double,
)

enum class AlertKind {
    SPEED_CAMERA,
    SPEED_LIMIT,
    SECTION_ATK_START,
    SECTION_ATK_END,
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

    val priority: Int
        get() = when (this) {
            STOP, SPEED_CAMERA -> 100
            RAILWAY, YIELD -> 80
            SECTION_ATK_START, SECTION_ATK_END -> 70
            SPEED_LIMIT, TOLL -> 60
            HAZARD, FERRY, WILDLIFE -> 40
            PRIORITY_ROAD, MUNICIPALITY -> 10
        }
}

data class Alert(
    val kind: AlertKind,
    val nvdbId: Long,
    val metersAhead: Double,
    val title: String,
    val body: String,
    val sequenceId: Long,
    val objectType: RoadObjectType?,
    val payload: String = "",
)
