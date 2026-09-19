package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.WallshowApp
import com.example.service.WallpaperChangerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallpaperActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NEXT_WALLPAPER = "com.example.action.NEXT_WALLPAPER"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
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
        }
    }
}
