package no.skiltvarsler.tilesource

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadGraphBuilder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object GraphHolder {
    const val EMPTY_TILE_ID = "empty"
    const val ACTIVE_LIST = "active.txt"

    private val graph = AtomicReference(emptyGraph())
    private val shifting = AtomicBoolean(false)
    private val windowFiles = AtomicReference<List<File>>(emptyList())
    @Volatile private var windowLatitude: Double? = null
    @Volatile private var windowLongitude: Double? = null
    @Volatile private var windowRadiusMeters: Double = AndroidTileLoader.DEFAULT_WINDOW_METERS

    fun current(): RoadGraph = graph.get()

    fun isReady(): Boolean = current().links.isNotEmpty()

    fun covers(files: List<File>): Boolean {
        if (!isReady() || files.isEmpty()) {
            return false
        }
        val expected = files.map { it.nameWithoutExtension }.sorted().joinToString("+")
        return current().tileId == expected
    }

    fun replace(next: RoadGraph) {
        graph.set(next)
    }

    fun clear() {
        graph.set(emptyGraph())
        windowLatitude = null
        windowLongitude = null
    }

    fun identity(): String {
        val graph = current()
        return "${graph.tileId}:${graph.version}"
    }

    fun loadNear(files: List<File>, latitude: Double, longitude: Double) {
        windowFiles.set(files)
        val cacheDir = files.firstOrNull()?.parentFile
        if (cacheDir != null) {
            writeActiveFiles(cacheDir, files)
        }
        var radius = AndroidTileLoader.DEFAULT_WINDOW_METERS
        var lastError: Throwable? = null
        while (radius >= 1_500.0) {
            try {
                val next = AndroidTileLoader.loadNear(files, latitude, longitude, radius)
                graph.set(next)
                windowLatitude = latitude
                windowLongitude = longitude
                windowRadiusMeters = radius
                return
            } catch (error: OutOfMemoryError) {
                lastError = error
                radius /= 2.0
            }
        }
        graph.set(emptyGraph())
        throw lastError ?: OutOfMemoryError("for lite minne til kommune-flisen")
    }

    fun shouldShiftWindow(latitude: Double, longitude: Double): Boolean {
        val originLat = windowLatitude ?: return false
        val originLon = windowLongitude ?: return false
        if (windowFiles.get().isEmpty()) return false
        val moved = Geo.distanceMeters(
            LatLon(originLat, originLon),
            LatLon(latitude, longitude),
        )
        return moved > windowRadiusMeters * 0.4
    }

    fun shiftWindowIfNeeded(latitude: Double, longitude: Double) {
        if (!shouldShiftWindow(latitude, longitude)) return
        if (!shifting.compareAndSet(false, true)) return
        try {
            val files = windowFiles.get()
            if (files.isEmpty()) return
            loadNear(files, latitude, longitude)
        } finally {
            shifting.set(false)
        }
    }

    fun loadFromCache(dir: File) {
        val files = activeFiles(dir)
        if (files.isEmpty()) return
        windowFiles.set(files)
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
