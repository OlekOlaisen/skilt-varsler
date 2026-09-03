package no.skiltvarsler.prefetch

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object TilePrefetch {
    const val UNIQUE_NOW = "tile-prefetch-now"

    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<TilePrefetchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NOW,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
