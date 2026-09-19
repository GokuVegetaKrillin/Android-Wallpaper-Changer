package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY dateAdded DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY dateAdded DESC")
    suspend fun getAllFoldersList(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE uri = :uri LIMIT 1")
    suspend fun getFolderByUri(uri: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE uri = :uri")
    suspend fun deleteFolderByUri(uri: String)
}

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers WHERE folderUri = :folderUri ORDER BY dateAdded DESC")
    fun getWallpapersForFolder(folderUri: String): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE folderUri = :folderUri ORDER BY dateAdded DESC")
    suspend fun getWallpapersForFolderList(folderUri: String): List<WallpaperEntity>

    @Query("SELECT * FROM wallpapers WHERE isIncludedInSlideshow = 1")
    fun getActiveSlideshowWallpapers(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE isIncludedInSlideshow = 1")
    suspend fun getActiveSlideshowWallpapersList(): List<WallpaperEntity>

    @Query("SELECT * FROM wallpapers")
    fun getAllWallpapers(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers")
    suspend fun getAllWallpapersList(): List<WallpaperEntity>

    @Query("SELECT COUNT(*) FROM wallpapers WHERE folderUri = :folderUri")
    suspend fun getWallpaperCountForFolder(folderUri: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpapers(wallpapers: List<WallpaperEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(wallpaper: WallpaperEntity)

    @Query("UPDATE wallpapers SET isIncludedInSlideshow = :isIncluded WHERE uri = :uri")
    suspend fun updateInclusion(uri: String, isIncluded: Boolean)

    @Query("DELETE FROM wallpapers WHERE folderUri = :folderUri")
    suspend fun deleteWallpapersForFolder(folderUri: String)

    @Query("DELETE FROM wallpapers WHERE uri = :uri")
    suspend fun deleteWallpaperByUri(uri: String)
}

@Dao
interface OperationLogDao {
    @Query("SELECT * FROM operation_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getLogsSince(sinceTimestamp: Long): Flow<List<OperationLogEntity>>

    @Query("SELECT * FROM operation_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getLogsSinceList(sinceTimestamp: Long): List<OperationLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OperationLogEntity)

    @Query("DELETE FROM operation_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneLogsOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM operation_logs")
    suspend fun clearAllLogs()
}
