package no.skiltvarsler

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import no.skiltvarsler.prefetch.TilePrefetchWorker
import no.skiltvarsler.tilesource.GraphHolder
import no.skiltvarsler.tracking.AlertNotifier
import no.skiltvarsler.tracking.LastAlertStore
import java.io.File
import java.util.concurrent.TimeUnit

class SkiltApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlertNotifier.ensureChannels(this)
        GraphHolder.loadFromCache(File(filesDir, "tiles"))
        val cached = GraphHolder.current()
        if (cached.tileId != "fixture-e6-vestby-like") {
            LastAlertStore.setTileStatus("Cache: ${cached.tileId}")
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
}
