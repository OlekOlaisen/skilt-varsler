package no.skiltvarsler.matcher

data class SignOption(
    val id: String,
    val kind: AlertKind,
    val label: String,
    val payload: String = "",
    val nvdbId: Long = 0,
    val categoryKey: String,
    val defaultEnabled: Boolean = true,
)

data class SignGroup(
    val title: String,
    val signs: List<SignOption>,
)

object SignCatalog {
    val speeds: List<SignOption> = listOf(30, 40, 50, 60, 70, 80, 90, 100, 110).map { kmh ->
        SignOption(
            id = "speed:$kmh",
            kind = AlertKind.SPEED_LIMIT,
            label = "$kmh km/t",
            payload = kmh.toString(),
            nvdbId = kmh.toLong(),
            categoryKey = "speedLimit",
        )
    }

    val wildlife: List<SignOption> = listOf(
        SignOption("wildlife:elg", AlertKind.WILDLIFE, "Elg", "Elg", categoryKey = "wildlife"),
        SignOption("wildlife:hjort", AlertKind.WILDLIFE, "Hjort", "Hjort", categoryKey = "wildlife"),
        SignOption("wildlife:rein", AlertKind.WILDLIFE, "Rein", "Rein", categoryKey = "wildlife"),
        SignOption("wildlife:storfe", AlertKind.WILDLIFE, "Storfe", "Storfe", categoryKey = "wildlife"),
        SignOption("wildlife:sau", AlertKind.WILDLIFE, "Sau og hest", "Sau", categoryKey = "wildlife"),
    )

    val hazards: List<SignOption> = listOf(
        hazard("100.1", "Farlig sving til høyre"),
        hazard("100.2", "Farlig sving til venstre"),
        hazard("102.1", "Farlige svinger, første til høyre"),
        hazard("102.2", "Farlige svinger, første til venstre"),
        hazard("104.1", "Bratt bakke, stigning"),
        hazard("104.2", "Bratt bakke, fall"),
        hazard("106.1", "Smalere veg, begge sider"),
        hazard("106.2", "Smalere veg til høyre"),
        hazard("106.3", "Smalere veg til venstre"),
        hazard("108", "Ujevn veg"),
        hazard("109", "Fartshump"),
        hazard("110", "Vegarbeid"),
        hazard("112", "Løs grus"),
        hazard("114.1", "Steinsprang"),
        hazard("114.2", "Steinsprang, motsatt side"),
        hazard("116", "Glatt kjørebane"),
        hazard("117", "Høy vegkant"),
        hazard("118", "Bevegelig bru"),
        hazard("120", "Kai eller ferjeleie"),
        hazard("122", "Tunnel"),
        hazard("124", "Farlig vegkryss"),
        hazard("126", "Rundkjøring"),
        hazard("132", "Trafikklyssignal"),
        hazard("134", "Planovergang med bom"),
        hazard("135", "Planovergang uten bom"),
        hazard("136.1", "Avstand til planovergang (3)"),
        hazard("136.2", "Avstand til planovergang (2)"),
        hazard("136.3", "Avstand til planovergang (1)"),
        hazard("138.1", "Andreaskors"),
        hazard("138.2", "Andreaskors, flere spor"),
        hazard("140", "Gående"),
        hazard("142", "Barn"),
        hazard("144", "Syklende"),
        hazard("148", "Motgående trafikk"),
        hazard("150", "Fly"),
        hazard("152", "Sidevind"),
        hazard("153", "Trafikkulykke"),
        hazard("154", "Skiløpere"),
        hazard("155", "Ridende"),
        hazard("156", "Annen fare"),
    )

    val singles: List<SignOption> = listOf(
        SignOption("camera", AlertKind.SPEED_CAMERA, "Fotoboks", categoryKey = "speedCamera"),
        SignOption("sectionAtk", AlertKind.SECTION_ATK_START, "Streknings-ATK", categoryKey = "sectionAtk"),
        SignOption("toll", AlertKind.TOLL, "Bomstasjon", categoryKey = "toll"),
        SignOption("railway", AlertKind.RAILWAY, "Jernbane", categoryKey = "railway"),
        SignOption("ferry", AlertKind.FERRY, "Ferje", categoryKey = "ferry"),
        SignOption("stop", AlertKind.STOP, "Stopp", categoryKey = "stop"),
        SignOption("yield", AlertKind.YIELD, "Vikeplikt", categoryKey = "yield"),
        SignOption(
            "priorityRoad",
            AlertKind.PRIORITY_ROAD,
            "Forkjørsveg",
            categoryKey = "priorityRoad",
        ),
        SignOption("municipality", AlertKind.MUNICIPALITY, "Kommunegrense", categoryKey = "municipality"),
    )

    val groups: List<SignGroup> = listOf(
        SignGroup("ATK og fart", listOf(singles[0], singles[1]) + speeds),
        SignGroup("Veg og regulering", singles.drop(2)),
        SignGroup("Viltfare", wildlife),
        SignGroup("Fareskilt", hazards),
    )

    val all: List<SignOption> = groups.flatMap { it.signs }

    private val byId: Map<String, SignOption> = all.associateBy { it.id }

    fun option(id: String): SignOption? = byId[id]

    fun optionId(kind: AlertKind, payload: String = ""): String = when (kind) {
        AlertKind.SPEED_LIMIT -> "speed:${payload.trim()}"
        AlertKind.WILDLIFE -> wildlifeId(payload)
        AlertKind.HAZARD -> hazardId(payload)
        AlertKind.SPEED_CAMERA -> "camera"
        AlertKind.SECTION_ATK_START, AlertKind.SECTION_ATK_END -> "sectionAtk"
        AlertKind.TOLL -> "toll"
        AlertKind.RAILWAY -> "railway"
        AlertKind.FERRY -> "ferry"
        AlertKind.STOP -> "stop"
        AlertKind.YIELD -> "yield"
        AlertKind.PRIORITY_ROAD -> "priorityRoad"
        AlertKind.MUNICIPALITY -> "municipality"
    }

    fun categoryKey(kind: AlertKind, payload: String = ""): String {
        return byId[optionId(kind, payload)]?.categoryKey ?: when (kind) {
            AlertKind.SPEED_LIMIT -> "speedLimit"
            AlertKind.WILDLIFE -> "wildlife"
            AlertKind.HAZARD -> "hazard"
            AlertKind.SPEED_CAMERA -> "speedCamera"
            AlertKind.SECTION_ATK_START, AlertKind.SECTION_ATK_END -> "sectionAtk"
            AlertKind.TOLL -> "toll"
            AlertKind.RAILWAY -> "railway"
            AlertKind.FERRY -> "ferry"
            AlertKind.STOP -> "stop"
            AlertKind.YIELD -> "yield"
            AlertKind.PRIORITY_ROAD -> "priorityRoad"
            AlertKind.MUNICIPALITY -> "municipality"
        }
    }

    fun defaultEnabled(id: String): Boolean = byId[id]?.defaultEnabled ?: true

    private fun hazard(number: String, label: String): SignOption {
        return SignOption(
            id = "hazard:$number",
            kind = AlertKind.HAZARD,
            label = label,
            payload = number,
            categoryKey = "hazard",
        )
    }

    private fun wildlifeId(payload: String): String {
        val number = normalizeNumber(payload)
        when (number) {
            "146.1", "146" -> return "wildlife:elg"
            "146.2" -> return "wildlife:hjort"
            "146.3" -> return "wildlife:rein"
            "146.4" -> return "wildlife:storfe"
            "146.5" -> return "wildlife:sau"
        }
        val key = payload.lowercase()
        return when {
            "elg" in key -> "wildlife:elg"
            "hjort" in key || "rådyr" in key || "radyr" in key -> "wildlife:hjort"
            "rein" in key -> "wildlife:rein"
            "storfe" in key || "ku" in key || "okse" in key -> "wildlife:storfe"
            "hest" in key || "sau" in key || "husdyr" in key -> "wildlife:sau"
            else -> "wildlife:other"
        }
    }

    private fun hazardId(payload: String): String {
        val number = normalizeNumber(payload)
        if (number.startsWith("146")) return wildlifeId(payload)
        val exact = "hazard:$number"
        if (byId.containsKey(exact)) return exact
        val major = number.substringBefore('.')
        val majorId = "hazard:$major"
        if (byId.containsKey(majorId)) return majorId
        val dottedMajor = byId.keys.firstOrNull { it.startsWith("hazard:$major.") }
        return dottedMajor ?: exact
    }

    internal fun normalizeNumber(payload: String): String {
        val trimmed = payload.trim()
        val dotted = Regex("""(\d+)[._](\d+[a-zA-Z]?)""").find(trimmed)
        if (dotted != null) {
            return "${dotted.groupValues[1]}.${dotted.groupValues[2]}"
        }
        val major = Regex("""^\d+""").find(trimmed)
        return major?.value ?: trimmed
    }
}
