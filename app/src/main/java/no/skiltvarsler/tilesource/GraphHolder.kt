package no.skiltvarsler.tilesource

import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadGraphBuilder
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object GraphHolder {
    const val EMPTY_TILE_ID = "empty"
    const val ACTIVE_LIST = "active.txt"

    private val graph = AtomicReference(emptyGraph())

    fun current(): RoadGraph = graph.get()

    fun isReady(): Boolean = current().links.isNotEmpty()

    fun replace(next: RoadGraph) {
        graph.set(next)
    }

    fun clear() {
        graph.set(emptyGraph())
    }

    fun identity(): String {
        val graph = current()
        return "${graph.tileId}:${graph.version}"
    }

    fun loadFromCache(dir: File) {
        val files = activeFiles(dir)
        if (files.isEmpty()) return
        try {
            replace(AndroidTileLoader.loadAll(files))
        } catch (_: Exception) {
            // Keep the empty graph if a cached tile is unreadable.
        }
    }

    fun writeActiveFiles(dir: File, files: List<File>) {
        File(dir, ACTIVE_LIST).writeText(files.joinToString("\n") { it.name })
    }

    fun activeFiles(dir: File): List<File> {
        val listed = File(dir, ACTIVE_LIST)
        if (!listed.exists()) return emptyList()
        return listed.readLines()
            .map { it.trim() }
            .filter { it.endsWith(".sqlite") }
            .map { File(dir, it) }
            .filter { it.exists() }
    }

    private fun emptyGraph(): RoadGraph {
        return RoadGraphBuilder().apply {
            tileId = EMPTY_TILE_ID
            version = "0"
        }.build()
    }
}
