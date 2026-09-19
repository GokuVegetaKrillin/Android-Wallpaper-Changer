package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val imageCount: Int = 0,
    val coverUri: String? = null,
    val isEnabled: Boolean = true,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    @PrimaryKey val uri: String,
    val folderUri: String,
    val displayName: String,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val isIncludedInSlideshow: Boolean = true,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "operation_logs")
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // e.g. "Wallpaper Change", "Pending Home Applied", "Folder Rescan", "Service Started", "Service Stopped", "Manual Change"
    val status: String, // "SUCCESS", "PENDING", "FAILED", "INFO"
    val details: String,
    val wallpaperName: String? = null,
    val wallpaperUri: String? = null
)
