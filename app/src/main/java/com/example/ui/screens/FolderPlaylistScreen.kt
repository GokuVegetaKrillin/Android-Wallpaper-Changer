package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.FolderEntity
import com.example.data.local.WallpaperEntity

enum class SortMode {
    DATE_ADDED,
    NAME_ASC,
    RESOLUTION_DESC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPlaylistScreen(
    folder: FolderEntity,
    wallpapers: List<WallpaperEntity>,
    onBack: () -> Unit,
    onSelectWallpaper: (WallpaperEntity) -> Unit,
    onToggleInclusion: (WallpaperEntity) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDeleteFolder: () -> Unit
) {
    var sortMode by remember { mutableStateOf(SortMode.DATE_ADDED) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val sortedWallpapers = remember(wallpapers, sortMode) {
        when (sortMode) {
            SortMode.DATE_ADDED -> wallpapers.sortedByDescending { it.dateAdded }
            SortMode.NAME_ASC -> wallpapers.sortedBy { it.displayName.lowercase() }
            SortMode.RESOLUTION_DESC -> wallpapers.sortedByDescending { it.width * it.height }
        }
    }

    val includedCount = wallpapers.count { it.isIncludedInSlideshow }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Wallpaper Playlist",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${folder.name} • $includedCount of ${wallpapers.size} active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_main")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Sort Icon
                    IconButton(onClick = {
                        sortMode = when (sortMode) {
                            SortMode.DATE_ADDED -> SortMode.NAME_ASC
                            SortMode.NAME_ASC -> SortMode.RESOLUTION_DESC
                            SortMode.RESOLUTION_DESC -> SortMode.DATE_ADDED
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort"
                        )
                    }

                    // More Menu
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu"
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Include All in Slideshow") },
                            onClick = {
                                onSelectAll(true)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exclude All from Slideshow") },
                            onClick = {
                                onSelectAll(false)
                                showMenu = false
                            }
                        )
                        if (!folder.uri.startsWith("wallshow://sample_pack")) {
                            DropdownMenuItem(
                                text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (wallpapers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No images in this folder",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(sortedWallpapers, key = { it.uri }) { wallpaper ->
                    WallpaperGridItem(
                        wallpaper = wallpaper,
                        onClick = { onSelectWallpaper(wallpaper) },
                        onToggleInclusion = { onToggleInclusion(wallpaper) }
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Remove Folder") },
                text = { Text("Are you sure you want to remove \"${folder.name}\" from Wallshow? (Your original files will not be deleted).") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            onDeleteFolder()
                        }
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun WallpaperGridItem(
    wallpaper: WallpaperEntity,
    onClick: () -> Unit,
    onToggleInclusion: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .testTag("wallpaper_item_${wallpaper.displayName}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image
            AsyncImage(
                model = if (wallpaper.uri.startsWith("drawable://")) {
                    wallpaper.uri.removePrefix("drawable://").toIntOrNull()
                } else wallpaper.uri,
                contentDescription = wallpaper.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Resolution Tag at Bottom-Left (Screenshot 3: "1168x784", etc.)
            val dimLabel = if (wallpaper.width > 0 && wallpaper.height > 0) {
                "${wallpaper.width}x${wallpaper.height}"
            } else ""

            if (dimLabel.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color(0x99000000), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = dimLabel,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Inclusion Checkmark Badge at Bottom-Right (Screenshot 3: green checkmark circle)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (wallpaper.isIncludedInSlideshow) Color(0xFF00C853)
                        else Color(0x88000000)
                    )
                    .clickable(onClick = onToggleInclusion),
                contentAlignment = Alignment.Center
            ) {
                if (wallpaper.isIncludedInSlideshow) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Included in slideshow",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Circle,
                        contentDescription = "Excluded from slideshow",
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
