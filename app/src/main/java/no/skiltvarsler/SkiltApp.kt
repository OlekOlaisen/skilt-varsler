package no.skiltvarsler

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import no.skiltvarsler.billing.SubscriptionRepository
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.prefetch.TilePrefetchWorker
import no.skiltvarsler.situations.SituationsHolder
import no.skiltvarsler.tilesource.GraphHolder
import no.skiltvarsler.tilesource.KartStatus
import no.skiltvarsler.tilesource.TileInventory
import no.skiltvarsler.tracking.AlertNotifier
import no.skiltvarsler.tracking.LastAlertStore
import java.io.File
import java.util.concurrent.TimeUnit

class SkiltApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SubscriptionRepository.configure(this)
        AlertNotifier.ensureChannels(this)
        DebugLog.init(this)
        val tileCacheDir = File(filesDir, "tiles")
        GraphHolder.loadFromCache(tileCacheDir)
        loadSituations()
        val activeIds = TileInventory.activeIdsFromGraph()
        LastAlertStore.setTileInventory(TileInventory.fromCacheDir(tileCacheDir, activeIds))
        if (GraphHolder.isReady()) {
            LastAlertStore.setTileStatus(KartStatus.fromGraph(GraphHolder.current()))
        } else {
            LastAlertStore.setTileStatus("Ikke lastet ennå")
        }
        val manager = WorkManager.getInstance(this)
        val wifi = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val anyNet = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        manager.enqueueUniquePeriodicWork(
            TilePrefetchWorker.UNIQUE_WIFI,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TilePrefetchWorker>(1, TimeUnit.DAYS)
                .setConstraints(wifi)
                .build(),
        )
        manager.enqueueUniquePeriodicWork(
            TilePrefetchWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TilePrefetchWorker>(6, TimeUnit.HOURS)
                .setConstraints(anyNet)
                .build(),
        )
    }

    private fun loadSituations() {
        val cached = File(filesDir, "situations/${SituationsHolder.FILE_NAME}")
        if (SituationsHolder.loadFile(cached)) {
            return
        }
        try {
            assets.open("situations/${SituationsHolder.FILE_NAME}").bufferedReader().use { reader ->
                SituationsHolder.loadJson(reader.readText())
            }
        } catch (_: Exception) {
            // Live DATEX feed is optional until credentials and release assets exist.
        }
    }
}
