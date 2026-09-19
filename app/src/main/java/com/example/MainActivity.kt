package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.WallshowViewModel
import com.example.ui.screens.FolderPlaylistScreen
import com.example.ui.screens.MainDashboardScreen
import com.example.ui.screens.WallpaperDetailScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WallshowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Request Notification Permission on Android 13+
                RequestNotificationPermission()

                val state by viewModel.uiState.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BackHandler(enabled = state.viewingWallpaper != null || state.selectedFolder != null) {
                        if (state.viewingWallpaper != null) {
                            viewModel.closeWallpaperDetail()
                        } else if (state.selectedFolder != null) {
                            viewModel.closeFolder()
                        }
                    }

                    AnimatedContent(
                        targetState = Triple(
                            state.viewingWallpaper != null,
                            state.selectedFolder != null,
                            state.viewingWallpaper?.uri ?: state.selectedFolder?.uri ?: "main"
                        ),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen_transition"
                    ) { (isViewingDetail, hasSelectedFolder, _) ->
                        when {
                            isViewingDetail && state.viewingWallpaper != null -> {
                                WallpaperDetailScreen(
                                    wallpaper = state.viewingWallpaper!!,
                                    defaultHomeScreenCount = state.settings.homeScreenCount,
                                    isSettingWallpaper = state.isSettingWallpaper,
                                    onClose = { viewModel.closeWallpaperDetail() },
                                    onApplyWallpaper = { target, homeScreens ->
                                        viewModel.setWallpaperManually(
                                            wallpaper = state.viewingWallpaper!!,
                                            target = target,
                                            homeScreens = homeScreens
                                        )
                                    }
                                )
                            }
                            hasSelectedFolder && state.selectedFolder != null -> {
                                FolderPlaylistScreen(
                                    folder = state.selectedFolder!!,
                                    wallpapers = state.folderWallpapers,
                                    isRescanning = state.isRescanning,
                                    onBack = { viewModel.closeFolder() },
                                    onSelectWallpaper = { viewModel.openWallpaperDetail(it) },
                                    onToggleInclusion = { viewModel.toggleWallpaperInclusion(it) },
                                    onSelectAll = { viewModel.selectAllInFolder(it) },
                                    onRescanFolder = { viewModel.rescanCurrentFolder() },
                                    onDeleteFolder = { viewModel.deleteFolder(state.selectedFolder!!.uri) }
                                )
                            }
                            else -> {
                                MainDashboardScreen(
                                    state = state,
                                    onToggleService = { viewModel.toggleService() },
                                    onUpdateSettings = { viewModel.updateSettings(it) },
                                    onOpenFolder = { viewModel.openFolder(it) },
                                    onAddFolder = { viewModel.addFolder(it) },
                                    onAddPictures = { viewModel.addPictures(it) },
                                    onChangeWallpaperNow = { viewModel.changeToNextWallpaperNow() },
                                    onOpenLogs = { viewModel.setShowLogsDialog(true) },
                                    onCloseLogs = { viewModel.setShowLogsDialog(false) },
                                    onClearLogs = { viewModel.clearLogs() },
                                    onRescanAll = { viewModel.rescanAllFolders() },
                                    onClearMessage = { viewModel.clearMessage() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* granted or denied */ }

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
