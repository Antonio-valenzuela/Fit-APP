package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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

        assertEquals(1024, max(prepared.width, prepared.height))
        assertEquals(1024, prepared.preview.width)
        assertEquals(512, prepared.preview.height)
        assertTrue(prepared.bytes.isNotEmpty())
        assertTrue(prepared.bytes.size < file.length())
    }

    @Test
    fun `Gemini consent copy names image upload and medical disclaimer`() {
        assertTrue(GeminiNutritionConsent.text.contains("enviar esta imagen/datos a Gemini"))
        assertTrue(GeminiNutritionConsent.text.contains("FitPlan no sustituye diagnóstico médico"))
    }
}
