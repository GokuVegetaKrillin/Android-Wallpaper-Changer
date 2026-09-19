package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WallpaperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallshowApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var wallpaperRepository: WallpaperRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)
        wallpaperRepository = WallpaperRepository(this, database, settingsRepository)

        // Seed initial sample wallpapers if database is newly initialized
        CoroutineScope(Dispatchers.IO).launch {
            wallpaperRepository.initializeDefaultWallpapersIfEmpty()
        }
    }
}
