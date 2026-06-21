package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionRulesTest {

    @Test
    fun testPortionEstimations() {
        val draftPlato = FoodEntryDraft("Comida", "Plato", "COMIDA", null, null, null, null)
        val entryPlato = FoodDiaryRules.toFoodEntry(draftPlato)
        assertEquals(400, entryPlato.calories)
        assertEquals(20f, entryPlato.proteinGrams, 0.01f)

        val draftTaza = FoodEntryDraft("Sopa", "Taza", "CENA", null, null, null, null)
        val entryTaza = FoodDiaryRules.toFoodEntry(draftTaza)
        assertEquals(150, entryTaza.calories)
        assertEquals(20f, entryTaza.carbsGrams, 0.01f)
        
        val draftManual = FoodEntryDraft("Pollo", "Plato", "COMIDA", 500, 30f, 10f, 5f)
        val entryManual = FoodDiaryRules.toFoodEntry(draftManual)
        assertEquals(500, entryManual.calories) // Should override default portion
        assertEquals(30f, entryManual.proteinGrams, 0.01f)
    }

    @Test
    fun testCalorieBalanceAndDeficit() {
        val entry1 = FoodEntryEntity(foodName = "A", portionLabel = "Plato", calories = 400, proteinGrams = 20f, carbsGrams = 40f, fatGrams = 15f, mealType = "COMIDA", dateKey = "key")
        val entry2 = FoodEntryEntity(foodName = "B", portionLabel = "Taza", calories = 150, proteinGrams = 5f, carbsGrams = 20f, fatGrams = 5f, mealType = "DESAYUNO", dateKey = "key")
        
        val workout = WorkoutLogEntity(routineName = "Gym", durationMinutes = 60, caloriesBurned = 300, exercisesText = "")
        
        val summary = CalorieBalanceRules.build(
            legacyMeals = emptyList(),
            entries = listOf(entry1, entry2),
            workouts = listOf(workout),
            sessions = emptyList(),
            healthLogs = emptyList()
        )

        assertEquals(550, summary.consumedCalories)
        assertEquals(300, summary.burnedCalories)
        assertEquals(250, summary.balanceCalories) // 550 - 300
        assertEquals(25f, summary.proteinGrams, 0.01f)
    }

    @Test
    fun testFrequentFoodsOrdering() {
        // Pollo registered today (1 time)
        val entry1 = FoodEntryEntity(foodName = "Pollo", portionLabel = "Plato", mealType = "COMIDA", dateKey = "2023-10-15", barcode = "123")
        
        // Avena registered yesterday and day before (3 times)
        val entry2 = FoodEntryEntity(foodName = "Avena", portionLabel = "Taza", mealType = "DESAYUNO", dateKey = "2023-10-14", barcode = "")
        val entry3 = FoodEntryEntity(foodName = "avena ", portionLabel = "Taza", mealType = "DESAYUNO", dateKey = "2023-10-13", barcode = "")
        val entry4 = FoodEntryEntity(foodName = "Avena", portionLabel = "Taza", mealType = "DESAYUNO", dateKey = "2023-10-13", barcode = "")
        
        // Manzana registered 2 days ago (2 times)
        val entry5 = FoodEntryEntity(foodName = "Manzana", portionLabel = "Pieza", mealType = "COLACION", dateKey = "2023-10-13", barcode = null)
        val entry6 = FoodEntryEntity(foodName = "Manzana", portionLabel = "Pieza", mealType = "COLACION", dateKey = "2023-10-12", barcode = null)

        val list = listOf(entry1, entry2, entry3, entry4, entry5, entry6)
        val frequent = FoodDiaryRules.getFrequentFoods(list)

        // Avena should be first (3 count), then Manzana (2 count), then Pollo (1 count)
        assertEquals(3, frequent.size)
        assertEquals("Avena", frequent[0].foodName.trim())
        assertEquals("Manzana", frequent[1].foodName.trim())
        assertEquals("Pollo", frequent[2].foodName.trim())
    }
}
