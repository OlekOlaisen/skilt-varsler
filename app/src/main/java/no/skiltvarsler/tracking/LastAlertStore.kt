package no.skiltvarsler.tracking

import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertKind
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

data class UpcomingSign(
    val title: String,
    val metersAhead: Int,
    val kind: AlertKind,
    val payload: String,
    val nvdbId: Long,
) {
    val distanceLabel: String
        get() = if (metersAhead >= 1000) {
            val km = metersAhead / 1000.0
            String.format(java.util.Locale("nb", "NO"), "%.1f km", km)
        } else {
            "$metersAhead m"
        }
}

object LastAlertStore {
    const val MAX_UPCOMING = 6

    private val last = AtomicReference<Alert?>(null)
    private val tracking = AtomicReference("Klar")
    private val tile = AtomicReference("Ingen NVDB-flis lastet")
    private val upcoming = AtomicReference<List<UpcomingSign>>(emptyList())
    private val muted = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile var latitude: Double? = null
        private set
    @Volatile var longitude: Double? = null
        private set
    @Volatile var bearingDegrees: Double? = null
        private set
    @Volatile var trackingActive: Boolean = false
        private set

    val alertsMuted: Boolean
        get() = muted.get()

    fun update(alert: Alert) {
        last.set(alert)
    }

    fun current(): Alert? = last.get()

    fun upcomingSigns(): List<UpcomingSign> = upcoming.get()

    fun setUpcomingSigns(items: List<UpcomingSign>) {
        val next = items.take(MAX_UPCOMING)
        if (upcoming.get() == next) {
            return
        }
        upcoming.set(next)
        notifyListeners()
    }

    fun setAlertsMuted(muted: Boolean) {
        if (this.muted.getAndSet(muted) == muted) {
            return
        }
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun setTracking(status: String) {
        tracking.set(status)
    }

    fun setTrackingActive(active: Boolean) {
        val previous = trackingActive
        trackingActive = active
        if (!active && tracking.get() == "Starter sporing") {
            tracking.set("Stoppet")
        }
        if (!active) {
            setUpcomingSigns(emptyList())
        }
        if (previous != active) {
            notifyListeners()
        }
    }

    fun trackingStatus(): String = tracking.get()

    fun setFix(lat: Double, lon: Double, bearing: Double?) {
        latitude = lat
        longitude = lon
        bearingDegrees = bearing
    }

    fun setTileStatus(status: String) {
        tile.set(status)
        DebugLog.append("TILE $status")
    }

    fun tileStatus(): String = tile.get()

    private fun notifyListeners() {
        listeners.forEach { listener -> listener() }
    }
}

fun roundUpcomingMeters(metersAhead: Double): Int {
    return (metersAhead / 10.0).roundToInt() * 10
}
