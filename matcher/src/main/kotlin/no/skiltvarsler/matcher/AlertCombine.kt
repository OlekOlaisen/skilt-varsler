package no.skiltvarsler.matcher

/**
 * Merges near-simultaneous alerts into one heads-up title/body for Android Auto.
 * Engine still emits individual alerts for trip stats; combining is publish-time only.
 */
object AlertCombine {
    const val COALESCE_WINDOW_MS = 2_000L

    fun dedupeAndSort(alerts: List<Alert>): List<Alert> {
        return alerts
            .distinctBy { alert -> "${alert.kind}:${alert.nvdbId}" }
            .sortedByDescending { alert -> alert.kind.priority }
    }

    /**
     * Returns a single alert whose title/body join all inputs (priority order).
     * Bitmap/icon consumers should use [Alert.kind] from the highest-priority item.
     */
    fun merge(alerts: List<Alert>): Alert {
        val ordered = dedupeAndSort(alerts)
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
