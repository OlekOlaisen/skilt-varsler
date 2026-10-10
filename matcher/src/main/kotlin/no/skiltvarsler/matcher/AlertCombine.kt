package no.skiltvarsler.matcher

/**
 * Merges near-simultaneous alerts into one heads-up title/body for Android Auto.
 * Engine still emits individual alerts for trip stats; combining is publish-time only.
 */
object AlertCombine {
    const val COALESCE_WINDOW_MS = 2_000L

    fun dedupeAndSort(
        alerts: List<Alert>,
        settings: AlertSettings = AlertSettings.ALL_ON,
    ): List<Alert> {
        return alerts
            .distinctBy { alert -> "${alert.kind}:${alert.nvdbId}" }
            .sortedByDescending { alert -> settings.sortWeight(alert) }
    }

    /**
     * Returns a single alert whose title/body join all inputs (priority order).
     * Bitmap/icon consumers should use [Alert.kind] from the highest-priority item.
     */
    fun merge(
        alerts: List<Alert>,
        settings: AlertSettings = AlertSettings.ALL_ON,
    ): Alert {
        val ordered = dedupeAndSort(alerts, settings)
        require(ordered.isNotEmpty()) { "Cannot merge an empty alert list" }
        if (ordered.size == 1) {
            return ordered.first()
        }
        val primary = ordered.first()
        val title = ordered.joinToString(" · ") { alert -> alert.title }
        val body = ordered.map { alert -> alert.body }
            .filter { text -> text.isNotBlank() }
            .distinct()
            .joinToString(" · ")
        return primary.copy(title = title, body = body)
    }
}
