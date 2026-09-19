package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.WallshowApp
import java.util.concurrent.TimeUnit

class WallpaperChangerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WallshowApp ?: return Result.failure()
        val settings = app.settingsRepository.getDirectSettings()

        if (!settings.serviceRunning) {
            return Result.success()
        }

        val success = app.wallpaperRepository.changeToNextWallpaper()
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "wallshow_periodic_changer"

        fun schedulePeriodic(context: Context, intervalMinutes: Long) {
            // WorkManager minimum periodic interval is 15 minutes
            val interval = intervalMinutes.coerceAtLeast(15L)
            val constraints = Constraints.Builder().build()

            val workRequest = PeriodicWorkRequestBuilder<WallpaperChangerWorker>(
                interval,
                TimeUnit.MINUTES,
                5,
                TimeUnit.MINUTES // flex window
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
