package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WallshowApp
import com.example.data.local.FolderEntity
import com.example.data.local.WallpaperEntity
import com.example.data.repository.UserSettings
import com.example.engine.WallpaperCropper
import com.example.service.WallpaperChangerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WallshowUiState(
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolder: FolderEntity? = null,
    val folderWallpapers: List<WallpaperEntity> = emptyList(),
    val activeWallpapersCount: Int = 0,
    val settings: UserSettings = UserSettings(),
    val viewingWallpaper: WallpaperEntity? = null,
    val isSettingWallpaper: Boolean = false,
    val userMessage: String? = null
)

class WallshowViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WallshowApp
    private val repo = app.wallpaperRepository
    private val settingsRepo = app.settingsRepository

    private val _selectedFolder = MutableStateFlow<FolderEntity?>(null)
    private val _folderWallpapers = MutableStateFlow<List<WallpaperEntity>>(emptyList())
    private val _viewingWallpaper = MutableStateFlow<WallpaperEntity?>(null)
    private val _isSettingWallpaper = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(WallshowUiState())
    val uiState: StateFlow<WallshowUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.allFolders.collectLatest { folders ->
                _uiState.update { it.copy(folders = folders) }
            }
        }
        viewModelScope.launch {
            repo.activeSlideshowWallpapers.collectLatest { active ->
                _uiState.update { it.copy(activeWallpapersCount = active.size) }
            }
        }
        viewModelScope.launch {
            settingsRepo.settings.collectLatest { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            _selectedFolder.collectLatest { selected ->
                _uiState.update { it.copy(selectedFolder = selected) }
            }
        }
        viewModelScope.launch {
            _folderWallpapers.collectLatest { wallpapers ->
                _uiState.update { it.copy(folderWallpapers = wallpapers) }
            }
        }
        viewModelScope.launch {
            _viewingWallpaper.collectLatest { viewing ->
                _uiState.update { it.copy(viewingWallpaper = viewing) }
            }
        }
        viewModelScope.launch {
            _isSettingWallpaper.collectLatest { isSetting ->
                _uiState.update { it.copy(isSettingWallpaper = isSetting) }
            }
        }
        viewModelScope.launch {
            _userMessage.collectLatest { message ->
                _uiState.update { it.copy(userMessage = message) }
            }
        }
    }

    fun toggleService() {
        val current = _uiState.value.settings.serviceRunning
        if (!current) {
            val activeCount = _uiState.value.activeWallpapersCount
            if (activeCount == 0) {
                _userMessage.value = "Please select at least 1 image for the slideshow first."
                return
            }
            WallpaperChangerService.startService(getApplication())
            settingsRepo.updateSettings { it.copy(serviceRunning = true) }
            _userMessage.value = "Automatic wallpaper changer started!"
        } else {
            WallpaperChangerService.stopService(getApplication())
            settingsRepo.updateSettings { it.copy(serviceRunning = false) }
            _userMessage.value = "Wallpaper changer stopped."
        }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        settingsRepo.updateSettings(transform)
    }

    fun openFolder(folder: FolderEntity) {
        _selectedFolder.value = folder
        viewModelScope.launch {
            repo.getWallpapersForFolder(folder.uri).collectLatest { list ->
                _folderWallpapers.value = list
            }
        }
    }

    fun closeFolder() {
        _selectedFolder.value = null
        _folderWallpapers.value = emptyList()
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val folderName = repo.addFolderTree(uri)
                _userMessage.value = "Added folder \"$folderName\" with images."
            } catch (e: Exception) {
                _userMessage.value = "Failed to add folder: ${e.localizedMessage}"
            }
        }
    }

    fun addPictures(uris: List<Uri>) {
        viewModelScope.launch {
            try {
                val count = repo.addIndividualImages(uris)
                _userMessage.value = "Added $count pictures to your collection."
            } catch (e: Exception) {
                _userMessage.value = "Failed to add pictures: ${e.localizedMessage}"
            }
        }
    }

    fun deleteFolder(folderUri: String) {
        viewModelScope.launch {
            repo.deleteFolder(folderUri)
            if (_selectedFolder.value?.uri == folderUri) {
                closeFolder()
            }
            _userMessage.value = "Folder removed."
        }
    }

    fun toggleWallpaperInclusion(wallpaper: WallpaperEntity) {
        viewModelScope.launch {
            repo.toggleWallpaperInclusion(wallpaper.uri, !wallpaper.isIncludedInSlideshow)
        }
    }

    fun selectAllInFolder(include: Boolean) {
        val currentFolder = _selectedFolder.value ?: return
        viewModelScope.launch {
            _folderWallpapers.value.forEach {
                repo.toggleWallpaperInclusion(it.uri, include)
            }
        }
    }

    fun openWallpaperDetail(wallpaper: WallpaperEntity) {
        _viewingWallpaper.value = wallpaper
    }

    fun closeWallpaperDetail() {
        _viewingWallpaper.value = null
    }

    fun setWallpaperManually(
        wallpaper: WallpaperEntity,
        target: String,
        homeScreens: Int
    ) {
        viewModelScope.launch {
            _isSettingWallpaper.value = true
            val (screenWidth, screenHeight) = WallpaperCropper.getScreenDimensions(getApplication())
            val cropAndBitmap = WallpaperCropper.loadAndCropBitmap(
                context = getApplication(),
                uriString = wallpaper.uri,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                homeScreenCount = homeScreens
            )

            if (cropAndBitmap != null) {
                val (bitmap, _) = cropAndBitmap
                val success = WallpaperCropper.applyAsWallpaper(
                    context = getApplication(),
                    croppedBitmap = bitmap,
                    target = target,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )
                bitmap.recycle()

                if (success) {
                    settingsRepo.updateSettings {
                        it.copy(
                            currentWallpaperUri = wallpaper.uri,
                            currentWallpaperName = wallpaper.displayName,
                            homeScreenCount = homeScreens
                        )
                    }
                    _userMessage.value = "Wallpaper updated successfully!"
                } else {
                    _userMessage.value = "Failed to set wallpaper."
                }
            } else {
                _userMessage.value = "Could not load image bitmap."
            }
            _isSettingWallpaper.value = false
        }
    }

    fun changeToNextWallpaperNow() {
        viewModelScope.launch {
            _isSettingWallpaper.value = true
            val success = repo.changeToNextWallpaper()
            _isSettingWallpaper.value = false
            if (success) {
                _userMessage.value = "Changed to next wallpaper!"
            } else {
                _userMessage.value = "No active wallpapers found to set."
            }
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }
}
