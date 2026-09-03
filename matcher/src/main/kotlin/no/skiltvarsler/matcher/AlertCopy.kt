package no.skiltvarsler.matcher

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Tile payloads use `code|title|extra` from the NVDB pipeline.
 * Older tiles may be a bare skiltnummer, a name, or `106.1 - description`.
 */
data class ObjectPayload(
    val raw: String,
    val code: String,
    val title: String,
    val extra: String,
) {
    companion object {
        fun parse(raw: String): ObjectPayload {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                return ObjectPayload(raw = trimmed, code = "", title = "", extra = "")
            }
            val parts = trimmed.split('|').map { it.trim() }
            return when (parts.size) {
                1 -> {
                    val dashed = splitDashedCode(parts[0])
                    if (dashed != null) {
                        ObjectPayload(raw = trimmed, code = dashed.first, title = dashed.second, extra = "")
                    } else if (looksLikeCode(parts[0])) {
                        ObjectPayload(raw = trimmed, code = parts[0], title = "", extra = "")
                    } else {
                        ObjectPayload(raw = trimmed, code = "", title = parts[0], extra = "")
                    }
                }
                2 -> {
                    if (looksLikeCode(parts[0])) {
                        ObjectPayload(raw = trimmed, code = parts[0], title = parts[1], extra = "")
                    } else {
                        ObjectPayload(raw = trimmed, code = "", title = parts[0], extra = parts[1])
                    }
                }
                else -> {
                    val code = if (looksLikeCode(parts[0])) parts[0] else ""
                    val title = if (code.isEmpty()) parts[0] else parts[1]
                    val extra = if (code.isEmpty()) parts[1] else parts[2]
                    ObjectPayload(raw = trimmed, code = code, title = title, extra = extra)
                }
            }
        }

        private fun looksLikeCode(value: String): Boolean {
            return value.matches(Regex("""\d+(?:[._]\d+[a-zA-Z]?)?"""))
        }

        private fun splitDashedCode(value: String): Pair<String, String>? {
            val separator = value.indexOf(" - ")
            if (separator <= 0) {
                return null
            }
            val code = value.substring(0, separator).trim()
            val title = value.substring(separator + 3).trim()
            if (!looksLikeCode(code) || title.isEmpty()) {
                return null
            }
            return code to title
        }
    }
}

object AlertCopy {
    const val SKYTTELPASS_DISCOUNT = 0.20

    private val genericTitles = setOf(
        "TOLL",
        "FERRY",
        "RAILWAY",
        "SPEED_CAMERA",
        "SECTION_ATK",
        "WILDLIFE",
        "HAZARD",
        "Ferje",
        "Bomstasjon",
        "Jernbane",
        "Fotoboks",
        "Streknings-ATK",
        "Viltfare",
        "Fareskilt",
        "Tunnel",
    )

    fun titleFor(kind: AlertKind, payload: String): String {
        val parsed = ObjectPayload.parse(payload)
        return when (kind) {
            AlertKind.SPEED_CAMERA -> "Fotoboks"
            AlertKind.TOLL -> namedOrFallback(parsed, "Bomstasjon")
            AlertKind.RAILWAY -> "Jernbane"
            AlertKind.FERRY -> namedOrFallback(parsed, "Ferje")
            AlertKind.STOP -> "Stopp"
            AlertKind.YIELD -> "Vikeplikt"
            AlertKind.HAZARD -> namedOrFallback(parsed, SignLabel.displayName(payload, "Fareskilt"))
            AlertKind.MUNICIPALITY -> SignLabel.displayName(payload, "Kommunegrense")
            AlertKind.PRIORITY_ROAD -> "Forkjørsveg"
            AlertKind.SECTION_ATK_START -> namedOrFallback(parsed, "Streknings-ATK")
            AlertKind.SECTION_ATK_END -> "Slutt streknings-ATK"
            AlertKind.WILDLIFE -> wildlifeTitle(payload)
            AlertKind.SPEED_LIMIT -> payload
        }
    }

    fun bodyFor(kind: AlertKind, metersAhead: Double, payload: String = ""): String {
        if (kind == AlertKind.STOP || kind == AlertKind.YIELD) {
            return "Ved skiltet"
        }
        val extra = extraBody(kind, payload)
        if (metersAhead < 1.0) {
            return extra.ifBlank {
                when (kind) {
                    AlertKind.SECTION_ATK_START -> "Gjennomsnittsfart"
                    AlertKind.SECTION_ATK_END -> "Hold snittfarten"
                    else -> ""
                }
            }
        }
        val distance = "Om ${metersAhead.toInt()} m"
        return if (extra.isBlank()) distance else "$distance · $extra"
    }

    fun extraBody(kind: AlertKind, payload: String): String {
        val parsed = ObjectPayload.parse(payload)
        return when (kind) {
            AlertKind.TOLL -> {
                val price = skyttelpassKroner(parsed.extra)
                if (price.isNullOrBlank()) "" else "$price kr"
            }
            AlertKind.HAZARD -> {
                val meters = parsed.extra.toDoubleOrNull()
                if (meters != null && meters >= 1.0) formatLengthMeters(meters) else ""
            }
            else -> ""
        }
    }

    fun skyttelpassKroner(rawPrice: String): String? {
        val fullPrice = rawPrice.replace(',', '.').toDoubleOrNull() ?: return null
        val discounted = fullPrice * (1.0 - SKYTTELPASS_DISCOUNT)
        return formatKroner(discounted)
    }

    fun formatLengthMeters(meters: Double): String {
        if (meters >= 950.0) {
            val km = meters / 1000.0
            val tenths = (km * 10.0).roundToInt()
            return if (tenths % 10 == 0) {
                "${tenths / 10} km"
            } else {
                "${tenths / 10},${tenths % 10} km"
            }
        }
        return "${meters.roundToInt()} m"
    }

    private fun wildlifeTitle(payload: String): String {
        val parsed = ObjectPayload.parse(payload)
        val art = parsed.title.ifBlank { SignLabel.displayName(payload, "") }
        if (art.isBlank() || isGeneric(art)) {
            return "Viltfare"
        }
        val species = art.replaceFirstChar { character -> character.lowercase() }
        return "Viltfare — $species"
    }

    private fun namedOrFallback(parsed: ObjectPayload, fallback: String): String {
        if (parsed.title.isNotBlank() && !isGeneric(parsed.title)) {
            return parsed.title
        }
        val fromLabel = SignLabel.displayName(parsed.raw, fallback)
        return if (isGeneric(fromLabel)) fallback else fromLabel
    }

    private fun isGeneric(value: String): Boolean {
        return genericTitles.any { it.equals(value, ignoreCase = true) }
    }

    private fun formatKroner(amount: Double): String {
        val ore = (amount * 100.0).roundToLong()
        val kroner = ore / 100
        val remainder = (ore % 100).toInt()
        return if (remainder == 0) {
            kroner.toString()
        } else {
            "%d,%02d".format(kroner, remainder)
        }
    }
}
