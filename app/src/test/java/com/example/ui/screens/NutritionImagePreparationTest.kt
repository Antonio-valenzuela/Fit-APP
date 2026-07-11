package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NutritionImagePreparationTest {

    @Test
    fun `prepare food image resizes large camera photo before Gemini`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "large-food-photo.jpg")
        val bitmap = Bitmap.createBitmap(1800, 900, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(210, 120, 70))
        }
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
        }

        val prepared = prepareFoodImage(context, Uri.fromFile(file))

        assertEquals(1280, max(prepared.width, prepared.height))
        assertEquals(1280, prepared.preview.width)
        assertEquals(640, prepared.preview.height)
        assertTrue(prepared.bytes.isNotEmpty())
        assertTrue(prepared.bytes.size < file.length())
        assertEquals(1800, prepared.originalWidth)
        assertEquals(900, prepared.originalHeight)
        assertEquals(file.length().toInt(), prepared.originalBytes)
        assertEquals(75, prepared.quality)
        assertEquals("image/jpeg", prepared.mimeType)
    }

    @Test
    fun `preparation profiles preserve aspect ratio and requested quality`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "profile-food-photo.jpg")
        val bitmap = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(90, 160, 60))
        }
        FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output) }

        val profiles = listOf(
            FoodImageProfile("conservative", 1600, 85),
            FoodImageProfile("balanced", 1280, 75),
            FoodImageProfile("aggressive", 960, 65)
        )

        profiles.forEach { profile ->
            val prepared = prepareFoodImage(context, Uri.fromFile(file), profile)
            assertEquals(profile.maxSidePx, prepared.width)
            assertEquals(profile.maxSidePx / 2, prepared.height)
            assertEquals(profile.jpegQuality, prepared.quality)
            assertEquals(profile.id, prepared.profileId)
        }
    }

    @Test
    fun `EXIF rotation swaps dimensions before resize`() {
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)

        val rotated = applyExifOrientation(bitmap, ExifInterface.ORIENTATION_ROTATE_90)

        assertEquals(60, rotated.width)
        assertEquals(120, rotated.height)
    }

    @Test
    fun `rejects prepared payload over configured ceiling`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "oversize-food-photo.jpg")
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    setPixel(x, y, Color.rgb((x * 17) % 255, (y * 31) % 255, ((x + y) * 13) % 255))
                }
            }
        }
        FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output) }

        try {
            prepareFoodImage(
                context,
                Uri.fromFile(file),
                FoodImageProfile("tiny-ceiling", 400, 90, maxOutputBytes = 32)
            )
            throw AssertionError("Expected payload rejection")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("demasiado grande"))
        }
    }

    @Test
    fun `Gemini consent copy names image upload and medical disclaimer`() {
        assertTrue(GeminiNutritionConsent.text.contains("enviar esta imagen/datos a Gemini"))
        assertTrue(GeminiNutritionConsent.text.contains("FitPlan no sustituye diagnóstico médico"))
    }
}
