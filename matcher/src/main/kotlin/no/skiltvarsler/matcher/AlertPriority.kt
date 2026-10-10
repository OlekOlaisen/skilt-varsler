package no.skiltvarsler.matcher

/**
 * User-facing alert priority tiers. Lower number = higher urgency (1 = Høyest).
 * Sort weight for the engine uses [sortWeight] so descending sort still prefers urgent alerts.
 */
object AlertPriority {
    const val HIGHEST = 1
    const val HIGH = 2
    const val MEDIUM = 3
    const val LOW = 4
    const val LOWEST = 5

    val ALL_TIERS: IntRange = HIGHEST..LOWEST

    fun label(tier: Int): String = when (tier.coerceIn(HIGHEST, LOWEST)) {
        HIGHEST -> "Høyest"
        HIGH -> "Høy"
        MEDIUM -> "Medium"
        LOW -> "Lav"
        else -> "Lavest"
    }

    fun coerce(tier: Int): Int = tier.coerceIn(HIGHEST, LOWEST)

    /**
     * Higher weight = show / merge first. Tier dominates; [AlertKind.priority] breaks ties
     * so the built-in ladder still applies when two signs share a tier.
     */
    fun sortWeight(tier: Int, kindPriority: Int): Int {
        val rank = (LOWEST + 1) - coerce(tier)
        return rank * 1_000 + kindPriority
    }

    fun defaultTierFor(kind: AlertKind): Int = when (kind) {
        AlertKind.STOP, AlertKind.SPEED_CAMERA, AlertKind.ACCIDENT -> HIGHEST
        AlertKind.RAILWAY, AlertKind.YIELD,
        AlertKind.SECTION_ATK_START, AlertKind.SECTION_ATK_END,
        AlertKind.TOLL, AlertKind.ROADWORK,
        -> HIGH
        AlertKind.PRIORITY_ROAD, AlertKind.SPEED_LIMIT -> MEDIUM
        AlertKind.HAZARD, AlertKind.FERRY, AlertKind.WILDLIFE -> LOW
        AlertKind.MUNICIPALITY -> LOWEST
    }
}
