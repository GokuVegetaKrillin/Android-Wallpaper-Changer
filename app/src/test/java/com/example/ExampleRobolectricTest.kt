package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.WallpaperCropper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun read_string_from_context() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Wallshow", appName)
    }

    @Test
    fun verify_single_screen_crop_calculation() {
        // Wide image 1920x1080 on 1080x1920 portrait phone
        val result = WallpaperCropper.calculateCrop(
            imgWidth = 1920,
            imgHeight = 1080,
            screenWidth = 1080,
            screenHeight = 1920,
            homeScreenCount = 1
        )
        // Stretches top-to-bottom, cuts sides
        assertEquals(0, result.cropRect.top)
        assertEquals(1080, result.cropRect.bottom)
        assertTrue(result.cropRect.width() < 1920)
        assertEquals(1, result.screenBoxes.size)
    }

    @Test
    fun verify_ultra_wide_multi_screen_centers() {
        // 1920x1080 with 2 home screens (arImg 1.777 > 2 * 0.5625 = 1.125)
        val result = WallpaperCropper.calculateCrop(
            imgWidth = 1920,
            imgHeight = 1080,
            screenWidth = 1080,
            screenHeight = 1920,
            homeScreenCount = 2
        )
        assertEquals(2, result.screenBoxes.size)
        // Trims outer margins and centers on image
        assertTrue(result.screenBoxes[0].originalPixelRect.left > 0)
        assertTrue(result.screenBoxes[1].originalPixelRect.right < 1920)
    }

    @Test
    fun verify_multi_screen_crop_overlap_span() {
        // 1920x1080 with 3 or 4 home screens spans full width with overlap
        val result = WallpaperCropper.calculateCrop(
            imgWidth = 1920,
            imgHeight = 1080,
            screenWidth = 1080,
            screenHeight = 1920,
            homeScreenCount = 4
        )
        assertEquals(4, result.screenBoxes.size)
        assertEquals(0, result.screenBoxes[0].originalPixelRect.left)
        assertEquals(1920, result.screenBoxes.last().originalPixelRect.right)
        assertTrue(result.overlapRatio > 0f)
    }
}
