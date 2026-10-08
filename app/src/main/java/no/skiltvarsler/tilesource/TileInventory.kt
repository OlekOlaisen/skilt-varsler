package no.skiltvarsler.tilesource

import no.skiltvarsler.prefetch.ManifestTile
import java.io.File

enum class TileCacheTag {
    /** Downloaded this run; file was not on the phone before. */
    NEW,

    /** Downloaded this run; an older version was already on the phone. */
    UPDATED,

    /** Already on the phone; reused without downloading. */
    CACHED,
    ;

    val label: String
        get() = when (this) {
            NEW -> "Ny"
            UPDATED -> "Oppdatert"
            CACHED -> "Lagret"
        }
}

data class TileInventoryItem(
    val id: String,
    val name: String,
    val tag: TileCacheTag,
    /** True when the tile is part of the active map-matching window. */
    val active: Boolean = false,
)

object TileInventory {
    fun displayName(tileId: String): String {
        val names = KartStatus.tileIdsToNames(tileId)
        return KartStatus.formatNames(names).takeIf { it != "Kart lastet" } ?: tileId
    }

    fun classify(
        tile: ManifestTile,
        cacheDir: File,
        localVersions: Map<String, String>,
    ): TileCacheTag {
        val target = File(cacheDir, tile.file)
        if (!target.exists() || !AndroidTileLoader.isReadable(target)) {
            return TileCacheTag.NEW
        }
        val versionOk = localVersions[tile.id] == tile.version
        return if (versionOk) TileCacheTag.CACHED else TileCacheTag.UPDATED
    }

    fun itemFor(
        tile: ManifestTile,
        tag: TileCacheTag,
        activeIds: Set<String>,
    ): TileInventoryItem {
        return TileInventoryItem(
            id = tile.id,
            name = displayName(tile.id),
            tag = tag,
            active = tile.id in activeIds,
        )
    }

    /** Tiles already on disk when the app starts, before a prefetch run. */
    fun fromCacheDir(cacheDir: File, activeIds: Set<String> = emptySet()): List<TileInventoryItem> {
        if (!cacheDir.isDirectory) {
            return emptyList()
        }
        return cacheDir
            .listFiles { file ->
                file.isFile &&
                    file.name.endsWith(".sqlite", ignoreCase = true) &&
                    AndroidTileLoader.isReadable(file)
            }
            .orEmpty()
            .sortedBy { file -> file.name }
            .map { file ->
                val id = file.nameWithoutExtension
                TileInventoryItem(
                    id = id,
                    name = displayName(id),
                    tag = TileCacheTag.CACHED,
                    active = id in activeIds,
                )
            }
    }

    fun activeIdsFromGraph(): Set<String> {
        if (!GraphHolder.isReady()) {
            return emptySet()
        }
        return GraphHolder.current().tileId
            .split('+')
            .map { part -> part.trim() }
            .filter { part -> part.isNotEmpty() }
            .toSet()
    }
}
