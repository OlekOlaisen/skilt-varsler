package no.skiltvarsler.matcher

/**
 * Human-readable N300 names for alert text. The skiltnummer stays on
 * [Alert.payload] for icon lookup and must not appear in the spoken/shown title.
 */
object SignLabel {
    private val numberPrefix = Regex(
        """^(\d+)(?:[._](\d+[a-zA-Z]?))?(?:\s*[-–—:|]+\s*|\s+|$)""",
    )
    private val leftoverSeparator = Regex("""^[\s\-–—:|]+""")

    fun displayName(payload: String, fallback: String): String {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return fallback
        val match = numberPrefix.find(trimmed) ?: return trimmed
        val rest = leftoverSeparator.replace(
            trimmed.substring(match.range.last + 1).trim(),
            "",
        ).trim()
        if (rest.isNotEmpty()) {
            val titleOnly = rest.substringBefore('|').trim()
            if (titleOnly.isNotEmpty()) return titleOnly
        }
        val major = match.groupValues[1]
        val minor = distanceSignMinor(major, match.groupValues[2])
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
        "112" to "Steinsprut",
        "114.1" to "Rasfare, høyre side",
        "114.2" to "Rasfare, venstre side",
        "114" to "Rasfare",
        "116" to "Glatt kjørebane",
        "117" to "Farlig vegskulder",
        "118" to "Bevegelig bru",
        "120" to "Kai, strand eller ferjeleie",
        "122" to "Tunnel",
        "124" to "Farlig vegkryss",
        "126" to "Rundkjøring",
        "132" to "Trafikklyssignal",
        "134" to "Planovergang med bom",
        "135" to "Planovergang uten bom",
        "136.1" to "Avstand til planovergang",
        "136.2" to "Avstand til planovergang",
        "136.3" to "Avstand til planovergang",
        "136.31" to "Avstand til planovergang",
        "136" to "Avstand til planovergang",
        "138.1" to "Andreaskors",
        "138.2" to "Andreaskors, flere spor",
        "138" to "Andreaskors",
        "140" to "Avstand til gangfelt",
        "142" to "Barn",
        "144" to "Syklende",
        "146.1" to "Elg",
        "146.2" to "Rein",
        "146.3" to "Hjort",
        "146.4" to "Ku",
        "146.5" to "Sau",
        "146" to "Viltfare",
        "148" to "Møtende trafikk",
        "150" to "Fly",
        "152" to "Sidevind",
        "153" to "Trafikkulykke",
        "154" to "Skiløpere",
        "155" to "Ridende",
        "156" to "Annen fare",
        "202" to "Vikeplikt",
        "204" to "Stopp",
        "206" to "Forkjørsveg",
        "208" to "Slutt på forkjørsveg",
    )

    /** NVDB marks which side of the road a 136 distance plate faces with h or v. */
    private fun distanceSignMinor(major: String, minor: String): String {
        if (major != "136") return minor
        val digits = minor.trimEnd('h', 'H', 'v', 'V')
        return digits.ifEmpty { minor }
    }
}
