package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.WallpaperEntity
import com.example.ui.components.MultiScreenCropOverlay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperDetailScreen(
    wallpaper: WallpaperEntity,
    defaultHomeScreenCount: Int,
    isSettingWallpaper: Boolean,
    onClose: () -> Unit,
    onApplyWallpaper: (target: String, homeScreens: Int) -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSetSheet by remember { mutableStateOf(false) }
    var selectedTarget by remember { mutableStateOf("BOTH") } // BOTH, HOME_ONLY, LOCK_ONLY
    var homeScreens by remember { mutableIntStateOf(defaultHomeScreenCount) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen Wallpaper Image
        AsyncImage(
            model = if (wallpaper.uri.startsWith("drawable://")) {
                wallpaper.uri.removePrefix("drawable://").toIntOrNull()
            } else wallpaper.uri,
            contentDescription = wallpaper.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Top App Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(Color(0x66000000), shape = CircleShape)
                    .testTag("close_detail_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            Text(
                text = wallpaper.displayName,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier.background(Color(0x66000000), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color.White
                )
            }
        }

        // Bottom Action Bar
        Surface(
            color = Color(0xCC111319),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { showSetSheet = true },
                    modifier = Modifier.background(Color(0x33FFFFFF), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Preview Crop",
                        tint = Color.White
                    )
                }

                Button(
                    onClick = { showSetSheet = true },
                    enabled = !isSettingWallpaper,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                        .testTag("set_wallpaper_button")
                ) {
                    if (isSettingWallpaper) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Applying...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set as Wallpaper", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Info Dialog
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Image Information") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Name: ${wallpaper.displayName}")
                        Text("Resolution: ${wallpaper.width} × ${wallpaper.height}")
                        val aspect = if (wallpaper.height > 0) {
                            String.format("%.2f:1", wallpaper.width.toFloat() / wallpaper.height.toFloat())
                        } else "Unknown"
                        Text("Aspect Ratio: $aspect")
                        Text("Location: ${if (wallpaper.uri.startsWith("drawable://")) "Sample Asset" else "Storage File"}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Set As Wallpaper & Multi-Screen Crop Bottom Sheet
        if (showSetSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSetSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "Set as Wallpaper",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Customize home screen alignment & aspect ratio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Multi-Screen Adaptive Preview
                    MultiScreenCropOverlay(
                        imageUri = wallpaper.uri,
                        imageWidth = wallpaper.width,
                        imageHeight = wallpaper.height,
                        homeScreenCount = homeScreens
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Number of Home Screens Stepper
                    Text(
                        text = "Number of Home Screens: $homeScreens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..5).forEach { count ->
                            FilterChip(
                                selected = homeScreens == count,
                                onClick = { homeScreens = count },
                                label = { Text("$count ${if (count == 1) "Screen" else "Screens"}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Wallpaper Target Selection
                    Text(
                        text = "Apply To:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val targets = listOf(
                        "BOTH" to "Home and Lock Screen",
                        "HOME_ONLY" to "Home Screen Only",
                        "LOCK_ONLY" to "Lock Screen Only"
                    )

                    targets.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedTarget == key,
                                onClick = { selectedTarget = key }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Button
                    Button(
                        onClick = {
                            showSetSheet = false
                            onApplyWallpaper(selectedTarget, homeScreens)
                        },
                        enabled = !isSettingWallpaper,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_apply_wallpaper")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
