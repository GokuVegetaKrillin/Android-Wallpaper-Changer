package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.FolderEntity
import com.example.data.local.OperationLogEntity
import com.example.data.local.WallpaperEntity
import com.example.engine.WallpaperApplyResult
import com.example.engine.WallpaperCropper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class RescanResult(
    val folderName: String,
    val addedCount: Int,
    val removedCount: Int,
    val totalCount: Int
)

class WallpaperRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val folderDao = database.folderDao()
    private val wallpaperDao = database.wallpaperDao()
    private val operationLogDao = database.operationLogDao()

    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val allWallpapers: Flow<List<WallpaperEntity>> = wallpaperDao.getAllWallpapers()
    val activeSlideshowWallpapers: Flow<List<WallpaperEntity>> = wallpaperDao.getActiveSlideshowWallpapers()

    fun getPast24HourLogs(): Flow<List<OperationLogEntity>> {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return operationLogDao.getLogsSince(cutoff)
    }

    suspend fun logOperation(
        action: String,
        status: String,
        details: String,
        wallpaperName: String? = null,
        wallpaperUri: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            operationLogDao.pruneLogsOlderThan(cutoff)
            operationLogDao.insertLog(
                OperationLogEntity(
                    timestamp = System.currentTimeMillis(),
                    action = action,
                    status = status,
                    details = details,
                    wallpaperName = wallpaperName,
                    wallpaperUri = wallpaperUri
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        operationLogDao.clearAllLogs()
    }

    fun getWallpapersForFolder(folderUri: String): Flow<List<WallpaperEntity>> =
        wallpaperDao.getWallpapersForFolder(folderUri)

    suspend fun initializeDefaultWallpapersIfEmpty() = withContext(Dispatchers.IO) {
        val activeCount = wallpaperDao.getActiveSlideshowWallpapersList().size
        if (activeCount > 0) return@withContext

        // Create Default Sample Collection folder
        val sampleFolderUri = "wallshow://sample_pack"
        val sampleFolder = FolderEntity(
            uri = sampleFolderUri,
            name = "Default Sample Wallpapers",
            imageCount = 3,
            coverUri = "drawable://${R.drawable.sample_wallpaper_wide_1789793566854}",
            isEnabled = true
        )
        folderDao.insertFolder(sampleFolder)

        val samples = listOf(
            Triple(
                R.drawable.sample_wallpaper_wide_1789793566854,
                "Fantasy Forest Landscape (16:9)",
                sampleFolderUri
            ),
            Triple(
                R.drawable.sample_wallpaper_tall_1789793579947,
                "Mystic Wizard Tower (9:16)",
                sampleFolderUri
            ),
            Triple(
                R.drawable.sample_wallpaper_square_1789793593777,
                "Enchanted Library (1:1)",
                sampleFolderUri
            )
        )

        val wallpaperEntities = samples.map { (resId, name, fUri) ->
            val uriStr = "drawable://$resId"
            val (w, h) = WallpaperCropper.decodeImageBounds(context, uriStr)
            WallpaperEntity(
                uri = uriStr,
                folderUri = fUri,
                displayName = name,
                width = w,
                height = h,
                sizeBytes = 1024 * 1024L,
                isIncludedInSlideshow = true
            )
        }
        wallpaperDao.insertWallpapers(wallpaperEntities)
        logOperation("Initialization", "INFO", "Initialized default sample collection with 3 wallpapers")
    }

    suspend fun addFolderTree(treeUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val documentFile = DocumentFile.fromTreeUri(context, treeUri)
        val folderName = documentFile?.name ?: "Selected Folder"
        val folderUriStr = treeUri.toString()

        val imageFiles = mutableListOf<DocumentFile>()
        scanDocumentFiles(documentFile, imageFiles)

        val wallpaperEntities = imageFiles.mapNotNull { doc ->
            val uriStr = doc.uri.toString()
            val (w, h) = WallpaperCropper.decodeImageBounds(context, uriStr)
            val name = doc.name ?: "image"
            WallpaperEntity(
                uri = uriStr,
                folderUri = folderUriStr,
                displayName = name,
                width = w,
                height = h,
                sizeBytes = doc.length(),
                isIncludedInSlideshow = true
            )
        }

        val coverUri = wallpaperEntities.firstOrNull()?.uri
        val folderEntity = FolderEntity(
            uri = folderUriStr,
            name = folderName,
            imageCount = wallpaperEntities.size,
            coverUri = coverUri,
            isEnabled = true
        )

        folderDao.insertFolder(folderEntity)
        wallpaperDao.insertWallpapers(wallpaperEntities)

        logOperation(
            action = "Folder Added",
            status = "SUCCESS",
            details = "Added folder \"$folderName\" with ${wallpaperEntities.size} images to queue"
        )

        return@withContext folderName
    }

    suspend fun rescanFolder(folderUriStr: String): RescanResult = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderByUri(folderUriStr) ?: return@withContext RescanResult("Unknown", 0, 0, 0)

        // Sample pack is static
        if (folderUriStr.startsWith("wallshow://sample_pack")) {
            val total = wallpaperDao.getWallpaperCountForFolder(folderUriStr)
            return@withContext RescanResult(folder.name, 0, 0, total)
        }

        // Custom picked selection check
        if (folderUriStr.startsWith("wallshow://custom_selection")) {
            val existing = wallpaperDao.getWallpapersForFolderList(folderUriStr)
            var removed = 0
            for (w in existing) {
                val exists = try {
                    context.contentResolver.openInputStream(Uri.parse(w.uri))?.use { true } ?: false
                } catch (e: Exception) {
                    false
                }
                if (!exists) {
                    wallpaperDao.deleteWallpaperByUri(w.uri)
                    removed++
                }
            }
            val total = wallpaperDao.getWallpaperCountForFolder(folderUriStr)
            folderDao.updateFolder(folder.copy(imageCount = total))
            if (removed > 0) {
                logOperation("Folder Rescan", "INFO", "Rescanned custom pictures: -$removed inaccessible images removed (Total: $total)")
            }
            return@withContext RescanResult(folder.name, 0, removed, total)
        }

        // Document tree folder rescan
        val treeUri = Uri.parse(folderUriStr)
        val documentFile = DocumentFile.fromTreeUri(context, treeUri)
        if (documentFile == null || !documentFile.exists()) {
            val total = wallpaperDao.getWallpaperCountForFolder(folderUriStr)
            return@withContext RescanResult(folder.name, 0, 0, total)
        }

        val diskFiles = mutableListOf<DocumentFile>()
        scanDocumentFiles(documentFile, diskFiles)

        val diskMap = diskFiles.associateBy { it.uri.toString() }
        val existingInDb = wallpaperDao.getWallpapersForFolderList(folderUriStr)
        val existingMap = existingInDb.associateBy { it.uri }

        var removedCount = 0
        for (existingW in existingInDb) {
            if (!diskMap.containsKey(existingW.uri)) {
                wallpaperDao.deleteWallpaperByUri(existingW.uri)
                removedCount++
            }
        }

        var addedCount = 0
        val newEntities = mutableListOf<WallpaperEntity>()
        for ((uriStr, doc) in diskMap) {
            if (!existingMap.containsKey(uriStr)) {
                val (w, h) = WallpaperCropper.decodeImageBounds(context, uriStr)
                newEntities.add(
                    WallpaperEntity(
                        uri = uriStr,
                        folderUri = folderUriStr,
                        displayName = doc.name ?: "image",
                        width = w,
                        height = h,
                        sizeBytes = doc.length(),
                        isIncludedInSlideshow = true // Automatically added to queue!
                    )
                )
                addedCount++
            }
        }

        if (newEntities.isNotEmpty()) {
            wallpaperDao.insertWallpapers(newEntities)
        }

        val totalCount = wallpaperDao.getWallpaperCountForFolder(folderUriStr)
        val updatedList = wallpaperDao.getWallpapersForFolderList(folderUriStr)
        val newCover = updatedList.firstOrNull()?.uri ?: folder.coverUri

        folderDao.updateFolder(
            folder.copy(
                imageCount = totalCount,
                coverUri = newCover
            )
        )

        if (addedCount > 0 || removedCount > 0) {
            logOperation(
                action = "Folder Rescan",
                status = "SUCCESS",
                details = "Rescanned \"${folder.name}\": +$addedCount new images added to queue, -$removedCount removed (Total: $totalCount)"
            )
        }

        RescanResult(folder.name, addedCount, removedCount, totalCount)
    }

    suspend fun rescanAllFolders() = withContext(Dispatchers.IO) {
        val folders = folderDao.getAllFoldersList()
        for (f in folders) {
            rescanFolder(f.uri)
        }
    }

    private fun scanDocumentFiles(dir: DocumentFile?, result: MutableList<DocumentFile>) {
        if (dir == null || !dir.isDirectory) return
        val files = dir.listFiles()
        for (f in files) {
            if (f.isDirectory) {
                scanDocumentFiles(f, result)
            } else {
                val mime = f.type ?: ""
                val name = f.name?.lowercase() ?: ""
                if (mime.startsWith("image/") ||
                    name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".webp")
                ) {
                    result.add(f)
                }
            }
        }
    }

    suspend fun addIndividualImages(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0

        val customFolderUri = "wallshow://custom_selection"
        var existing = folderDao.getFolderByUri(customFolderUri)
        if (existing == null) {
            existing = FolderEntity(
                uri = customFolderUri,
                name = "Custom Picked Pictures",
                imageCount = 0,
                coverUri = uris.first().toString()
            )
            folderDao.insertFolder(existing)
        }

        val list = uris.mapNotNull { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported by provider
            }
            val (w, h) = WallpaperCropper.decodeImageBounds(context, uri.toString())
            WallpaperEntity(
                uri = uri.toString(),
                folderUri = customFolderUri,
                displayName = "Photo ${System.currentTimeMillis() % 10000}",
                width = w,
                height = h,
                sizeBytes = 0,
                isIncludedInSlideshow = true
            )
        }

        wallpaperDao.insertWallpapers(list)
        val totalCount = wallpaperDao.getWallpaperCountForFolder(customFolderUri)
        folderDao.insertFolder(
            existing.copy(
                imageCount = totalCount,
                coverUri = list.firstOrNull()?.uri ?: existing.coverUri
            )
        )
        logOperation(
            action = "Pictures Added",
            status = "SUCCESS",
            details = "Added ${list.size} individual pictures to custom collection"
        )
        list.size
    }

    suspend fun toggleWallpaperInclusion(uri: String, isIncluded: Boolean) =
        withContext(Dispatchers.IO) {
            wallpaperDao.updateInclusion(uri, isIncluded)
        }

    suspend fun deleteFolder(folderUri: String) = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderByUri(folderUri)
        val name = folder?.name ?: "Folder"
        wallpaperDao.deleteWallpapersForFolder(folderUri)
        folderDao.deleteFolderByUri(folderUri)
        logOperation("Folder Deleted", "INFO", "Removed folder \"$name\" and its wallpapers from app")
    }

    /**
     * Executes changing to the next wallpaper (either triggered by schedule, alarm, unlock, or manual button)
     */
    suspend fun changeToNextWallpaper(): Boolean = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getDirectSettings()
        var candidates = wallpaperDao.getActiveSlideshowWallpapersList()

        // Filter by minImageDimension if set
        if (settings.minImageDimension > 0) {
            val minDim = settings.minImageDimension
            val filtered = candidates.filter { it.width >= minDim && it.height >= minDim }
            if (filtered.isNotEmpty()) {
                candidates = filtered
            }
        }

        if (candidates.isEmpty()) {
            logOperation(
                action = "Wallpaper Rotation",
                status = "FAILED",
                details = "No active wallpapers available in slideshow queue"
            )
            return@withContext false
        }

        val nextWallpaper = if (settings.shuffle) {
            val currentUri = settings.currentWallpaperUri
            val others = candidates.filter { it.uri != currentUri }
            if (others.isNotEmpty()) others.random() else candidates.random()
        } else {
            val currentUri = settings.currentWallpaperUri
            val currentIndex = candidates.indexOfFirst { it.uri == currentUri }
            if (currentIndex in 0 until candidates.size - 1) {
                candidates[currentIndex + 1]
            } else {
                candidates[0]
            }
        }

        val (screenWidth, screenHeight) = WallpaperCropper.getScreenDimensions(context)
        val result = WallpaperCropper.loadAndCropBitmap(
            context = context,
            uriString = nextWallpaper.uri,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            homeScreenCount = settings.homeScreenCount
        )

        if (result == null) {
            logOperation(
                action = "Wallpaper Rotation",
                status = "FAILED",
                details = "Could not decode or crop image bitmap for \"${nextWallpaper.displayName}\"",
                wallpaperName = nextWallpaper.displayName,
                wallpaperUri = nextWallpaper.uri
            )
            return@withContext false
        }

        val (bitmap, _) = result
        val applyResult = WallpaperCropper.applyAsWallpaper(
            context = context,
            croppedBitmap = bitmap,
            target = settings.slideshowTarget,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        bitmap.recycle()

        val target = settings.slideshowTarget
        val homeNeeded = target == "BOTH" || target == "HOME_ONLY"
        val lockNeeded = target == "BOTH" || target == "LOCK_ONLY"

        if (homeNeeded && !applyResult.homeApplied) {
            // Home screen failed (e.g. device is locked)! Save as pending home wallpaper for unlock
            settingsRepository.updateSettings {
                it.copy(
                    pendingHomeWallpaperUri = nextWallpaper.uri,
                    pendingHomeWallpaperName = nextWallpaper.displayName,
                    lastChangedTimestamp = System.currentTimeMillis()
                )
            }
            val details = if (lockNeeded && applyResult.lockApplied) {
                "Lock screen changed (ID=${applyResult.lockId}). Home screen deferred (device locked=${applyResult.isKeyguardLocked}, Home ID=${applyResult.homeId}); saved to apply on unlock."
            } else {
                "Home screen change deferred (device locked=${applyResult.isKeyguardLocked}, Home ID=${applyResult.homeId}); saved to apply on unlock."
            }
            logOperation(
                action = "Wallpaper Rotation",
                status = "PENDING",
                details = details,
                wallpaperName = nextWallpaper.displayName,
                wallpaperUri = nextWallpaper.uri
            )
            return@withContext applyResult.anyApplied
        } else {
            // Home succeeded (or was not requested)
            settingsRepository.updateSettings {
                it.copy(
                    pendingHomeWallpaperUri = null,
                    pendingHomeWallpaperName = null,
                    currentWallpaperUri = nextWallpaper.uri,
                    currentWallpaperName = nextWallpaper.displayName,
                    lastChangedTimestamp = System.currentTimeMillis()
                )
            }

            val details = when (target) {
                "HOME_ONLY" -> "Applied to Home screen (Home ID=${applyResult.homeId}, locked=${applyResult.isKeyguardLocked})"
                "LOCK_ONLY" -> "Applied to Lock screen (Lock ID=${applyResult.lockId})"
                else -> "Applied to both Home (ID=${applyResult.homeId}) & Lock screen (ID=${applyResult.lockId}, locked=${applyResult.isKeyguardLocked})"
            }

            logOperation(
                action = "Wallpaper Rotation",
                status = if (applyResult.isFullyApplied) "SUCCESS" else "WARNING",
                details = details,
                wallpaperName = nextWallpaper.displayName,
                wallpaperUri = nextWallpaper.uri
            )
            return@withContext applyResult.anyApplied
        }
    }

    /**
     * Applies the queued pending home wallpaper if device was locked during a previous change.
     */
    suspend fun applyPendingHomeWallpaperIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getDirectSettings()
        val pendingUri = settings.pendingHomeWallpaperUri ?: return@withContext false
        val pendingName = settings.pendingHomeWallpaperName ?: "Pending Wallpaper"

        val (screenWidth, screenHeight) = WallpaperCropper.getScreenDimensions(context)
        val result = WallpaperCropper.loadAndCropBitmap(
            context = context,
            uriString = pendingUri,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            homeScreenCount = settings.homeScreenCount
        ) ?: return@withContext false

        val (bitmap, _) = result
        val applyResult = WallpaperCropper.applyAsWallpaper(
            context = context,
            croppedBitmap = bitmap,
            target = "HOME_ONLY",
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        bitmap.recycle()

        if (applyResult.homeApplied) {
            settingsRepository.updateSettings {
                it.copy(
                    pendingHomeWallpaperUri = null,
                    pendingHomeWallpaperName = null,
                    currentWallpaperUri = pendingUri,
                    currentWallpaperName = pendingName,
                    lastChangedTimestamp = System.currentTimeMillis()
                )
            }
            logOperation(
                action = "Pending Home Applied",
                status = "SUCCESS",
                details = "Device unlocked: Applied pending home wallpaper \"$pendingName\" (Home ID=${applyResult.homeId})",
                wallpaperName = pendingName,
                wallpaperUri = pendingUri
            )
            true
        } else {
            logOperation(
                action = "Pending Home Applied",
                status = "FAILED",
                details = "Device unlocked: Failed to apply pending home wallpaper \"$pendingName\" (Home ID=${applyResult.homeId}, locked=${applyResult.isKeyguardLocked})",
                wallpaperName = pendingName,
                wallpaperUri = pendingUri
            )
            false
        }
    }
}
