package com.example.data.model

import java.text.Normalizer
import java.util.Locale

enum class ExerciseCatalogLocationFilter {
    ANY,
    HOME,
    GYM
}

enum class ExerciseCatalogLanguage {
    SPANISH,
    ENGLISH
}

data class ExerciseCatalogFilter(
    val text: String? = null,
    val muscle: Muscle? = null,
    val bodyRegion: ExerciseBodyRegion? = null,
    val equipment: ExerciseEquipmentType? = null,
    val location: ExerciseCatalogLocationFilter = ExerciseCatalogLocationFilter.ANY,
    val availableEquipment: Set<ExerciseEquipmentType> = emptySet()
)

data class ExerciseCatalogItemView(
    val catalogId: String,
    val name: String,
    val instructions: String,
    val nameEs: String?,
    val nameEn: String?,
    val instructionsEs: String?,
    val instructionsEn: String?,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle>,
    val bodyRegion: ExerciseBodyRegion,
    val primaryEquipmentType: ExerciseEquipmentType,
    val equipmentRequirements: List<ExerciseEquipmentType>,
    val locationCompatibility: ExerciseLocationCompatibility,
    val hasSpanishTranslation: Boolean
)

fun ExerciseCatalogEntity.toItemView(
    secondaryMuscles: List<ExerciseSecondaryMuscleEntity> = emptyList(),
    equipmentRequirements: List<ExerciseEquipmentRequirementEntity> = emptyList(),
    language: ExerciseCatalogLanguage = ExerciseCatalogLanguage.SPANISH
): ExerciseCatalogItemView {
    val selectedName = when (language) {
        ExerciseCatalogLanguage.SPANISH -> nameEs ?: nameEn
        ExerciseCatalogLanguage.ENGLISH -> nameEn ?: nameEs
    } ?: "Ejercicio sin nombre"
    val selectedInstructions = when (language) {
        ExerciseCatalogLanguage.SPANISH -> instructionsEs ?: instructionsEn
        ExerciseCatalogLanguage.ENGLISH -> instructionsEn ?: instructionsEs
    } ?: "No hay instrucciones disponibles."
    return ExerciseCatalogItemView(
        catalogId = catalogId,
        name = selectedName,
        instructions = selectedInstructions,
        nameEs = nameEs,
        nameEn = nameEn,
        instructionsEs = instructionsEs,
        instructionsEn = instructionsEn,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = secondaryMuscles.map { it.muscle }.sortedBy { it.name },
        bodyRegion = bodyRegion,
        primaryEquipmentType = primaryEquipmentType,
        equipmentRequirements = equipmentRequirements.map { it.equipmentType }.sortedBy { it.name },
        locationCompatibility = locationCompatibility,
        hasSpanishTranslation = !nameEs.isNullOrBlank() && !instructionsEs.isNullOrBlank()
    )
}

internal fun normalizeExerciseSearchText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

internal fun sanitizeExerciseFtsQuery(value: String): String? =
    normalizeExerciseSearchText(value)
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { "$it*" }
        .ifBlank { null }
