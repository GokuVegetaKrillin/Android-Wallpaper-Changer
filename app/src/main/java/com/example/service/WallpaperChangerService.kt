package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.WallshowApp
import com.example.data.repository.UserSettings
import com.example.receiver.WallpaperActionReceiver
import com.example.worker.WallpaperChangerWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WallpaperChangerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var tickerJob: Job? = null
    private var settingsCollectorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiver: BroadcastReceiver? = null

    companion object {
        const val CHANNEL_ID = "wallshow_active_service_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, WallpaperChangerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WallpaperChangerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Wallshow:ServiceWakeLock").apply {
            setReferenceCounted(false)
        }

        registerScreenUnlockReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as WallshowApp
        val currentSettings = app.settingsRepository.getDirectSettings()

        app.settingsRepository.updateSettings { it.copy(serviceRunning = true) }

        // Start Foreground
        val notification = buildNotification(currentSettings)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Schedule WorkManager as reliable backup / reboot keeper
        WallpaperChangerWorker.schedulePeriodic(this, currentSettings.intervalMinutes)

        // Observe settings changes (interval, triggers, targets)
        observeSettings()

        return START_STICKY
    }

    private fun observeSettings() {
        val app = applicationContext as WallshowApp
        settingsCollectorJob?.cancel()
        settingsCollectorJob = serviceScope.launch {
            app.settingsRepository.settings.collectLatest { settings ->
                if (!settings.serviceRunning) {
                    stopSelf()
                    return@collectLatest
                }

                // Update notification
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(settings))

                // Reschedule WorkManager
                WallpaperChangerWorker.schedulePeriodic(this@WallpaperChangerService, settings.intervalMinutes)

                // Restart ticker with new interval
                startTicker(settings.intervalMinutes)
            }
        }
    }

    private fun startTicker(intervalMinutes: Long) {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            val app = applicationContext as WallshowApp
            val intervalMillis = (intervalMinutes * 60 * 1000L).coerceAtLeast(60 * 1000L)

            while (isActive) {
                delay(intervalMillis)
                try {
                    wakeLock?.acquire(3000)
                    app.wallpaperRepository.changeToNextWallpaper()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        if (wakeLock?.isHeld == true) wakeLock?.release()
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun registerScreenUnlockReceiver() {
        if (screenReceiver == null) {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_USER_PRESENT) {
                        val app = applicationContext as WallshowApp
                        val settings = app.settingsRepository.getDirectSettings()
                        if (settings.serviceRunning &&
                            (settings.changeTrigger == "LOCK_UNLOCK" || settings.changeTrigger == "BOTH")
                        ) {
                            serviceScope.launch {
                                app.wallpaperRepository.changeToNextWallpaper()
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun buildNotification(settings: UserSettings): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, WallpaperActionReceiver::class.java).apply {
            action = WallpaperActionReceiver.ACTION_NEXT_WALLPAPER
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WallpaperActionReceiver::class.java).apply {
            action = WallpaperActionReceiver.ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalLabel = formatInterval(settings.intervalMinutes)
        val targetLabel = when (settings.slideshowTarget) {
            "HOME_ONLY" -> "Home Screen"
            "LOCK_ONLY" -> "Lock Screen"
            else -> "Home & Lock"
        }

        val content = "Changing every $intervalLabel • $targetLabel"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.wallshow_launcher_icon_1789793535524)
            .setContentTitle("Wallshow: Wallpaper Rotation Active")
            .setContentText(content)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(
                if (settings.showNotification) NotificationCompat.PRIORITY_LOW
                else NotificationCompat.PRIORITY_MIN
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .build()
    }

    private fun formatInterval(minutes: Long): String {
        return when {
            minutes < 60 -> "$minutes min"
            minutes == 60L -> "1 hour"
            minutes < 1440 -> "${minutes / 60} hours"
            minutes == 1440L -> "1 day"
            else -> "${minutes / 1440} days"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Wallpaper Changer Active Service"
            val descriptionText = "Shows status of automatic wallpaper rotation"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val app = applicationContext as WallshowApp
        app.settingsRepository.updateSettings { it.copy(serviceRunning = false) }

        tickerJob?.cancel()
        settingsCollectorJob?.cancel()

        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {}
            screenReceiver = null
        }

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}

        // Cancel periodic work
        WallpaperChangerWorker.cancelPeriodic(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
