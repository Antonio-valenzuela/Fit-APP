package com.example.ui.nutrition

import com.example.data.api.FoodImageEstimate
import com.example.data.api.GeminiImageRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiFoodUiStateTest {
    private val request = GeminiImageRequest(
        imageBytes = byteArrayOf(1, 2, 3),
        originalBytes = 10,
        originalWidth = 100,
        originalHeight = 80,
        preparedWidth = 100,
        preparedHeight = 80,
        quality = 75
    )
    private val estimate = FoodImageEstimate(
        name = "Ensalada",
        calories = 250,
        proteinG = 8f,
        carbsG = 30f,
        fatG = 10f,
        portionEstimate = "1 plato",
        confidence = "high",
        notes = "Estimacion visual.",
        confidenceScore = 0.8f
    )

    @Test
    fun `prepared loading and review transitions retain the same image`() {
        val prepared = GeminiFoodStateReducer.prepared(GeminiFoodUiState(), request)
        val loading = GeminiFoodStateReducer.start(prepared)
        val review = GeminiFoodStateReducer.success(loading, estimate)

        assertEquals(GeminiFoodPhase.AWAITING_CONSENT, prepared.phase)
        assertSame(request, loading.request)
        assertEquals(GeminiFoodPhase.REVIEW, review.phase)
        assertSame(request, review.request)
        assertSame(estimate, review.estimate)
        assertFalse(review.saveRequested)
    }

    @Test
    fun `failure keeps request and captured description for retry`() {
        val initial = GeminiFoodUiState(description = "Ensalada con pollo")
        val loading = GeminiFoodStateReducer.start(GeminiFoodStateReducer.prepared(initial, request))
        val failed = GeminiFoodStateReducer.failure(loading, "Gemini esta ocupado")

        assertEquals(GeminiFoodPhase.ERROR, failed.phase)
        assertSame(request, failed.request)
        assertEquals("Ensalada con pollo", failed.description)
        assertEquals("Gemini esta ocupado", failed.message)
        assertTrue(failed.canRetry)
    }

    @Test
    fun `second start while loading is ignored`() {
        val loading = GeminiFoodStateReducer.start(GeminiFoodStateReducer.prepared(GeminiFoodUiState(), request))
        assertSame(loading, GeminiFoodStateReducer.start(loading))
    }

    @Test
    fun `cancel retains prepared request but removes result`() {
        val review = GeminiFoodStateReducer.success(
            GeminiFoodStateReducer.start(GeminiFoodStateReducer.prepared(GeminiFoodUiState(), request)),
            estimate
        )
        val cancelled = GeminiFoodStateReducer.cancel(review)

        assertEquals(GeminiFoodPhase.CANCELLED, cancelled.phase)
        assertSame(request, cancelled.request)
        assertNull(cancelled.estimate)
        assertTrue(cancelled.canRetry)
    }

    @Test
    fun `manual reset is the only transition that discards prepared data`() {
        val prepared = GeminiFoodStateReducer.prepared(GeminiFoodUiState(), request)
        val reset = GeminiFoodStateReducer.resetForManual(prepared)

        assertEquals(GeminiFoodPhase.IDLE, reset.phase)
        assertNull(reset.request)
        assertNull(reset.estimate)
        assertFalse(reset.canRetry)
    }
}
