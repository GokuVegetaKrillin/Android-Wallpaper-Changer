package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.FolderEntity
import com.example.data.local.WallpaperEntity
import com.example.engine.WallpaperCropper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WallpaperRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val folderDao = database.folderDao()
    private val wallpaperDao = database.wallpaperDao()

    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val allWallpapers: Flow<List<WallpaperEntity>> = wallpaperDao.getAllWallpapers()
    val activeSlideshowWallpapers: Flow<List<WallpaperEntity>> = wallpaperDao.getActiveSlideshowWallpapers()

    fun getWallpapersForFolder(folderUri: String): Flow<List<WallpaperEntity>> =
        wallpaperDao.getWallpapersForFolder(folderUri)

    suspend fun initializeDefaultWallpapersIfEmpty() = withContext(Dispatchers.IO) {
        val existingFolders = folderDao.getAllFolders()
        val count = wallpaperDao.getAllWallpapers()
        // Check if DB is already seeded
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
    }

    suspend fun addFolderTree(treeUri: Uri): String = withContext(Dispatchers.IO) {
        // Take persistable permission
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

        return@withContext folderName
    }

    private fun scanDocumentFiles(dir: DocumentFile?, result: MutableList<DocumentFile>) {
        if (dir == null || !dir.isDirectory) return
        val files = dir.listFiles()
        for (f in files) {
            if (f.isDirectory) {
                // optionally scan 1 level deeper
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
        list.size
    }

    suspend fun toggleWallpaperInclusion(uri: String, isIncluded: Boolean) =
        withContext(Dispatchers.IO) {
            wallpaperDao.updateInclusion(uri, isIncluded)
        }

    suspend fun deleteFolder(folderUri: String) = withContext(Dispatchers.IO) {
        wallpaperDao.deleteWallpapersForFolder(folderUri)
        folderDao.deleteFolderByUri(folderUri)
    }

    /**
     * Executes changing to the next wallpaper (either triggered by schedule or manual button)
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

        if (candidates.isEmpty()) return@withContext false

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

        // Apply it using the multi-screen cropping engine!
        val (screenWidth, screenHeight) = WallpaperCropper.getScreenDimensions(context)
        val result = WallpaperCropper.loadAndCropBitmap(
            context = context,
            uriString = nextWallpaper.uri,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            homeScreenCount = settings.homeScreenCount
        )

        if (result != null) {
            val (bitmap, _) = result
            val success = WallpaperCropper.applyAsWallpaper(
                context = context,
                croppedBitmap = bitmap,
                target = settings.slideshowTarget,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
            bitmap.recycle()

            if (success) {
                settingsRepository.updateSettings {
                    it.copy(
                        lastChangedTimestamp = System.currentTimeMillis(),
                        currentWallpaperUri = nextWallpaper.uri,
                        currentWallpaperName = nextWallpaper.displayName
                    )
                }
                return@withContext true
            }
        }
        false
    }
}
