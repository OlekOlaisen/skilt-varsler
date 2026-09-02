package no.skiltvarsler.tracking

import no.skiltvarsler.matcher.Alert
import java.util.concurrent.atomic.AtomicReference

object LastAlertStore {
    private val last = AtomicReference<Alert?>(null)
    private val tracking = AtomicReference("Klar")
    private val tile = AtomicReference("Ingen NVDB-flis lastet")

    @Volatile var latitude: Double? = null
        private set
    @Volatile var longitude: Double? = null
        private set
    @Volatile var bearingDegrees: Double? = null
        private set

    fun update(alert: Alert) {
        last.set(alert)
    }

    fun current(): Alert? = last.get()

    fun setTracking(status: String) {
        tracking.set(status)
    }

    fun trackingStatus(): String = tracking.get()

    fun setFix(lat: Double, lon: Double, bearing: Double?) {
        latitude = lat
        longitude = lon
        bearingDegrees = bearing
    }

    fun setTileStatus(status: String) {
        tile.set(status)
    }

    fun tileStatus(): String = tile.get()
}
