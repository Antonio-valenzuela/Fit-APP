package com.example.data.model

data class BuiltInExerciseEquipmentProfile(
    val primaryEquipmentType: ExerciseEquipmentType,
    val anyOf: Set<ExerciseEquipmentType> = emptySet(),
    val allOf: Set<ExerciseEquipmentType> = emptySet(),
    val locationCompatibility: ExerciseLocationCompatibility = ExerciseLocationCompatibility.BOTH
)

/** Auditable equipment policy for the 27 immutable TrainingCatalog built-ins. */
object BuiltInExerciseEquipmentProfiles {
    /** Explicit impossible ID used to keep every Room `IN (:ids)` binding non-empty. */
    const val NO_EQUIPMENT_FILTER_MATCH_ID = "__NO_BUILT_IN_EQUIPMENT_FILTER_MATCH__"

    val byCatalogId: Map<String, BuiltInExerciseEquipmentProfile> = linkedMapOf(
        "squat" to any(ExerciseEquipmentType.BODY_WEIGHT, ExerciseEquipmentType.BARBELL),
        "bulgarian_squat" to all(ExerciseEquipmentType.DUMBBELL, ExerciseEquipmentType.BENCH),
        "lunges" to any(ExerciseEquipmentType.BODY_WEIGHT, ExerciseEquipmentType.DUMBBELL),
        "hip_thrust" to all(ExerciseEquipmentType.BARBELL, ExerciseEquipmentType.BENCH),
        "romanian_deadlift" to any(ExerciseEquipmentType.BARBELL, ExerciseEquipmentType.DUMBBELL),
        "leg_curl" to all(ExerciseEquipmentType.MACHINE),
        "leg_extension" to all(ExerciseEquipmentType.MACHINE),
        "calf_raise" to any(ExerciseEquipmentType.MACHINE, ExerciseEquipmentType.STEP_PLATFORM),
        "bench_press" to any(ExerciseEquipmentType.BARBELL, ExerciseEquipmentType.DUMBBELL),
        "incline_press" to all(ExerciseEquipmentType.BENCH),
        "flyes" to any(ExerciseEquipmentType.DUMBBELL, ExerciseEquipmentType.CABLE),
        "push_up" to any(ExerciseEquipmentType.BODY_WEIGHT),
        "barbell_row" to all(ExerciseEquipmentType.BARBELL),
        "dumbbell_row" to all(ExerciseEquipmentType.DUMBBELL),
        "lat_pulldown" to all(ExerciseEquipmentType.CABLE),
        "pull_up" to all(ExerciseEquipmentType.PULL_UP_BAR),
        "military_press" to any(ExerciseEquipmentType.BARBELL, ExerciseEquipmentType.DUMBBELL),
        "lateral_raise" to all(ExerciseEquipmentType.DUMBBELL),
        "face_pull" to all(ExerciseEquipmentType.CABLE),
        "biceps_curl" to any(ExerciseEquipmentType.BARBELL, ExerciseEquipmentType.DUMBBELL),
        "hammer_curl" to all(ExerciseEquipmentType.DUMBBELL),
        "triceps_extension" to any(ExerciseEquipmentType.CABLE, ExerciseEquipmentType.DUMBBELL),
        "french_press" to any(ExerciseEquipmentType.EZ_BARBELL, ExerciseEquipmentType.DUMBBELL),
        "plank" to any(ExerciseEquipmentType.BODY_WEIGHT),
        "crunch" to any(ExerciseEquipmentType.BODY_WEIGHT),
        "leg_raise" to any(ExerciseEquipmentType.BODY_WEIGHT),
        "russian_twist" to any(ExerciseEquipmentType.BODY_WEIGHT, ExerciseEquipmentType.WEIGHTED)
    )

    val catalogIds: Set<String> = byCatalogId.keys

    init {
        check(byCatalogId.size == EXPECTED_BUILT_IN_COUNT)
        check(NO_EQUIPMENT_FILTER_MATCH_ID !in byCatalogId)
        check(byCatalogId.values.all { it.locationCompatibility == ExerciseLocationCompatibility.BOTH })
        check(byCatalogId.values.all { it.anyOf.isEmpty() xor it.allOf.isEmpty() })
    }

    fun getValue(catalogId: String): BuiltInExerciseEquipmentProfile =
        requireNotNull(byCatalogId[catalogId]) { "Unknown built-in exercise ID: $catalogId" }

    fun requireExactCatalogIds(actualCatalogIds: Set<String>) {
        require(actualCatalogIds == catalogIds) {
            val missing = catalogIds - actualCatalogIds
            val unexpected = actualCatalogIds - catalogIds
            "Built-in equipment registry mismatch; missing=$missing unexpected=$unexpected"
        }
    }

    fun compatibleHomeCatalogIds(
        availableEquipment: Set<ExerciseEquipmentType>
    ): List<String> = byCatalogId.mapNotNull { (catalogId, profile) ->
        catalogId.takeIf { profile.isHomeCompatible(availableEquipment) }
    }

    /**
     * Built-ins matching the catalog's equipment chip. Alternatives are OR, while every member
     * of an accumulated requirement remains discoverable by that concrete equipment type.
     */
    fun catalogIdsMatchingEquipmentFilter(
        equipment: ExerciseEquipmentType
    ): List<String> = byCatalogId.mapNotNull { (catalogId, profile) ->
        catalogId.takeIf {
            equipment == profile.primaryEquipmentType ||
                equipment in profile.anyOf ||
                equipment in profile.allOf
        }
    }

    /** Room cannot safely bind an empty collection to every SQLite `IN` implementation. */
    fun nonEmptyCatalogIdsMatchingEquipmentFilter(
        equipment: ExerciseEquipmentType?
    ): List<String> = equipment
        ?.let(::catalogIdsMatchingEquipmentFilter)
        .orEmpty()
        .ifEmpty { listOf(NO_EQUIPMENT_FILTER_MATCH_ID) }

    fun isHomeCompatible(
        catalogId: String,
        availableEquipment: Set<ExerciseEquipmentType>
    ): Boolean = getValue(catalogId).isHomeCompatible(availableEquipment)

    fun roomRequirementsFor(catalogId: String): List<ExerciseEquipmentType> =
        getValue(catalogId).allOf
            .filterNot { it == ExerciseEquipmentType.BODY_WEIGHT }
            .sortedBy { it.name }

    private fun BuiltInExerciseEquipmentProfile.isHomeCompatible(
        availableEquipment: Set<ExerciseEquipmentType>
    ): Boolean {
        if (!availableEquipment.containsAll(allOf.filterNot { it == ExerciseEquipmentType.BODY_WEIGHT })) {
            return false
        }
        return anyOf.isEmpty() ||
            ExerciseEquipmentType.BODY_WEIGHT in anyOf ||
            anyOf.any { it in availableEquipment }
    }

    private fun any(
        primary: ExerciseEquipmentType,
        vararg alternatives: ExerciseEquipmentType
    ) = BuiltInExerciseEquipmentProfile(
        primaryEquipmentType = primary,
        anyOf = setOf(primary, *alternatives)
    )

    private fun all(
        primary: ExerciseEquipmentType,
        vararg accumulated: ExerciseEquipmentType
    ) = BuiltInExerciseEquipmentProfile(
        primaryEquipmentType = primary,
        allOf = setOf(primary, *accumulated)
    )

    private const val EXPECTED_BUILT_IN_COUNT = 27
}
