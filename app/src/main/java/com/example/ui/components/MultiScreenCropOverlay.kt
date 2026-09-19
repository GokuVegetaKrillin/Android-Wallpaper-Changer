package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.engine.WallpaperCropper
import kotlin.math.roundToInt

@Composable
fun MultiScreenCropOverlay(
    imageUri: String,
    imageWidth: Int,
    imageHeight: Int,
    homeScreenCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (screenW, screenH) = remember(context) {
        WallpaperCropper.getScreenDimensions(context)
    }

    val safeImgW = if (imageWidth <= 0) 1920 else imageWidth
    val safeImgH = if (imageHeight <= 0) 1080 else imageHeight

    val cropResult = remember(safeImgW, safeImgH, screenW, screenH, homeScreenCount) {
        WallpaperCropper.calculateCrop(safeImgW, safeImgH, screenW, screenH, homeScreenCount)
    }

    val imgAspect = safeImgW.toFloat() / safeImgH.toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Image & Crop Boxes Canvas
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imgAspect.coerceIn(0.5f, 2.5f))
            ) {
                val containerW = maxWidth.value
                val containerH = maxHeight.value

                // Background Image
                AsyncImage(
                    model = if (imageUri.startsWith("drawable://")) {
                        imageUri.removePrefix("drawable://").toIntOrNull()
                    } else imageUri,
                    contentDescription = "Wallpaper Preview",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlaid Canvas for Crop Masks and Colored Screen Boxes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. Draw darkened area outside the crop rectangle
                    val cropL = (cropResult.cropRect.left.toFloat() / safeImgW) * w
                    val cropR = (cropResult.cropRect.right.toFloat() / safeImgW) * w
                    val cropT = (cropResult.cropRect.top.toFloat() / safeImgH) * h
                    val cropB = (cropResult.cropRect.bottom.toFloat() / safeImgH) * h

                    val dimColor = Color(0x99000000)

                    // Left dim
                    if (cropL > 0) {
                        drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(cropL, h))
                    }
                    // Right dim
                    if (cropR < w) {
                        drawRect(dimColor, topLeft = Offset(cropR, 0f), size = Size(w - cropR, h))
                    }
                    // Top dim
                    if (cropT > 0) {
                        drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(w, cropT))
                    }
                    // Bottom dim
                    if (cropB < h) {
                        drawRect(dimColor, topLeft = Offset(0f, cropB), size = Size(w, h - cropB))
                    }

                    // 2. Draw each home screen box with its distinct color
                    cropResult.screenBoxes.forEach { box ->
                        val boxL = (box.originalPixelRect.left.toFloat() / safeImgW) * w
                        val boxT = (box.originalPixelRect.top.toFloat() / safeImgH) * h
                        val boxW = (box.originalPixelRect.width().toFloat() / safeImgW) * w
                        val boxH = (box.originalPixelRect.height().toFloat() / safeImgH) * h

                        val strokeColor = Color(box.colorArgb)

                        // Outline
                        drawRect(
                            color = strokeColor,
                            topLeft = Offset(boxL, boxT),
                            size = Size(boxW, boxH),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend of screen boxes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            cropResult.screenBoxes.take(4).forEach { box ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(box.colorArgb), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = box.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Explanation / Summary Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = cropResult.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Image: ${safeImgW}x${safeImgH} | Screen: ${screenW}x${screenH}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
