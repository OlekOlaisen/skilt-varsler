package no.skiltvarsler.prefetch

import android.content.Context
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
        try {
            LastAlertStore.setTileStatus("Henter kommune-flis…")
            val manifestJson = JSONObject(downloadText("$base/manifest.json"))
            val allTiles = TilePlanner.parseManifest(manifestJson)
            val localManifest = File(cacheDir, "manifest.json")
            val latitude = LastAlertStore.latitude
            val longitude = LastAlertStore.longitude
            val needed = if (latitude != null && longitude != null) {
                TilePlanner.select(
                    tiles = allTiles,
                    latitude = latitude,
                    longitude = longitude,
                    bearingDegrees = LastAlertStore.bearingDegrees,
                )
            } else {
                refreshCached(allTiles, cacheDir)
            }
            val localVersions = readLocalVersions(localManifest)
            var downloaded = 0
            for (tile in needed) {
                val target = File(cacheDir, tile.file)
                val alreadyHave = target.exists() && localVersions[tile.id] == tile.version
                if (alreadyHave) continue
                val tmp = File(cacheDir, "${tile.file}.tmp")
                downloadTo(tmp, "$base/${tile.file}")
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                downloaded += 1
            }
            localManifest.writeText(manifestJson.toString())
            val files = needed.map { File(cacheDir, it.file) }.filter { it.exists() }
            if (files.isNotEmpty()) {
                GraphHolder.writeActiveFiles(cacheDir, files)
                GraphHolder.replace(AndroidTileLoader.loadAll(files))
            } else if (latitude != null && longitude != null) {
                GraphHolder.clear()
            }
            LastAlertStore.setTileStatus(statusText(allTiles, files, downloaded, latitude, longitude))
            Result.success()
        } catch (error: Exception) {
            LastAlertStore.setTileStatus("Flisfeil: ${error.message ?: error.javaClass.simpleName}")
            Result.retry()
        }
    }

    private fun refreshCached(
        allTiles: List<ManifestTile>,
        cacheDir: File,
    ): List<ManifestTile> {
        val activeNames = GraphHolder.activeFiles(cacheDir).map { it.name }.toSet()
        return allTiles.filter { it.file in activeNames }
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

    private fun readLocalVersions(manifestFile: File): Map<String, String> {
        if (!manifestFile.exists()) return emptyMap()
        return try {
            TilePlanner.parseManifest(JSONObject(manifestFile.readText())).associate { it.id to it.version }
        } catch (_: Exception) {
            emptyMap()
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
    }
}
