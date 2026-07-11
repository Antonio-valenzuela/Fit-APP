package com.example.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GeminiNutritionProtocolTest {
    private val validResponse = """
        {
          "requestId":"123e4567-e89b-42d3-a456-426614174000",
          "modelVersion":"gemini-3.5-flash",
          "nutrition":{
            "foodName":"Tacos de pollo",
            "calories":520.0,
            "protein":32.0,
            "carbs":54.0,
            "fat":20.0,
            "portionEstimate":"3 tacos medianos",
            "confidence":0.82,
            "notes":"Estimacion visual.",
            "ingredients":["tortilla","pollo","cebolla"]
          },
          "metrics":{
            "preprocessMs":5,
            "providerMs":1200,
            "parseMs":2,
            "totalMs":1210,
            "requestBytes":4096,
            "originalBytes":3000,
            "preparedBytes":2000,
            "originalWidth":2400,
            "originalHeight":1800,
            "preparedWidth":1280,
            "preparedHeight":960,
            "quality":75,
            "attempt":1,
            "cacheHit":false
          },
          "thoughtSignature":"opaque-protocol-metadata",
          "extraProtocol":{"thought":true,"text":"not nutrition"}
        }
    """.trimIndent()

    @Test
    fun `parses normalized structured result and ignores additional protocol fields`() {
        val result = GeminiNutritionProtocol.parseResponse(validResponse)

        assertEquals("Tacos de pollo", result.estimate.name)
        assertEquals(520, result.estimate.calories)
        assertEquals(32f, result.estimate.proteinG)
        assertEquals(0.82f, result.estimate.confidenceScore)
        assertEquals(listOf("tortilla", "pollo", "cebolla"), result.estimate.ingredients)
        assertEquals("gemini-3.5-flash", result.modelVersion)
        assertEquals(1200L, result.metrics.providerMs)
    }

    @Test
    fun `rejects a raw Gemini envelope with thoughtSignature and thinking blocks safely`() {
        val rawEnvelope = """
            {"candidates":[{"content":{"parts":[
              {"thought":true,"text":"private reasoning","thoughtSignature":"opaque"},
              {"text":"{\"foodName\":\"Apple\"}","thoughtSignature":"opaque-final"}
            ]}}],"modelVersion":"gemini-3.5-flash"}
        """.trimIndent()

        val error = expectProtocolError { GeminiNutritionProtocol.parseResponse(rawEnvelope) }

        assertEquals("MISSING_NUTRITION", error.code)
        assertFalse(error.message.orEmpty().contains("thoughtSignature"))
        assertFalse(error.userMessage.contains("{"))
    }

    @Test
    fun `rejects invalid JSON`() {
        val error = expectProtocolError { GeminiNutritionProtocol.parseResponse("{not-json") }
        assertEquals("INVALID_RESPONSE_JSON", error.code)
    }

    @Test
    fun `rejects absent required nutrition fields`() {
        val withoutFat = validResponse.replace("\"fat\":20.0,", "")
        val error = expectProtocolError { GeminiNutritionProtocol.parseResponse(withoutFat) }
        assertEquals("MISSING_FIELD", error.code)
        assertEquals("fat", error.field)
    }

    @Test
    fun `rejects negative calories and macronutrients`() {
        val fields = mapOf(
            "calories" to "520.0",
            "protein" to "32.0",
            "carbs" to "54.0",
            "fat" to "20.0"
        )
        fields.forEach { (field, original) ->
            val error = expectProtocolError {
                GeminiNutritionProtocol.parseResponse(validResponse.replace("\"$field\":$original", "\"$field\":-1.0"))
            }
            assertEquals("INVALID_NON_NEGATIVE_NUMBER", error.code)
            assertEquals(field, error.field)
        }
    }

    @Test
    fun `rejects confidence outside zero and one`() {
        for (value in listOf("-0.1", "1.1")) {
            val error = expectProtocolError {
                GeminiNutritionProtocol.parseResponse(validResponse.replace("\"confidence\":0.82", "\"confidence\":$value"))
            }
            assertEquals("INVALID_CONFIDENCE", error.code)
        }
    }

    @Test
    fun `rejects null ingredient lists and blank names`() {
        val nullList = expectProtocolError {
            GeminiNutritionProtocol.parseResponse(
                validResponse.replace("[\"tortilla\",\"pollo\",\"cebolla\"]", "null")
            )
        }
        assertEquals("INVALID_LIST", nullList.code)

        val blankName = expectProtocolError {
            GeminiNutritionProtocol.parseResponse(validResponse.replace("Tacos de pollo", "   "))
        }
        assertEquals("INVALID_TEXT", blankName.code)
        assertEquals("foodName", blankName.field)
    }

    @Test
    fun `build request includes metrics and encodes image once`() {
        val image = byteArrayOf(1, 2, 3, 4)
        val request = GeminiImageRequest(
            imageBytes = image,
            originalBytes = 8,
            originalWidth = 200,
            originalHeight = 100,
            preparedWidth = 100,
            preparedHeight = 50,
            quality = 75
        )

        val json = GeminiNutritionProtocol.buildRequestJson(request, "request-id")

        assertTrue(json.contains("\"imageBase64\":\"AQIDBA==\""))
        assertTrue(json.contains("\"preparedBytes\":4"))
        assertFalse(json.contains(image.toString()))
    }

    private fun expectProtocolError(block: () -> Unit): GeminiProtocolException {
        try {
            block()
            fail("Expected GeminiProtocolException")
        } catch (error: GeminiProtocolException) {
            return error
        }
        throw AssertionError("Unreachable")
    }
}
