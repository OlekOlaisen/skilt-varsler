package no.skiltvarsler.matcher

/**
 * Human-readable N300 names for alert text. The skiltnummer stays on
 * [Alert.payload] for icon lookup and must not appear in the spoken/shown title.
 */
object SignLabel {
    private val numberPrefix = Regex("""^(\d+)(?:[._](\d+[a-zA-Z]?))?(?:\s+|$)""")

    fun displayName(payload: String, fallback: String): String {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return fallback
        val match = numberPrefix.find(trimmed) ?: return trimmed
        val rest = trimmed.substring(match.range.last + 1).trim()
        if (rest.isNotEmpty()) return rest
        val major = match.groupValues[1]
        val minor = match.groupValues[2]
        val dotted = if (minor.isEmpty()) major else "$major.$minor"
        return LABELS[dotted] ?: LABELS[major] ?: fallback
    }

    private val LABELS = mapOf(
        "100.1" to "Farlig sving til høyre",
        "100.2" to "Farlig sving til venstre",
        "100" to "Farlig sving",
        "102.1" to "Farlige svinger, første til høyre",
        "102.2" to "Farlige svinger, første til venstre",
        "102" to "Farlige svinger",
        "104.1" to "Bratt bakke",
        "104.2" to "Bratt bakke",
        "104" to "Bratt bakke",
        "106.1" to "Smalere veg",
        "106.2" to "Smalere veg",
        "106.3" to "Smalere veg",
        "106" to "Smalere veg",
        "108" to "Ujevn veg",
        "109" to "Fartshump",
        "110" to "Vegarbeid",
        "112" to "Steinsprang",
        "114.1" to "Rasfare",
        "114.2" to "Rasfare",
        "114" to "Rasfare",
        "116" to "Glatt kjørebane",
        "117" to "Farlig vegskulder",
        "118" to "Sidevind",
        "120" to "Kai eller ferjeleie",
        "122" to "Tunnel",
        "124" to "Farlig vegkryss",
        "126" to "Rundkjøring",
        "132" to "Trikk",
        "134" to "Jernbaneovergang",
        "135" to "Jernbaneovergang med bom",
        "136.1" to "Avstand til planovergang",
        "136.2" to "Avstand til planovergang",
        "136.3" to "Avstand til planovergang",
        "136.31" to "Avstand til planovergang",
        "136" to "Avstand til planovergang",
        "138.1" to "Fly",
        "138.2" to "Fly",
        "138" to "Fly",
        "140" to "Syklende",
        "142" to "Gående",
        "144" to "Barn",
        "146.1" to "Elg",
        "146.2" to "Hjort",
        "146.3" to "Rein",
        "146.4" to "Husdyr",
        "146.5" to "Husdyr",
        "146" to "Viltfare",
        "148" to "Ridende",
        "150" to "Trafikklyssignal",
        "152" to "Lavtflygende fly",
        "154" to "Motgående trafikk",
        "155" to "Kø",
        "156" to "Annen fare",
        "202" to "Vikeplikt",
        "204" to "Stopp",
        "206" to "Forkjørsveg",
    )
}
