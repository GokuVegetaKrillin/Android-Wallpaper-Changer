package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val serviceRunning: Boolean = false,
    val intervalMinutes: Long = 60L,
    val showNotification: Boolean = true,
    val changeTrigger: String = "TIME_ONLY", // TIME_ONLY, LOCK_UNLOCK, BOTH
    val shuffle: Boolean = true,
    val minImageDimension: Int = 500,
    val slideshowTarget: String = "BOTH", // BOTH, HOME_ONLY, LOCK_ONLY
    val homeScreenCount: Int = 2,
    val scaleType: String = "MULTI_SCREEN_ADAPTIVE", // MULTI_SCREEN_ADAPTIVE, CENTER_CROP, FIT_SCREEN
    val lastChangedTimestamp: Long = 0L,
    val currentWallpaperUri: String? = null,
    val currentWallpaperName: String? = null,
    val pendingHomeWallpaperUri: String? = null,
    val pendingHomeWallpaperName: String? = null
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("wallshow_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        return UserSettings(
            serviceRunning = prefs.getBoolean("service_running", false),
            intervalMinutes = prefs.getLong("interval_minutes", 60L),
            showNotification = prefs.getBoolean("show_notification", true),
            changeTrigger = prefs.getString("change_trigger", "TIME_ONLY") ?: "TIME_ONLY",
            shuffle = prefs.getBoolean("shuffle", true),
            minImageDimension = prefs.getInt("min_image_dimension", 500),
            slideshowTarget = prefs.getString("slideshow_target", "BOTH") ?: "BOTH",
            homeScreenCount = prefs.getInt("home_screen_count", 2),
            scaleType = prefs.getString("scale_type", "MULTI_SCREEN_ADAPTIVE") ?: "MULTI_SCREEN_ADAPTIVE",
            lastChangedTimestamp = prefs.getLong("last_changed_timestamp", 0L),
            currentWallpaperUri = prefs.getString("current_wallpaper_uri", null),
            currentWallpaperName = prefs.getString("current_wallpaper_name", null),
            pendingHomeWallpaperUri = prefs.getString("pending_home_wallpaper_uri", null),
            pendingHomeWallpaperName = prefs.getString("pending_home_wallpaper_name", null)
        )
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val updated = transform(_settings.value)
        prefs.edit().apply {
            putBoolean("service_running", updated.serviceRunning)
            putLong("interval_minutes", updated.intervalMinutes)
            putBoolean("show_notification", updated.showNotification)
            putString("change_trigger", updated.changeTrigger)
            putBoolean("shuffle", updated.shuffle)
            putInt("min_image_dimension", updated.minImageDimension)
            putString("slideshow_target", updated.slideshowTarget)
            putInt("home_screen_count", updated.homeScreenCount)
            putString("scale_type", updated.scaleType)
            putLong("last_changed_timestamp", updated.lastChangedTimestamp)
            putString("current_wallpaper_uri", updated.currentWallpaperUri)
            putString("current_wallpaper_name", updated.currentWallpaperName)
            putString("pending_home_wallpaper_uri", updated.pendingHomeWallpaperUri)
            putString("pending_home_wallpaper_name", updated.pendingHomeWallpaperName)
            apply()
        }
        _settings.value = updated
    }

    fun getDirectSettings(): UserSettings = loadSettings()
}
