package com.alexhr90.muabet.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alexhr90.muabet.data.settings.AppSettings
import com.alexhr90.muabet.widget.WatchedWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the one periodic [SyncWorker] in step with the settings and the widget. */
@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun sync(s: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (!s.notifications && !WatchedWidget.isPlaced(context)) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val minutes = s.notifyIntervalMin.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** One run now (the widget's refresh button, a newly placed widget). */
    fun runOnce() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val PERIODIC = "sync"
        const val ONCE = "sync-now"
    }
}
