package no.skiltvarsler.tilesource

import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadGraphBuilder
import no.skiltvarsler.tiles.TileCoverage
import no.skiltvarsler.tiles.TileSelector
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object GraphHolder {
    const val EMPTY_TILE_ID = "empty"
    const val ACTIVE_LIST = "active.txt"

    private val graph = AtomicReference(emptyGraph())
    private val shifting = AtomicBoolean(false)
    private val loadLock = Any()
    private val windowFiles = AtomicReference<List<File>>(emptyList())
    private val knownTiles = AtomicReference<List<TileCoverage>>(emptyList())
    @Volatile private var tileCacheDir: File? = null
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

    /**
     * Registers every tile the manifest knows about, so the active window can pull in a
     * neighbouring kommune on its own instead of waiting for the next prefetch run.
     */
    fun setKnownTiles(cacheDir: File, tiles: List<TileCoverage>) {
        tileCacheDir = cacheDir
        knownTiles.set(tiles)
    }

    /**
     * The downloaded tiles that reach into the match window around the position. Tiles still
     * missing from disk are skipped; the prefetch worker downloads those.
     */
    fun windowFilesFor(latitude: Double, longitude: Double): List<File> {
        val cacheDir = tileCacheDir ?: return emptyList()
        return TileSelector.intersectingWindow(
            tiles = knownTiles.get(),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = windowRadiusMeters,
        )
            .map { tile -> File(cacheDir, tile.file) }
            .filter { file -> file.exists() && file.length() > 0L }
            .sortedBy { file -> file.name }
    }

    /**
     * Serialized because the prefetch worker and the tracking loop both load windows; interleaving
     * them would leave the published graph and the window origin describing different positions.
     */
    fun loadNear(files: List<File>, latitude: Double, longitude: Double): Unit = synchronized(loadLock) {
        val ordered = files.sortedBy { file -> file.name }
        windowFiles.set(ordered)
        val cacheDir = ordered.firstOrNull()?.parentFile
        if (cacheDir != null) {
            writeActiveFiles(cacheDir, ordered)
        }
        var radius = AndroidTileLoader.DEFAULT_WINDOW_METERS
        var lastError: Throwable? = null
        while (radius >= 1_500.0) {
            try {
                val next = AndroidTileLoader.loadNear(ordered, latitude, longitude, radius)
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

    /**
     * Reloads the window when the car has left it, or when a tile that reaches into the window
     * has finished downloading. The second case is what closes the alerting gap at a kommune
     * border: the neighbour is pulled in while it is still ahead of the car.
     */
    fun shiftWindowIfNeeded(latitude: Double, longitude: Double) {
        val active = windowFiles.get()
        if (active.isEmpty()) return
        val desired = windowFilesFor(latitude, longitude).ifEmpty { active }
        val filesChanged = desired.map { it.name } != active.map { it.name }
        if (!filesChanged && !movedOutOfWindow(latitude, longitude)) return
        if (!shifting.compareAndSet(false, true)) return
        try {
            loadNear(desired, latitude, longitude)
        } finally {
            shifting.set(false)
        }
    }

    fun loadFromCache(dir: File) {
        val files = activeFiles(dir)
        if (files.isEmpty()) return
        tileCacheDir = dir
        windowFiles.set(files)
    }

    private fun movedOutOfWindow(latitude: Double, longitude: Double): Boolean {
        val originLat = windowLatitude ?: return false
        val originLon = windowLongitude ?: return false
        val moved = Geo.distanceMeters(
            LatLon(originLat, originLon),
            LatLon(latitude, longitude),
        )
        return moved > windowRadiusMeters * 0.4
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
            .sortedBy { file -> file.name }
    }

    private fun emptyGraph(): RoadGraph {
        return RoadGraphBuilder().apply {
            tileId = EMPTY_TILE_ID
            version = "0"
        }.build()
    }
}
