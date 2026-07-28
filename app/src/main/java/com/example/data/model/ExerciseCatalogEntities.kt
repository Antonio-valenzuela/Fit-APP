package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ExerciseImportStatus { ACTIVE, MERGED, REVIEW, DISCARDED }

enum class ExerciseLocationCompatibility { HOME_COMPATIBLE, GYM_COMPATIBLE, BOTH }

enum class ExerciseBodyRegion {
    BACK, CARDIO, CHEST, CORE, FOREARMS, LOWER_LEGS, NECK,
    SHOULDERS, UPPER_ARMS, UPPER_LEGS
}

enum class ExerciseEquipmentType {
    AB_WHEEL, BAND, BAR, BARBELL, BENCH, BODY_WEIGHT, BOSU_BALL, CABLE,
    DUMBBELL, ELLIPTICAL, EZ_BARBELL, KETTLEBELL, MACHINE, MEDICINE_BALL,
    PULL_UP_BAR, ROLLER, ROPE, SKIERG, SLED, SMITH_MACHINE, STABILITY_BALL,
    STATIONARY_BIKE, STEPMILL, STEP_PLATFORM, TIRE, TRAP_BAR,
    UPPER_BODY_ERGOMETER, WEIGHTED
}

@Entity(
    tableName = "exercise_catalog",
    indices = [
        Index(value = ["catalogId"], unique = true),
        Index("normalizedName"),
        Index("primaryMuscle"),
        Index("bodyRegion"),
        Index("primaryEquipmentType"),
        Index("locationCompatibility")
    ]
)
data class ExerciseCatalogEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,
    val catalogId: String,
    val nameEs: String?,
    val nameEn: String?,
    val normalizedName: String,
    val instructionsEs: String?,
    val instructionsEn: String?,
    val primaryMuscle: Muscle,
    val bodyRegion: ExerciseBodyRegion,
    val primaryEquipmentType: ExerciseEquipmentType,
    val locationCompatibility: ExerciseLocationCompatibility
)

@Entity(
    tableName = "exercise_source_mappings",
    primaryKeys = ["source", "sourceOriginalId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseCatalogEntity::class,
            parentColumns = ["catalogId"],
            childColumns = ["catalogId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("catalogId"), Index("importStatus"), Index("source", "importVersion")]
)
data class ExerciseSourceMappingEntity(
    val source: String,
    val sourceOriginalId: String,
    val catalogId: String?,
    val importStatus: ExerciseImportStatus,
    val importVersion: String,
    val normalizationRulesVersion: String
)

@Entity(
    tableName = "exercise_secondary_muscles",
    primaryKeys = ["catalogId", "muscle"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseCatalogEntity::class,
            parentColumns = ["catalogId"],
            childColumns = ["catalogId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("muscle"), Index("catalogId")]
)
data class ExerciseSecondaryMuscleEntity(
    val catalogId: String,
    val muscle: Muscle
)

/**
 * Each row is an accumulative requirement: every listed equipment type is
 * required. Alternatives are intentionally not represented by this table.
 * BODY_WEIGHT is a primary equipment type, never a requirement row.
 */
@Entity(
    tableName = "exercise_equipment_requirements",
    primaryKeys = ["catalogId", "equipmentType"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseCatalogEntity::class,
            parentColumns = ["catalogId"],
            childColumns = ["catalogId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("equipmentType"), Index("catalogId")]
)
data class ExerciseEquipmentRequirementEntity(
    val catalogId: String,
    val equipmentType: ExerciseEquipmentType
)

@Entity(
    tableName = "exercise_search_content",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseCatalogEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["rowid"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExerciseSearchContentEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val nameEs: String?,
    val nameEn: String?,
    val normalizedName: String,
    val aliases: String
)

@Fts4(contentEntity = ExerciseSearchContentEntity::class)
@Entity(tableName = "exercise_catalog_fts")
data class ExerciseCatalogFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val nameEs: String?,
    val nameEn: String?,
    val normalizedName: String,
    val aliases: String
)

@Entity(tableName = "exercise_catalog_imports")
data class ExerciseCatalogImportEntity(
    @PrimaryKey
    val source: String,
    val importVersion: String,
    val compactSha256: String,
    val recordCount: Int,
    val normalizationRulesVersion: String,
    val appliedAtEpochMillis: Long
)

data class ExerciseImportManifest(
    val source: String,
    val importVersion: String,
    val compactSha256: String,
    val recordCount: Int,
    val normalizationRulesVersion: String
)

enum class ExerciseImportPlan { IMPORT, NO_OP, ABORT_VERSION_CHANGE;
    companion object {
        fun decide(existing: ExerciseImportManifest?, incoming: ExerciseImportManifest): ExerciseImportPlan {
            if (existing == null) return IMPORT
            return if (existing == incoming) NO_OP else ABORT_VERSION_CHANGE
        }
    }
}

object ExerciseCatalogRecordValidator {
    fun isValidNameAndInstructions(nameEs: String?, nameEn: String?, instructionsEs: String?, instructionsEn: String?): Boolean =
        listOf(nameEs, nameEn).any { !it.isNullOrBlank() } &&
            listOf(instructionsEs, instructionsEn).any { !it.isNullOrBlank() }

    fun requirementsFor(primaryEquipmentType: String, requirements: List<String>): List<String> =
        if (primaryEquipmentType == ExerciseEquipmentType.BODY_WEIGHT.name) emptyList() else requirements
}

object ExerciseCatalogAliasManifest {
    val default: Map<String, String> = linkedMapOf(
        "abdominales" to "crunch",
        "curl de bíceps" to "biceps_curl",
        "dominadas" to "pull_up",
        "elevaciones laterales" to "lateral_raise",
        "flexiones" to "push_up",
        "lagartijas" to "push_up",
        "peso muerto" to "romanian_deadlift",
        "plancha" to "plank",
        "press de banca" to "bench_press",
        "remo" to "barbell_row",
        "sentadilla" to "squat",
        "zancadas" to "lunges"
    )
}
