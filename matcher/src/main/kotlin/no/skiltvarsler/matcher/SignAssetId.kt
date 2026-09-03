package no.skiltvarsler.matcher

/**
 * Maps an alert to a Statens vegvesen N300 filename in `assets/trafikkskilt/`.
 *
 * Numbers follow skiltforskriften, not UNECE codes:
 * 202 vikeplikt, 204 stopp, 206 forkjørsveg, 362 fartsgrense,
 * 556.1 punkt-ATK, 556.2 streknings-ATK, 765/792.30 bom, 775 bilferje.
 */
object SignAssetId {
    private val wildlifeByName = mapOf(
        "elg" to "146_1",
        "hjort" to "146_2",
        "rådyr" to "146_2",
        "radyr" to "146_2",
        "rein" to "146_3",
        "storfe" to "146_4",
        "ku" to "146_4",
        "okse" to "146_4",
        "hest" to "146_5",
        "sau" to "146_5",
        "husdyr" to "146_5",
    )

    fun candidates(alert: Alert): List<String> =
        candidates(alert.kind, alert.payload, alert.nvdbId)

    fun candidates(kind: AlertKind, payload: String, nvdbId: Long): List<String> {
        val kindStems = stemsForKind(kind, payload, nvdbId)
        val payloadStems = if (kind == AlertKind.SPEED_LIMIT) {
            emptyList()
        } else {
            stemsFromPayload(payload)
        }
        val ordered = when (kind) {
            AlertKind.HAZARD, AlertKind.WILDLIFE, AlertKind.RAILWAY ->
                payloadStems + kindStems
            else -> kindStems + payloadStems
        }
        return ordered.flatMap { stem ->
            listOf(stem) + namedFallbacks[stem].orEmpty()
        }.distinct().map { "$it.svg" }
    }

    private fun stemsFromPayload(payload: String): List<String> {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return emptyList()

        wildlifeStem(trimmed)?.let { return listOf(it) }

        val dotted = Regex("""(\d+)\.(\d+[a-zA-Z]?)""").find(trimmed)
        if (dotted != null) {
            return listOf("${dotted.groupValues[1]}_${dotted.groupValues[2]}")
        }

        if (Regex("""^\d+[_\-].+""").matches(trimmed)) {
            return listOf(trimmed.replace('-', '_'))
        }

        if (trimmed.all { it.isDigit() }) {
            return if (trimmed.length >= 3) {
                listOf(trimmed, "${trimmed}_0")
            } else {
                emptyList()
            }
        }

        val embedded = Regex("""(\d+)(?:[._](\d+[a-zA-Z]?))?""").find(trimmed) ?: return emptyList()
        val major = embedded.groupValues[1]
        val minor = embedded.groupValues[2]
        if (minor.isEmpty()) {
            return if (major.length >= 3) {
                listOf(major, "${major}_0")
            } else {
                emptyList()
            }
        }
        return listOf("${major}_$minor")
    }

    private fun stemsForKind(kind: AlertKind, payload: String, nvdbId: Long): List<String> {
        return when (kind) {
            AlertKind.SPEED_CAMERA -> listOf("556_0")
            AlertKind.SECTION_ATK_START, AlertKind.SECTION_ATK_END -> listOf("556_2")
            AlertKind.STOP -> listOf("204_0")
            AlertKind.YIELD -> listOf("202_0")
            AlertKind.SPEED_LIMIT -> listOf(speedLimitStem(payload, nvdbId))
            AlertKind.WILDLIFE -> listOf(wildlifeStem(payload) ?: "146_1")
            AlertKind.RAILWAY -> listOf("134_0")
            AlertKind.FERRY -> listOf("775_0")
            AlertKind.TOLL -> listOf("792_30", "765_0")
            AlertKind.HAZARD -> listOf("156_0")
            AlertKind.PRIORITY_ROAD -> listOf("206_0")
            AlertKind.MUNICIPALITY -> emptyList()
        }
    }

    private val namedFallbacks = mapOf(
        "100_1" to listOf("skarp-sving-til-hoeyre"),
        "100_2" to listOf("farlig-sving-til-venstre"),
        "102_1" to listOf("farlige-svinger-den-foerste-til-hoeyre"),
        "153" to listOf("153_0", "trafikkulykke"),
        "153_0" to listOf("trafikkulykke"),
    )

    private fun speedLimitStem(payload: String, nvdbId: Long): String {
        val fromPayload = payload.trim().toIntOrNull()
        val kmh = fromPayload
            ?: nvdbId.takeIf { it in 20L..130L }?.toInt()
            ?: Regex("""\d+""").find(payload)?.value?.toIntOrNull()
            ?: 80
        return "362_$kmh"
    }

    private fun wildlifeStem(payload: String): String? {
        val key = payload.trim().lowercase()
        wildlifeByName[key]?.let { return it }
        return wildlifeByName.entries.firstOrNull { key.contains(it.key) }?.value
    }
}
