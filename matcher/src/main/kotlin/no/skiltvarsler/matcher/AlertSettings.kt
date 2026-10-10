package no.skiltvarsler.matcher

data class AlertSettings(
    val byId: Map<String, Boolean> = SignCatalog.all.associate { it.id to it.defaultEnabled },
    val categoryFallback: Map<String, Boolean> = emptyMap(),
    /**
     * Sparse user overrides of alert priority tier (1 = Høyest … 5 = Lavest).
     * Missing ids use [SignCatalog.defaultPriorityTier].
     */
    val priorityOverrides: Map<String, Int> = emptyMap(),
    val alertsMuted: Boolean = false,
    val combineAlerts: Boolean = true,
    /**
     * When true, also alert for reminder forkjørsveg plates and same-number speed limits
     * along a continuing stretch (normally silenced after the first confirmation).
     */
    val repeatReminderSigns: Boolean = false,
) {
    fun enabled(kind: AlertKind, payload: String = ""): Boolean {
        val id = SignCatalog.optionId(kind, payload)
        byId[id]?.let { return it }
        val category = SignCatalog.categoryKey(kind, payload)
        return categoryFallback[category] ?: SignCatalog.defaultEnabled(id)
    }

    fun isOn(id: String): Boolean {
        byId[id]?.let { return it }
        val option = SignCatalog.option(id)
        if (option != null) {
            categoryFallback[option.categoryKey]?.let { return it }
            return option.defaultEnabled
        }
        return true
    }

    fun priorityTier(kind: AlertKind, payload: String = ""): Int {
        val id = SignCatalog.optionId(kind, payload)
        return priorityTierForId(id)
    }

    fun priorityTierForId(id: String): Int {
        priorityOverrides[id]?.let { return AlertPriority.coerce(it) }
        return SignCatalog.defaultPriorityTier(id)
    }

    fun hasPriorityOverride(id: String): Boolean = priorityOverrides.containsKey(id)

    fun sortWeight(kind: AlertKind, payload: String = ""): Int {
        return AlertPriority.sortWeight(priorityTier(kind, payload), kind.priority)
    }

    fun sortWeight(alert: Alert): Int = sortWeight(alert.kind, alert.payload)

    fun groupAllOn(group: SignGroup): Boolean = group.signs.all { isOn(it.id) }

    companion object {
        val ALL_ON = AlertSettings()
    }
}
