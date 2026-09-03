package com.ntoma.studio

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.Configuration
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.work.CatalogRefreshWorker
import com.ntoma.studio.work.HistoryPruneWorker
import com.ntoma.studio.work.InspirationWorker
import java.util.concurrent.TimeUnit

class NtomaApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.get(this)
        container.crashReporter.install()
        container.notifier.createChannels()
        container.notifier.attachPreferences { container.settings.current() }
        scheduleBackgroundWork()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun scheduleBackgroundWork() {
        val wm = WorkManager.getInstance(this)

        val refresh = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork("catalog_refresh", ExistingPeriodicWorkPolicy.KEEP, refresh)

        val prune = PeriodicWorkRequestBuilder<HistoryPruneWorker>(1, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork("history_prune", ExistingPeriodicWorkPolicy.KEEP, prune)

        val inspiration = PeriodicWorkRequestBuilder<InspirationWorker>(7, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork("weekly_inspiration", ExistingPeriodicWorkPolicy.KEEP, inspiration)
    }
}
