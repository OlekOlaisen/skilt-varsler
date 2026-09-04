package no.skiltvarsler.prefetch

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tilesource.AndroidTileLoader
import no.skiltvarsler.tilesource.GraphHolder
import no.skiltvarsler.tracking.LastAlertStore
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TilePrefetchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val base = SettingsStore(applicationContext).tileBaseUrl.first().trimEnd('/')
        if (base.isBlank()) {
            LastAlertStore.setTileStatus("Ingen flis-URL satt")
            return@withContext Result.success()
        }
        val cacheDir = File(applicationContext.filesDir, "tiles").apply { mkdirs() }
        deleteStaleTempFiles(cacheDir)
        try {
            val latitude = LastAlertStore.latitude
            val longitude = LastAlertStore.longitude
            if (latitude == null || longitude == null) {
                LastAlertStore.setTileStatus("Venter på GPS for å hente kommune-flis")
                return@withContext Result.success()
            }
            val localManifest = File(cacheDir, "manifest.json")
            val cachedManifest = readManifestIfFresh(localManifest)
            val manifestJson = cachedManifest ?: JSONObject(downloadText("$base/manifest.json"))
            val allTiles = TilePlanner.parseManifest(manifestJson)
            GraphHolder.setKnownTiles(cacheDir, allTiles.map { it.toCoverage() })
            val windowTiles = TilePlanner.window(allTiles, latitude, longitude)
            val aheadTiles = TilePlanner
                .select(allTiles, latitude, longitude, LastAlertStore.bearingDegrees)
                .filterNot { ahead -> windowTiles.any { it.id == ahead.id } }
            val localVersions = readLocalVersions(localManifest)
            var downloaded = download(windowTiles, cacheDir, base, localVersions)
            if (cachedManifest == null) {
                localManifest.writeText(manifestJson.toString())
            }
            val files = GraphHolder.windowFilesFor(latitude, longitude)
            if (files.isEmpty()) {
                GraphHolder.clear()
                LastAlertStore.setTileStatus(statusText(allTiles, files, downloaded, latitude, longitude))
                return@withContext Result.success()
            }
            if (downloaded > 0 || !GraphHolder.covers(files)) {
                try {
                    GraphHolder.loadNear(files, latitude, longitude)
                } catch (error: SQLiteException) {
                    files.forEach { file -> file.delete() }
                    LastAlertStore.setTileStatus("Flisfeil: korrupt kommune-flis, henter på nytt")
                    return@withContext Result.retry()
                }
            }
            downloaded += download(aheadTiles, cacheDir, base, localVersions)
            LastAlertStore.setTileStatus(statusText(allTiles, files, downloaded, latitude, longitude))
            Result.success()
        } catch (error: OutOfMemoryError) {
            GraphHolder.clear()
            LastAlertStore.setTileStatus("Flisfeil: for lite minne til kommune-flisen")
            Result.failure()
        } catch (error: Exception) {
            LastAlertStore.setTileStatus("Flisfeil: ${error.message ?: error.javaClass.simpleName}")
            Result.retry()
        }
    }

    private fun download(
        tiles: List<ManifestTile>,
        cacheDir: File,
        base: String,
        localVersions: Map<String, String>,
    ): Int {
        var downloaded = 0
        for (tile in tiles) {
            val target = File(cacheDir, tile.file)
            val versionOk = localVersions[tile.id] == tile.version
            if (target.exists() && !AndroidTileLoader.isReadable(target)) {
                target.delete()
            }
            if (target.exists() && versionOk && AndroidTileLoader.isReadable(target)) {
                continue
            }
            LastAlertStore.setTileStatus("Henter kommune-flis…")
            downloadAtomically(target, "$base/${tile.file}")
            downloaded += 1
        }
        return downloaded
    }

    private fun statusText(
        allTiles: List<ManifestTile>,
        files: List<File>,
        downloaded: Int,
        latitude: Double?,
        longitude: Double?,
    ): String {
        if (allTiles.isEmpty()) return "Ingen fliser i manifestet"
        if (files.isEmpty() && latitude != null && longitude != null) {
            return "Ingen flis for denne posisjonen ennå"
        }
        if (files.isEmpty()) {
            return "Venter på GPS for å hente kommune-flis"
        }
        val graph = GraphHolder.current()
        return "Fliser: ${graph.tileId} (${files.size} filer, $downloaded nye)"
    }

    /**
     * Driving into new territory enqueues this worker often, so the manifest is reused for a while
     * instead of being downloaded on every run. The periodic jobs pick up newer tile versions.
     */
    private fun readManifestIfFresh(manifestFile: File): JSONObject? {
        if (!manifestFile.exists()) return null
        if (System.currentTimeMillis() - manifestFile.lastModified() > MANIFEST_MAX_AGE_MS) return null
        return try {
            JSONObject(manifestFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun readLocalVersions(manifestFile: File): Map<String, String> {
        if (!manifestFile.exists()) return emptyMap()
        return try {
            TilePlanner.parseManifest(JSONObject(manifestFile.readText())).associate { it.id to it.version }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun deleteStaleTempFiles(cacheDir: File) {
        cacheDir.listFiles { file -> file.name.endsWith(".tmp") }?.forEach { file ->
            file.delete()
        }
    }

    private fun downloadAtomically(target: File, url: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        if (tmp.exists()) {
            tmp.delete()
        }
        try {
            downloadTo(tmp, url)
            if (!AndroidTileLoader.isReadable(tmp)) {
                tmp.delete()
                error("korrupt nedlasting av ${target.name}")
            }
            if (target.exists()) {
                target.delete()
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            if (!AndroidTileLoader.isReadable(target)) {
                target.delete()
                error("korrupt kommune-flis etter lagring av ${target.name}")
            }
        } catch (error: Exception) {
            if (tmp.exists()) {
                tmp.delete()
            }
            throw error
        }
    }

    private fun downloadText(url: String): String {
        open(url).inputStream.use { stream ->
            return stream.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun downloadTo(file: File, url: String) {
        open(url).inputStream.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(5) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "skilt-varsler-app")
            connection.setRequestProperty("Accept", "*/*")
            val code = connection.responseCode
            if (code in 300..399) {
                val next = connection.getHeaderField("Location") ?: error("Redirect uten Location: $current")
                connection.disconnect()
                current = if (next.startsWith("http")) next else URL(URL(current), next).toString()
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                error("HTTP $code for $current")
            }
            return connection
        }
        error("For mange redirects for $url")
    }

    companion object {
        const val UNIQUE_NAME = "tile-prefetch"
        const val UNIQUE_WIFI = "tile-prefetch-wifi"
        private const val MANIFEST_MAX_AGE_MS = 60 * 60 * 1000L
    }
}
