package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionPhotoPreparationInstrumentedTest {
    @Test
    fun readableFileProviderPhotoSurvivesAndroidBoundsDecode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "food_photos").apply { mkdirs() }
        val file = File(directory, "instrumented-food-photo.jpg")
        val source = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(210, 120, 70))
        }

        try {
            FileOutputStream(file).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, output))
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val prepared = prepareFoodImage(context, uri)

            assertEquals(320, prepared.width)
            assertEquals(200, prepared.height)
            assertTrue(prepared.bytes.isNotEmpty())
            prepared.preview.recycle()
        } finally {
            source.recycle()
            file.delete()
        }
    }
}
