package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.WallshowApp
import com.example.service.AlarmScheduler
import com.example.service.WallpaperChangerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallpaperActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NEXT_WALLPAPER = "com.example.action.NEXT_WALLPAPER"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_SCHEDULED_ALARM = "com.example.action.SCHEDULED_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? WallshowApp ?: return

        when (intent.action) {
            ACTION_NEXT_WALLPAPER -> {
                CoroutineScope(Dispatchers.IO).launch {
                    app.wallpaperRepository.changeToNextWallpaper()
                }
            }
            ACTION_STOP_SERVICE -> {
                WallpaperChangerService.stopService(context)
            }
            ACTION_SCHEDULED_ALARM -> {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Wallshow:AlarmWakeLock"
                )
                wakeLock?.acquire(15000L) // 15s max wake lock

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settings = app.settingsRepository.getDirectSettings()
                        if (settings.serviceRunning) {
                            app.wallpaperRepository.changeToNextWallpaper()
                            AlarmScheduler.scheduleNextAlarm(context, settings.intervalMinutes)
                        }
                    } finally {
                        try {
                            if (wakeLock?.isHeld == true) wakeLock.release()
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }
}
