package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodDiaryRulesTest {
    @Test
    fun `allows saving food with name and visual portion without calories`() {
        val draft = FoodEntryDraft(
            foodName = "tortilla con huevo",
            portionLabel = "Plato",
            mealType = "DESAYUNO",
            calories = null,
            proteinGrams = null,
            carbsGrams = null,
            fatGrams = null
        )

        val validation = FoodDiaryRules.validate(draft)

        assertTrue(validation.isValid)
        assertEquals(emptyList<String>(), validation.errors)
    }

    @Test
    fun `inventa calorias y macros cuando el usuario los omite basandose en la porcion`() {
        val draft = FoodEntryDraft(
            foodName = "arroz con frijoles",
            portionLabel = "Taza",
            mealType = "COMIDA",
            calories = null,
            proteinGrams = null,
            carbsGrams = null,
            fatGrams = null
        )

        val entry = FoodDiaryRules.toFoodEntry(draft)

        assertEquals(150, entry.calories)
        assertEquals(5f, entry.proteinGrams)
        assertEquals(20f, entry.carbsGrams)
        assertEquals(5f, entry.fatGrams)
    }

    @Test
    fun `rejects negative nutrition numbers`() {
        val draft = FoodEntryDraft(
            foodName = "atun",
            portionLabel = "1 lata",
            mealType = "CENA",
            calories = -10,
            proteinGrams = 20f,
            carbsGrams = 0f,
            fatGrams = 1f
        )

        val validation = FoodDiaryRules.validate(draft)

        assertFalse(validation.isValid)
        assertEquals(listOf("Las calorias y macros no pueden ser negativos."), validation.errors)
    }
}
