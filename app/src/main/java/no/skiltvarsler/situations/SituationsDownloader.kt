package no.skiltvarsler.situations

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.settings.SettingsStore
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads only situations.json from the tile release base URL.
 * Used while driving so live DATEX updates reach the phone without a full tile prefetch.
 */
object SituationsDownloader {
    /** How often to re-fetch while a trip is active. */
    const val DRIVING_MIN_INTERVAL_MS = 15 * 60 * 1000L

    private val mutex = Mutex()
    @Volatile private var lastAttemptMs: Long = 0L

    suspend fun refreshIfStale(
        context: Context,
        minIntervalMs: Long = DRIVING_MIN_INTERVAL_MS,
        force: Boolean = false,
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!force && now - lastAttemptMs < minIntervalMs) {
            return false
        }
        return mutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && lockedNow - lastAttemptMs < minIntervalMs) {
                return@withLock false
            }
            lastAttemptMs = lockedNow
            val base = SettingsStore(context).tileBaseUrl.first().trimEnd('/')
            withContext(Dispatchers.IO) {
                downloadAndLoad(context, base)
            }
        }
    }

    fun downloadAndLoad(context: Context, baseUrl: String): Boolean {
        val base = baseUrl.trimEnd('/')
        if (base.isBlank()) {
            return false
        }
        val cacheDir = File(context.filesDir, "situations").apply { mkdirs() }
        val target = File(cacheDir, SituationsHolder.FILE_NAME)
        return try {
            downloadAtomicallyJson(target, "$base/${SituationsHolder.FILE_NAME}")
            if (SituationsHolder.loadFile(target)) {
                DebugLog.append(
                    "SITUATIONS refreshed n=${SituationsHolder.all().size} " +
                        "v=${SituationsHolder.version()}",
                )
                true
            } else {
                false
            }
        } catch (error: Exception) {
            DebugLog.append("SITUATIONS refresh failed: ${error.message ?: error.javaClass.simpleName}")
            if (SituationsHolder.all().isEmpty()) {
                SituationsHolder.loadFile(target)
            }
            false
        }
    }

    private fun downloadAtomicallyJson(target: File, url: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        if (tmp.exists()) {
            tmp.delete()
        }
        try {
            downloadTo(tmp, url)
            val text = tmp.readText(Charsets.UTF_8)
            JSONObject(text)
            if (target.exists()) {
                target.delete()
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (error: Exception) {
            if (tmp.exists()) {
                tmp.delete()
            }
            throw error
        }
    }

    private fun downloadTo(target: File, url: String) {
        val connection = openFollowingRedirects(url)
        try {
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openFollowingRedirects(url: String): HttpURLConnection {
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
}
