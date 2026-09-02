package no.skiltvarsler.tilesource

import no.skiltvarsler.matcher.SyntheticGraph
import no.skiltvarsler.tiles.RoadGraph
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object GraphHolder {
    private val graph = AtomicReference(SyntheticGraph.e6VestbyLike())

    fun current(): RoadGraph = graph.get()

    fun replace(next: RoadGraph) {
        graph.set(next)
    }

    fun identity(): String {
        val graph = current()
        return "${graph.tileId}:${graph.version}"
    }

    fun loadFromCache(dir: File) {
        val files = dir.listFiles { file -> file.extension == "sqlite" }?.toList().orEmpty()
        if (files.isEmpty()) return
        try {
            replace(AndroidTileLoader.loadAll(files))
        } catch (_: Exception) {
            // Keep the in-memory synthetic graph if a cached tile is unreadable.
        }
    }
}
