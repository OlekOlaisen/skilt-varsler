package no.skiltvarsler.log

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import no.skiltvarsler.BuildConfig
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.GpsFix
import no.skiltvarsler.matcher.Match
import no.skiltvarsler.tilesource.GraphHolder
import no.skiltvarsler.tracking.LastAlertStore

/**
 * On-demand debug log for sharing a drive with an AI or developer.
 * Nothing is written until the user starts logging on the Test tab.
 */
object DebugLog {
    private const val MAX_FILE_BYTES = 1_500_000L
    private const val MAX_UI_LINES = 400
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    private val writer = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val uiLinesState = MutableStateFlow<List<String>>(emptyList())
    private val enabledState = MutableStateFlow(false)
    private val lock = Any()

    @Volatile
    private var logFile: File? = null

    val lines: StateFlow<List<String>> = uiLinesState.asStateFlow()
    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        val directory = File(appContext.filesDir, "logs")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, "debug-log.txt")
        logFile = file
        writer.execute {
            uiLinesState.value = readTail(file, MAX_UI_LINES)
        }
    }

    fun isEnabled(): Boolean = enabledState.value

    fun start() {
        if (enabledState.value) {
            return
        }
        enabledState.value = true
        appendAlways(
            "LOG START version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                "sdk=${Build.VERSION.SDK_INT} ${Build.MANUFACTURER} ${Build.MODEL}",
        )
        appendAlways(snapshotLine())
    }

    fun stop() {
        if (!enabledState.value) {
            return
        }
        appendAlways("LOG STOP")
        enabledState.value = false
    }

    fun append(message: String) {
        if (!enabledState.value) {
            return
        }
        appendAlways(message)
    }

    fun appendFix(fix: GpsFix, match: Match?, tileStatus: String) {
        if (!enabledState.value) {
            return
        }
        val speedKmh = String.format(Locale.US, "%.0f", fix.speedMetersPerSecond * 3.6)
        val accuracy = String.format(Locale.US, "%.1f", fix.accuracyMeters)
        val bearing = fix.bearingDegrees?.let { String.format(Locale.US, "%.0f", it) } ?: "-"
        val matchText = if (match == null) {
            "MATCH none"
        } else {
            "MATCH seq=${match.sequenceId} link=${match.linkId} " +
                "pos=${String.format(Locale.US, "%.3f", match.position)} " +
                "dir=${match.direction} dist=${String.format(Locale.US, "%.1f", match.distanceToLinkMeters)}m"
        }
        appendAlways(
            String.format(
                Locale.US,
                "GPS %.6f,%.6f acc=%sm spd=%skm/h brg=%s %s graph=%s tile=%s",
                fix.position.latitude,
                fix.position.longitude,
                accuracy,
                speedKmh,
                bearing,
                matchText,
                GraphHolder.identity(),
                tileStatus.replace('\n', ' '),
            ),
        )
    }

    fun appendAlert(alert: Alert) {
        if (!enabledState.value) {
            return
        }
        appendAlways(
            "ALERT ${alert.kind} ${String.format(Locale.US, "%.0f", alert.metersAhead)}m " +
                "\"${oneLine(alert.title)}\" | ${oneLine(alert.body)} " +
                "payload=${oneLine(alert.payload)} nvdb=${alert.nvdbId} seq=${alert.sequenceId}",
        )
    }

    fun clear() {
        uiLinesState.value = emptyList()
        writer.execute {
            val file = logFile ?: return@execute
            synchronized(lock) {
                file.writeText("")
            }
        }
        if (enabledState.value) {
            appendAlways("CLEARED")
        }
    }

    fun shareIntent(context: Context): Intent? {
        val file = logFile
        if (file == null || !file.exists() || file.length() == 0L) {
            return null
        }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_SUFFIX,
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Skilt-varsler debug-logg")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("debug-logg", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Del debug-logg")
    }

    fun copyText(): String {
        val file = logFile
        synchronized(lock) {
            if (file != null && file.exists()) {
                val fromFile = file.readText()
                if (fromFile.isNotBlank()) {
                    return fromFile
                }
            }
        }
        return uiLinesState.value.joinToString("\n")
    }

    fun hasContent(): Boolean {
        val file = logFile
        return (file != null && file.exists() && file.length() > 0L) ||
            uiLinesState.value.isNotEmpty()
    }

    private fun snapshotLine(): String {
        val latitude = LastAlertStore.latitude
        val longitude = LastAlertStore.longitude
        val gps = if (latitude != null && longitude != null) {
            String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
        } else {
            "none"
        }
        val graph = GraphHolder.current()
        return "SNAPSHOT tracking=${LastAlertStore.trackingStatus()} " +
            "active=${LastAlertStore.trackingActive} " +
            "gps=$gps brg=${LastAlertStore.bearingDegrees ?: "-"} " +
            "graph=${GraphHolder.identity()} links=${graph.links.size} " +
            "ready=${GraphHolder.isReady()} " +
            "tile=${oneLine(LastAlertStore.tileStatus())}"
    }

    private fun appendAlways(message: String) {
        val line = "${timeFormat.format(Date())} $message"
        uiLinesState.update { current ->
            (current + line).takeLast(MAX_UI_LINES)
        }
        writer.execute {
            val file = logFile ?: return@execute
            synchronized(lock) {
                rotateIfNeeded(file)
                file.appendText(line + "\n")
            }
        }
    }

    private fun oneLine(value: String): String {
        return value.replace('\n', ' ').replace('\r', ' ')
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_FILE_BYTES) {
            return
        }
        val kept = readTail(file, MAX_UI_LINES * 4)
        file.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    private fun readTail(file: File, maxLines: Int): List<String> {
        if (!file.exists()) {
            return emptyList()
        }
        return try {
            file.readLines().takeLast(maxLines)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
