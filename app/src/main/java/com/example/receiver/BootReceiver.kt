package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.WallshowApp
import com.example.service.WallpaperChangerService
import com.example.worker.WallpaperChangerWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val app = context.applicationContext as? WallshowApp ?: return
            val settings = app.settingsRepository.getDirectSettings()

            if (settings.serviceRunning) {
                // Restart service or schedule work
                WallpaperChangerService.startService(context)
                WallpaperChangerWorker.schedulePeriodic(context, settings.intervalMinutes)
            }
        }
    }
}
