package no.skiltvarsler.matcher

data class AlertSettings(
    val byId: Map<String, Boolean> = SignCatalog.all.associate { it.id to it.defaultEnabled },
    val categoryFallback: Map<String, Boolean> = emptyMap(),
    val alertsMuted: Boolean = false,
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

    fun groupAllOn(group: SignGroup): Boolean = group.signs.all { isOn(it.id) }

    companion object {
        val ALL_ON = AlertSettings()
    }
}
