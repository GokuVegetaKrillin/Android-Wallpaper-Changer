package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.receiver.WallpaperActionReceiver

object AlarmScheduler {
    private const val REQUEST_CODE_ALARM = 2002

    fun scheduleNextAlarm(context: Context, intervalMinutes: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WallpaperActionReceiver::class.java).apply {
            action = WallpaperActionReceiver.ACTION_SCHEDULED_ALARM
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_ALARM, intent, flags)

        val intervalMillis = (intervalMinutes * 60 * 1000L).coerceAtLeast(60 * 1000L)
        val triggerAtMillis = SystemClock.elapsedRealtime() + intervalMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d("WallpaperChanger", "Scheduled exact alarm in $intervalMinutes min (at +$intervalMillis ms)")
        } catch (e: SecurityException) {
            // Fallback for Android 12+ without SCHEDULE_EXACT_ALARM permission
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                Log.d("WallpaperChanger", "Scheduled inexact alarm in $intervalMinutes min (fallback)")
            } catch (e2: Exception) {
                Log.e("WallpaperChanger", "Failed to schedule alarm", e2)
            }
        } catch (e: Exception) {
            Log.e("WallpaperChanger", "Failed to schedule alarm", e)
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WallpaperActionReceiver::class.java).apply {
            action = WallpaperActionReceiver.ACTION_SCHEDULED_ALARM
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_ALARM, intent, flags)
        try {
            alarmManager.cancel(pendingIntent)
            Log.d("WallpaperChanger", "Cancelled scheduled alarm")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
