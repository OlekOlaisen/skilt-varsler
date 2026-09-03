package no.skiltvarsler.tracking

import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertCopy
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.matcher.SignOption
import no.skiltvarsler.tiles.RoadObjectType

object TestAlerts {
    fun payloadFor(sign: SignOption): String = when (sign.kind) {
        AlertKind.TOLL -> "792|Sørkedalsveien|42"
        AlertKind.FERRY -> "775|Moss–Horten"
        AlertKind.SECTION_ATK_START -> "556.2|Lærdalstunnelen"
        AlertKind.MUNICIPALITY -> "Oslo"
        AlertKind.RAILWAY -> "134"
        AlertKind.HAZARD -> if (sign.payload.startsWith("122")) {
            "122|Lærdalstunnelen|24500"
        } else {
            sign.payload
        }
        else -> sign.payload
    }

    fun labelFor(sign: SignOption): String = when (sign.kind) {
        AlertKind.TOLL -> "Bomstasjon 42 kr"
        AlertKind.FERRY -> "Ferje Moss–Horten"
        AlertKind.SECTION_ATK_START -> "Streknings-ATK Lærdalstunnelen"
        AlertKind.HAZARD -> if (sign.payload.startsWith("122")) {
            "Lærdalstunnelen"
        } else {
            sign.label
        }
        else -> sign.label
    }

    fun alertFor(sign: SignOption): Alert {
        val payload = payloadFor(sign)
        val metersAhead = when (sign.kind) {
            AlertKind.MUNICIPALITY, AlertKind.SPEED_LIMIT -> 0.0
            else -> 180.0
        }
        val title = when (sign.kind) {
            AlertKind.SPEED_LIMIT -> "Fartsgrense ${sign.payload}"
            else -> AlertCopy.titleFor(sign.kind, payload)
        }
        val body = AlertCopy.bodyFor(sign.kind, metersAhead, payload).ifBlank {
            when (sign.kind) {
                AlertKind.SPEED_LIMIT -> "${sign.payload} km/t"
                AlertKind.MUNICIPALITY -> "Kommunegrense"
                else -> "Om ${metersAhead.toInt()} m"
            }
        }
        return Alert(
            kind = sign.kind,
            nvdbId = if (sign.nvdbId != 0L) sign.nvdbId else sign.id.hashCode().toLong(),
            metersAhead = metersAhead,
            title = title,
            body = body,
            sequenceId = 0L,
            objectType = objectTypeFor(sign.kind),
            payload = payload,
        )
    }

    private fun objectTypeFor(kind: AlertKind): RoadObjectType? = when (kind) {
        AlertKind.SPEED_CAMERA -> RoadObjectType.SPEED_CAMERA
        AlertKind.SECTION_ATK_START, AlertKind.SECTION_ATK_END -> RoadObjectType.SECTION_ATK
        AlertKind.TOLL -> RoadObjectType.TOLL
        AlertKind.WILDLIFE -> RoadObjectType.WILDLIFE
        AlertKind.RAILWAY -> RoadObjectType.RAILWAY
        AlertKind.FERRY -> RoadObjectType.FERRY
        AlertKind.STOP -> RoadObjectType.STOP
        AlertKind.YIELD -> RoadObjectType.YIELD
        AlertKind.HAZARD -> RoadObjectType.HAZARD
        AlertKind.PRIORITY_ROAD -> RoadObjectType.PRIORITY_ROAD
        AlertKind.MUNICIPALITY -> RoadObjectType.MUNICIPALITY
        AlertKind.SPEED_LIMIT -> null
    }
}
