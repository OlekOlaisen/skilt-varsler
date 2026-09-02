package no.skiltvarsler.matcher

data class AlertSettings(
    val speedCamera: Boolean = true,
    val speedLimit: Boolean = true,
    val sectionAtk: Boolean = true,
    val toll: Boolean = true,
    val wildlife: Boolean = true,
    val railway: Boolean = true,
    val ferry: Boolean = true,
    val stop: Boolean = true,
    val yield: Boolean = true,
    val hazard: Boolean = true,
    val priorityRoad: Boolean = false,
    val municipality: Boolean = true,
) {
    fun enabled(kind: AlertKind): Boolean = when (kind) {
        AlertKind.SPEED_CAMERA -> speedCamera
        AlertKind.SPEED_LIMIT -> speedLimit
        AlertKind.SECTION_ATK_START, AlertKind.SECTION_ATK_END -> sectionAtk
        AlertKind.TOLL -> toll
        AlertKind.WILDLIFE -> wildlife
        AlertKind.RAILWAY -> railway
        AlertKind.FERRY -> ferry
        AlertKind.STOP -> stop
        AlertKind.YIELD -> yield
        AlertKind.HAZARD -> hazard
        AlertKind.PRIORITY_ROAD -> priorityRoad
        AlertKind.MUNICIPALITY -> municipality
    }

    companion object {
        val ALL_ON = AlertSettings()
    }
}
