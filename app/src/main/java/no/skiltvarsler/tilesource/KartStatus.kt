package no.skiltvarsler.tilesource

import no.skiltvarsler.tiles.RoadGraph

/**
 * Formats loaded NVDB map tiles for the UI: human kommune names, never internal tile ids.
 */
object KartStatus {
    fun fromGraph(graph: RoadGraph): String = formatNames(kommuneNamesFor(graph))

    fun kommuneNamesFor(graph: RoadGraph): List<String> {
        val fromPolygons = graph.kommunePolygons
            .groupBy { it.kommune }
            .map { (nummer, polys) ->
                val raw = polys.first().name.trim()
                if (raw.isNotEmpty() && !raw.all { it.isDigit() }) {
                    raw
                } else {
                    KommuneNames.nameFor(nummer)
                }
            }
            .distinct()
            .sorted()
        if (fromPolygons.isNotEmpty()) {
            return fromPolygons
        }
        return tileIdsToNames(graph.tileId)
    }

    fun tileIdsToNames(tileId: String): List<String> {
        return tileId
            .split('+')
            .mapNotNull { part ->
                val match = KOMMUNE_TILE_ID.matchEntire(part.trim()) ?: return@mapNotNull null
                KommuneNames.nameFor(match.groupValues[1].toInt())
            }
            .distinct()
            .sorted()
    }

    fun formatNames(names: List<String>): String {
        return when {
            names.isEmpty() -> "Kart lastet"
            names.size <= 3 -> names.joinToString(", ")
            else -> names.take(3).joinToString(", ") + " m.fl."
        }
    }

    private val KOMMUNE_TILE_ID = Regex("""kommune-(\d+)""", RegexOption.IGNORE_CASE)
}
