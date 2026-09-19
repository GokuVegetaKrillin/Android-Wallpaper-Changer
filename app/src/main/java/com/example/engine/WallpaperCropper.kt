package com.example.engine

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScreenBox(
    val index: Int,
    val title: String,
    val colorArgb: Long, // 0xFFFF0000 (Red), 0xFF0000FF (Blue), 0xFF00FF00 (Green), etc.
    val normalizedRect: RectF, // 0..1 relative to the cropped wallpaper
    val originalPixelRect: Rect // coordinates relative to original image
)

data class CropResult(
    val cropRect: Rect,
    val screenBoxes: List<ScreenBox>,
    val overlapRatio: Float,
    val summary: String
)

object WallpaperCropper {

    private val SCREEN_COLORS = listOf(
        0xFFFF1744, // Red (Screen 1)
        0xFF2979FF, // Blue (Screen 2)
        0xFF00E676, // Green (Screen 3)
        0xFFFF9100, // Amber / Orange (Screen 4)
        0xFFE040FB, // Magenta / Purple (Screen 5)
        0xFF00E5FF  // Cyan (Screen 6)
    )

    fun getScreenDimensions(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars()
            )
            val width = metrics.bounds.width()
            val height = metrics.bounds.height()
            Pair(max(1, width), max(1, height))
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(displayMetrics)
            Pair(max(1, displayMetrics.widthPixels), max(1, displayMetrics.heightPixels))
        }
    }

    /**
     * Implements the exact algorithm specified by the user:
     * - Preserves aspect ratio with zero distortion.
     * - Displays as much of the image as possible.
     * - N=1: centers on image, trimming sides if wide, or top/bottom if tall.
     * - N>=2:
     *   - If image is wide enough for overlap (or exact match): spans left edge to right edge
     *     with overlapping intermediate screens.
     *   - If image is ultra-wide (even wider than N side-by-side disjoint screens):
     *     centers N non-overlapping screens, trimming excess left and right parts.
     */
    fun calculateCrop(
        imgWidth: Int,
        imgHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        homeScreenCount: Int
    ): CropResult {
        val n = max(1, homeScreenCount)
        val arScreen = screenWidth.toFloat() / max(1, screenHeight).toFloat()
        val arImg = imgWidth.toFloat() / max(1, imgHeight).toFloat()

        val cropRect: Rect
        val screenBoxes = mutableListOf<ScreenBox>()
        var overlapRatio = 0f
        val summary: String

        if (n == 1) {
            if (arImg >= arScreen) {
                // Image is wider than 1 screen: stretch top-to-bottom, center horizontally
                val targetW = (imgHeight * arScreen).roundToInt().coerceIn(1, imgWidth)
                val left = ((imgWidth - targetW) / 2).coerceAtLeast(0)
                cropRect = Rect(left, 0, left + targetW, imgHeight)
                summary = "1 Screen: Centered crop across full height, side margins trimmed"
            } else {
                // Image is taller than 1 screen: stretch left-to-right, center vertically
                val targetH = (imgWidth / arScreen).roundToInt().coerceIn(1, imgHeight)
                val top = ((imgHeight - targetH) / 2).coerceAtLeast(0)
                cropRect = Rect(0, top, imgWidth, top + targetH)
                summary = "1 Screen: Centered crop across full width, top/bottom margins trimmed"
            }

            screenBoxes.add(
                ScreenBox(
                    index = 0,
                    title = "Screen 1",
                    colorArgb = SCREEN_COLORS[0],
                    normalizedRect = RectF(0f, 0f, 1f, 1f),
                    originalPixelRect = Rect(cropRect)
                )
            )
        } else {
            val maxAr = n * arScreen

            when {
                // Subcase A: Image is extremely wide (wider than N disjoint screens, see 1789792991044.png)
                arImg > maxAr -> {
                    val targetTotalW = (n * imgHeight * arScreen).roundToInt().coerceIn(1, imgWidth)
                    val left = ((imgWidth - targetTotalW) / 2).coerceAtLeast(0)
                    cropRect = Rect(left, 0, left + targetTotalW, imgHeight)
                    val subScreenW = targetTotalW.toFloat() / n

                    for (i in 0 until n) {
                        val subLeft = left + (i * subScreenW).roundToInt()
                        val subRight = (left + ((i + 1) * subScreenW).roundToInt()).coerceAtMost(imgWidth)
                        val normLeft = i.toFloat() / n.toFloat()
                        val normRight = (i + 1).toFloat() / n.toFloat()

                        screenBoxes.add(
                            ScreenBox(
                                index = i,
                                title = "Screen ${i + 1}",
                                colorArgb = SCREEN_COLORS[i % SCREEN_COLORS.size],
                                normalizedRect = RectF(normLeft, 0f, normRight, 1f),
                                originalPixelRect = Rect(subLeft, 0, subRight, imgHeight)
                            )
                        )
                    }
                    overlapRatio = 0f
                    summary = "$n Screens (Ultra-wide): Centered span, 0% overlap, outer sides trimmed"
                }

                // Subcase B: Image is wider than 1 screen, but within N screens (Screens overlap, see 1789791876257.png)
                arImg >= arScreen -> {
                    // Full image height and width are used!
                    cropRect = Rect(0, 0, imgWidth, imgHeight)
                    val subScreenW = (imgHeight * arScreen).coerceIn(1f, imgWidth.toFloat())
                    val travelDistance = imgWidth.toFloat() - subScreenW
                    val step = if (n > 1) travelDistance / (n - 1) else 0f

                    for (i in 0 until n) {
                        val subLeftPx = (i * step).roundToInt()
                        val subRightPx = (subLeftPx + subScreenW).roundToInt().coerceAtMost(imgWidth)

                        val normLeft = subLeftPx / imgWidth.toFloat()
                        val normRight = subRightPx / imgWidth.toFloat()

                        screenBoxes.add(
                            ScreenBox(
                                index = i,
                                title = "Screen ${i + 1}",
                                colorArgb = SCREEN_COLORS[i % SCREEN_COLORS.size],
                                normalizedRect = RectF(normLeft, 0f, normRight, 1f),
                                originalPixelRect = Rect(subLeftPx, 0, subRightPx, imgHeight)
                            )
                        )
                    }

                    val overlapPx = max(0f, subScreenW - step)
                    overlapRatio = (overlapPx / subScreenW).coerceIn(0f, 1f)
                    val overlapPercent = (overlapRatio * 100).roundToInt()
                    summary = "$n Screens: Left to right span with ${overlapPercent}% overlap between screens"
                }

                // Subcase C: Image is taller than 1 screen
                else -> {
                    val targetH = (imgWidth / arScreen).roundToInt().coerceIn(1, imgHeight)
                    val top = ((imgHeight - targetH) / 2).coerceAtLeast(0)
                    cropRect = Rect(0, top, imgWidth, top + targetH)

                    for (i in 0 until n) {
                        screenBoxes.add(
                            ScreenBox(
                                index = i,
                                title = "Screen ${i + 1}",
                                colorArgb = SCREEN_COLORS[i % SCREEN_COLORS.size],
                                normalizedRect = RectF(0f, 0f, 1f, 1f),
                                originalPixelRect = Rect(cropRect)
                            )
                        )
                    }
                    overlapRatio = 1f
                    summary = "$n Screens (Portrait): Centered vertically across full width, screens fully overlap"
                }
            }
        }

        return CropResult(
            cropRect = cropRect,
            screenBoxes = screenBoxes,
            overlapRatio = overlapRatio,
            summary = summary
        )
    }

    suspend fun decodeImageBounds(context: Context, uriString: String): Pair<Int, Int> =
        withContext(Dispatchers.IO) {
            try {
                if (uriString.startsWith("drawable://")) {
                    val resId = uriString.removePrefix("drawable://").toIntOrNull()
                    if (resId != null) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeResource(context.resources, resId, options)
                        return@withContext Pair(max(1, options.outWidth), max(1, options.outHeight))
                    }
                }

                val uri = Uri.parse(uriString)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                Pair(max(1, options.outWidth), max(1, options.outHeight))
            } catch (e: Exception) {
                Pair(1080, 1920)
            }
        }

    suspend fun loadAndCropBitmap(
        context: Context,
        uriString: String,
        screenWidth: Int,
        screenHeight: Int,
        homeScreenCount: Int
    ): Pair<Bitmap, CropResult>? = withContext(Dispatchers.IO) {
        try {
            val (origW, origH) = decodeImageBounds(context, uriString)
            val cropResult = calculateCrop(origW, origH, screenWidth, screenHeight, homeScreenCount)

            // Decode original full or sub-sampled bitmap
            val sampleSize = calculateInSampleSize(origW, origH, 3840, 3840)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap: Bitmap? = if (uriString.startsWith("drawable://")) {
                val resId = uriString.removePrefix("drawable://").toIntOrNull()
                if (resId != null) {
                    BitmapFactory.decodeResource(context.resources, resId, options)
                } else null
            } else {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }

            if (rawBitmap == null) return@withContext null

            // Scale crop rect according to inSampleSize
            val scale = rawBitmap.width.toFloat() / origW.toFloat()
            val scaledLeft = (cropResult.cropRect.left * scale).roundToInt().coerceIn(0, rawBitmap.width - 1)
            val scaledTop = (cropResult.cropRect.top * scale).roundToInt().coerceIn(0, rawBitmap.height - 1)
            val scaledW = (cropResult.cropRect.width() * scale).roundToInt().coerceIn(1, rawBitmap.width - scaledLeft)
            val scaledH = (cropResult.cropRect.height() * scale).roundToInt().coerceIn(1, rawBitmap.height - scaledTop)

            val cropped = Bitmap.createBitmap(rawBitmap, scaledLeft, scaledTop, scaledW, scaledH)
            if (cropped != rawBitmap) {
                rawBitmap.recycle()
            }

            Pair(cropped, cropResult)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Applies the cropped bitmap to WallpaperManager.
     * target: "BOTH", "HOME_ONLY", "LOCK_ONLY"
     */
    suspend fun applyAsWallpaper(
        context: Context,
        croppedBitmap: Bitmap,
        target: String,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)

            // Suggest dimensions for smooth scrolling if setting home screen
            try {
                wallpaperManager.suggestDesiredDimensions(
                    max(screenWidth, croppedBitmap.width),
                    max(screenHeight, croppedBitmap.height)
                )
            } catch (e: Exception) {
                // Ignore failure on some devices/permissions
            }

            when (target) {
                "HOME_ONLY" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(
                            croppedBitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_SYSTEM
                        )
                    } else {
                        wallpaperManager.setBitmap(croppedBitmap)
                    }
                }
                "LOCK_ONLY" -> {
                    // For lock screen, crop central single screen from the wallpaper
                    val lockBitmap = extractCenterScreen(croppedBitmap, screenWidth, screenHeight)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(
                            lockBitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_LOCK
                        )
                    } else {
                        wallpaperManager.setBitmap(lockBitmap)
                    }
                    if (lockBitmap != croppedBitmap) {
                        lockBitmap.recycle()
                    }
                }
                "BOTH" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        // Home screen gets full multi-screen cropped bitmap
                        wallpaperManager.setBitmap(
                            croppedBitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_SYSTEM
                        )
                        // Lock screen gets centered single-screen portion
                        val lockBitmap = extractCenterScreen(croppedBitmap, screenWidth, screenHeight)
                        wallpaperManager.setBitmap(
                            lockBitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_LOCK
                        )
                        if (lockBitmap != croppedBitmap) {
                            lockBitmap.recycle()
                        }
                    } else {
                        wallpaperManager.setBitmap(croppedBitmap)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun extractCenterScreen(bitmap: Bitmap, screenWidth: Int, screenHeight: Int): Bitmap {
        val arScreen = screenWidth.toFloat() / max(1, screenHeight).toFloat()
        val targetW = (bitmap.height * arScreen).roundToInt().coerceIn(1, bitmap.width)
        val left = ((bitmap.width - targetW) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, left, 0, targetW, bitmap.height)
    }
}
