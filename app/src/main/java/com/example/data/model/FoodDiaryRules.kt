package com.example.data.model

data class FoodEntryDraft(
    val foodName: String,
    val portionLabel: String,
    val mealType: String,
    val calories: Int?,
    val proteinGrams: Float?,
    val carbsGrams: Float?,
    val fatGrams: Float?,
    val saturatedFatGrams: Float? = null,
    val fiberGrams: Float? = null,
    val sugarGrams: Float? = null,
    val sodiumMg: Float? = null,
    val source: String = "MANUAL",
    val barcode: String? = null,
    val notes: String = "",
    val dateKey: String = ""
)

data class FoodValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

object FoodDiaryRules {
    val mealTypes = listOf("DESAYUNO", "COMIDA", "CENA", "COLACION")
    val visualPortions = listOf("Plato", "Taza", "Vaso", "Pieza", "Porcion")
    val latamSuggestions = listOf(
        "tortilla", "huevo", "pollo", "atun", "arroz", "frijoles", "avena",
        "leche", "yogurt", "pan", "platano", "manzana", "refresco", "agua", "cafe"
    )

    data class MacroEstimate(val calories: Int, val protein: Float, val carbs: Float, val fat: Float)

    private fun estimateMacros(portion: String): MacroEstimate {
        return when (portion.lowercase()) {
            "plato" -> MacroEstimate(400, 20f, 40f, 15f)
            "taza" -> MacroEstimate(150, 5f, 20f, 5f)
            "vaso" -> MacroEstimate(100, 2f, 20f, 0f)
            "pieza" -> MacroEstimate(80, 1f, 15f, 0f)
            "porcion", "porción" -> MacroEstimate(200, 10f, 20f, 8f)
            else -> MacroEstimate(200, 10f, 20f, 8f)
        }
    }

    fun getFrequentFoods(entries: List<FoodEntryEntity>): List<FoodEntryEntity> {
        return entries
            .groupBy { it.barcode?.takeIf { b -> b.isNotBlank() } ?: it.foodName.lowercase().trim() }
            .map { entryGroup -> entryGroup.value.first() to entryGroup.value.size }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun validate(draft: FoodEntryDraft): FoodValidationResult {
        val errors = buildList {
            if (draft.foodName.trim().isBlank()) add("Ingresa el nombre del alimento o platillo.")
            if (draft.portionLabel.trim().isBlank()) add("Elige una porcion visual.")
            if (draft.mealType !in mealTypes) add("Elige desayuno, comida, cena o colacion.")
            if (listOf(
                    draft.calories?.toFloat(),
                    draft.proteinGrams,
                    draft.carbsGrams,
                    draft.fatGrams,
                    draft.saturatedFatGrams,
                    draft.fiberGrams,
                    draft.sugarGrams,
                    draft.sodiumMg
                ).any { it != null && it < 0f }
            ) {
                add("Las calorias y macros no pueden ser negativos.")
            }
        }
        return FoodValidationResult(errors.isEmpty(), errors)
    }

    fun toFoodEntry(draft: FoodEntryDraft, now: Long = System.currentTimeMillis()): FoodEntryEntity {
        val hasManualMacros = draft.calories != null && draft.calories > 0
        val estimate = if (!hasManualMacros) estimateMacros(draft.portionLabel) else null

        return FoodEntryEntity(
            dateKey = draft.dateKey,
            mealType = draft.mealType,
            foodName = draft.foodName.trim(),
            portionLabel = draft.portionLabel,
            calories = if (hasManualMacros) draft.calories ?: 0 else estimate?.calories ?: 0,
            proteinGrams = if (hasManualMacros) draft.proteinGrams ?: 0f else estimate?.protein ?: 0f,
            carbsGrams = if (hasManualMacros) draft.carbsGrams ?: 0f else estimate?.carbs ?: 0f,
            fatGrams = if (hasManualMacros) draft.fatGrams ?: 0f else estimate?.fat ?: 0f,
            saturatedFatGrams = draft.saturatedFatGrams,
            fiberGrams = draft.fiberGrams,
            sugarGrams = draft.sugarGrams,
            sodiumMg = draft.sodiumMg,
            source = draft.source,
            barcode = draft.barcode?.trim()?.takeIf { it.isNotBlank() },
            notes = draft.notes.trim(),
            createdAt = now,
            updatedAt = now
        )
    }

    fun toFoodItem(entry: FoodEntryEntity, favorite: Boolean): FoodItemEntity {
        return FoodItemEntity(
            name = entry.foodName,
            barcode = entry.barcode,
            caloriesPerServing = entry.calories,
            proteinGrams = entry.proteinGrams,
            carbsGrams = entry.carbsGrams,
            fatGrams = entry.fatGrams,
            saturatedFatGrams = entry.saturatedFatGrams,
            fiberGrams = entry.fiberGrams,
            sugarGrams = entry.sugarGrams,
            sodiumMg = entry.sodiumMg,
            servingLabel = entry.portionLabel,
            isFavorite = favorite,
            isCustom = entry.source != "BARCODE"
        )
    }
}
