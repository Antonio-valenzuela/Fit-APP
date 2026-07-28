package com.example.data.local

import android.content.Context
import android.content.res.AssetManager
import androidx.room.withTransaction
import com.example.data.model.BuiltInExerciseEquipmentProfiles
import com.example.data.model.ExerciseBodyRegion
import com.example.data.model.ExerciseCatalogAliasManifest
import com.example.data.model.ExerciseCatalogEntity
import com.example.data.model.ExerciseCatalogImportEntity
import com.example.data.model.ExerciseCatalogRecordValidator
import com.example.data.model.ExerciseEquipmentRequirementEntity
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.ExerciseImportManifest
import com.example.data.model.ExerciseImportPlan
import com.example.data.model.ExerciseImportStatus
import com.example.data.model.ExerciseLocationCompatibility
import com.example.data.model.ExerciseSearchContentEntity
import com.example.data.model.ExerciseSecondaryMuscleEntity
import com.example.data.model.ExerciseSourceMappingEntity
import com.example.data.model.Muscle
import com.example.ui.workout.TrainingCatalog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

@JsonClass(generateAdapter = true)
internal data class CatalogAssetRecord(
    @Json(name = "external_id") val externalId: String,
    @Json(name = "name_es") val nameEs: String?,
    @Json(name = "name_en") val nameEn: String?,
    @Json(name = "instructions_es") val instructionsEs: String?,
    @Json(name = "instructions_en") val instructionsEn: String?,
    @Json(name = "primary_muscle") val primaryMuscle: String,
    @Json(name = "secondary_muscles") val secondaryMuscles: List<String>,
    @Json(name = "body_region") val bodyRegion: String,
    @Json(name = "equipment") val equipment: String,
    @Json(name = "equipment_requirements") val equipmentRequirements: List<String>,
    @Json(name = "location_compatibility") val locationCompatibility: String,
    @Json(name = "import_status") val importStatus: String,
    @Json(name = "proposed_canonical_reference") val proposedCanonicalReference: String?
)

@JsonClass(generateAdapter = true)
internal data class CatalogAssetManifest(
    val source: String,
    val importVersion: String,
    val compactSha256: String,
    val recordCount: Int,
    val normalizationRulesVersion: String
)

/** Idempotent bootstrap invoked by the catalog ViewModel before Room-backed reads. */
class ExerciseCatalogBootstrapper(
    private val database: AppDatabase
) {
    private val importMutex = Mutex()

    suspend fun importIfNeeded(context: Context): ExerciseImportPlan = importMutex.withLock {
        importIfNeededLocked(context)
    }

    private suspend fun importIfNeededLocked(context: Context): ExerciseImportPlan {
        BuiltInExerciseEquipmentProfiles.requireExactCatalogIds(
            TrainingCatalog.exercises.map { it.id }.toSet()
        )
        val assets = context.applicationContext.assets
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val manifest = assets.open("exercise_catalog/catalog_manifest.json").use { input ->
            moshi.adapter(CatalogAssetManifest::class.java).fromJson(input.bufferedReader().readText())
                ?: error("Empty exercise catalog manifest")
        }
        val incoming = ExerciseImportManifest(
            source = manifest.source,
            importVersion = manifest.importVersion,
            compactSha256 = manifest.compactSha256,
            recordCount = manifest.recordCount,
            normalizationRulesVersion = manifest.normalizationRulesVersion
        )
        val existing = database.exerciseCatalogDao().getImportManifest(incoming.source)?.toManifest()
        when (ExerciseImportPlan.decide(existing, incoming)) {
            ExerciseImportPlan.NO_OP -> {
                database.withTransaction {
                    val dao = database.exerciseCatalogDao()
                    check(dao.countMappings(incoming.source, incoming.importVersion) == incoming.recordCount) {
                        "Import manifest matches but source mapping count is inconsistent"
                    }
                    reconcileBuiltIns(dao)
                }
                return ExerciseImportPlan.NO_OP
            }
            ExerciseImportPlan.ABORT_VERSION_CHANGE ->
                error("Exercise catalog version/checksum/count differs; incremental reconciliation is not implemented")
            ExerciseImportPlan.IMPORT -> Unit
        }

        val compactBytes = readCompactAsset(assets)
        check(sha256(compactBytes) == incoming.compactSha256) { "Exercise catalog checksum differs from manifest" }
        val records = compactBytes.let { bytes ->
            val type = Types.newParameterizedType(List::class.java, CatalogAssetRecord::class.java)
            moshi.adapter<List<CatalogAssetRecord>>(type).fromJson(bytes.toString(Charsets.UTF_8))
                ?: error("Empty exercise catalog data")
        }
        check(records.size == incoming.recordCount) { "Catalog record count differs from manifest" }
        val aliases = assets.open("exercise_catalog/spanish_aliases.json").use { input ->
            val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            moshi.adapter<Map<String, String>>(type).fromJson(input.bufferedReader().readText()) ?: emptyMap()
        }

        database.withTransaction {
            val dao = database.exerciseCatalogDao()
            val rowIds = linkedMapOf<String, Long>()
            val canonicalRows = records.filter { it.importStatus == ExerciseImportStatus.ACTIVE.name }
                .map { it.toEntity() }
            canonicalRows.forEach { row ->
                val rowId = dao.insertCatalog(row)
                rowIds[row.catalogId] = rowId
            }
            TrainingCatalog.exercises.forEach { item ->
                val row = item.toCanonicalEntity()
                val rowId = dao.insertCatalog(row)
                rowIds[row.catalogId] = rowId
            }
            check(aliases.values.all { it in rowIds }) { "Alias points to a catalog ID that is not active" }

            canonicalRows.forEach { row ->
                val source = records.first { it.externalId == row.catalogId }
                dao.insertSecondaryMuscles(source.secondaryMuscles.map { muscle ->
                    ExerciseSecondaryMuscleEntity(row.catalogId, Muscle.valueOf(muscle))
                })
                dao.insertEquipmentRequirements(
                    ExerciseCatalogRecordValidator.requirementsFor(row.primaryEquipmentType.name, source.equipmentRequirements)
                        .filter { it != ExerciseEquipmentType.BODY_WEIGHT.name }
                        .map { ExerciseEquipmentRequirementEntity(row.catalogId, ExerciseEquipmentType.valueOf(it)) }
                )
            }
            TrainingCatalog.exercises.forEach { item ->
                dao.insertSecondaryMuscles(item.secondaryMuscles.mapNotNull { itemMuscle ->
                    Muscle.fromLegacyName(itemMuscle)?.let { ExerciseSecondaryMuscleEntity(item.id, it) }
                })
                val requirements = BuiltInExerciseEquipmentProfiles.roomRequirementsFor(item.id)
                    .map { ExerciseEquipmentRequirementEntity(item.id, it) }
                if (requirements.isNotEmpty()) dao.insertEquipmentRequirements(requirements)
            }
            rowIds.forEach { (catalogId, rowId) ->
                val aliasTerms = (aliases.entries
                    .filter { it.value == catalogId }
                    .map { it.key }
                    .ifEmpty {
                        ExerciseCatalogAliasManifest.default.entries
                            .filter { it.value == catalogId }
                            .map { it.key }
                    })
                val alias = (aliasTerms + aliasTerms.map(::normalize))
                    .distinct()
                    .joinToString(" ")
                val row = if (catalogId.startsWith("exercises_dataset:")) records.first { it.externalId == catalogId }
                else null
                dao.insertSearchContent(
                    ExerciseSearchContentEntity(
                        rowId = rowId,
                        nameEs = row?.nameEs ?: TrainingCatalog.exerciseById(catalogId)?.name,
                        nameEn = row?.nameEn,
                        normalizedName = normalize(row?.nameEs ?: row?.nameEn ?: TrainingCatalog.exerciseById(catalogId)?.name.orEmpty()),
                        aliases = alias.orEmpty()
                    )
                )
            }
            dao.insertSourceMappings(records.map { record ->
                ExerciseSourceMappingEntity(
                    source = incoming.source,
                    sourceOriginalId = record.externalId.substringAfter(':').ifBlank { record.externalId },
                    catalogId = if (record.importStatus == ExerciseImportStatus.ACTIVE.name) record.externalId
                    else record.proposedCanonicalReference?.takeIf { record.importStatus == ExerciseImportStatus.MERGED.name },
                    importStatus = ExerciseImportStatus.valueOf(record.importStatus),
                    importVersion = incoming.importVersion,
                    normalizationRulesVersion = incoming.normalizationRulesVersion
                )
            } + TrainingCatalog.exercises.map { item ->
                ExerciseSourceMappingEntity(
                    source = "FITPLAN_BUILT_IN",
                    sourceOriginalId = item.id,
                    catalogId = item.id,
                    importStatus = ExerciseImportStatus.ACTIVE,
                    importVersion = incoming.importVersion,
                    normalizationRulesVersion = incoming.normalizationRulesVersion
                )
            })
            dao.insertImportManifest(
                ExerciseCatalogImportEntity(
                    source = incoming.source,
                    importVersion = incoming.importVersion,
                    compactSha256 = incoming.compactSha256,
                    recordCount = incoming.recordCount,
                    normalizationRulesVersion = incoming.normalizationRulesVersion,
                    appliedAtEpochMillis = System.currentTimeMillis()
                )
            )
            dao.rebuildSearchIndex()
        }
        return ExerciseImportPlan.IMPORT
    }

    private fun CatalogAssetRecord.toEntity(): ExerciseCatalogEntity {
        check(importStatus == ExerciseImportStatus.ACTIVE.name)
        check(ExerciseCatalogRecordValidator.isValidNameAndInstructions(nameEs, nameEn, instructionsEs, instructionsEn))
        return ExerciseCatalogEntity(
            catalogId = externalId,
            nameEs = nameEs,
            nameEn = nameEn,
            normalizedName = normalize(nameEs ?: nameEn.orEmpty()),
            instructionsEs = instructionsEs,
            instructionsEn = instructionsEn,
            primaryMuscle = Muscle.valueOf(primaryMuscle),
            bodyRegion = ExerciseBodyRegion.valueOf(bodyRegion),
            primaryEquipmentType = ExerciseEquipmentType.valueOf(equipment),
            locationCompatibility = ExerciseLocationCompatibility.valueOf(locationCompatibility)
        )
    }

    private fun com.example.ui.workout.ExerciseCatalogItem.toCanonicalEntity(): ExerciseCatalogEntity {
        val primaryMuscle = Muscle.fromLegacyName(primaryMuscle) ?: error("Unknown built-in muscle: $primaryMuscle")
        val profile = BuiltInExerciseEquipmentProfiles.getValue(id)
        return ExerciseCatalogEntity(
            catalogId = id,
            nameEs = name,
            nameEn = null,
            normalizedName = normalize(name),
            instructionsEs = instructions,
            instructionsEn = null,
            primaryMuscle = primaryMuscle,
            bodyRegion = primaryMuscle.toBodyRegion(),
            primaryEquipmentType = profile.primaryEquipmentType,
            locationCompatibility = profile.locationCompatibility
        )
    }

    private suspend fun reconcileBuiltIns(dao: ExerciseCatalogDao) {
        BuiltInExerciseEquipmentProfiles.byCatalogId.forEach { (catalogId, profile) ->
            check(
                dao.updateCatalogEquipmentMetadata(
                    catalogId = catalogId,
                    primaryEquipmentType = profile.primaryEquipmentType,
                    locationCompatibility = profile.locationCompatibility
                ) == 1
            ) { "Built-in catalog row is missing: $catalogId" }
            val missingSafeRows = BuiltInExerciseEquipmentProfiles.roomRequirementsFor(catalogId)
                .map { ExerciseEquipmentRequirementEntity(catalogId, it) }
            if (missingSafeRows.isNotEmpty()) {
                dao.insertEquipmentRequirementsIfMissing(missingSafeRows)
            }
        }
    }

    private fun Muscle.toBodyRegion(): ExerciseBodyRegion = when (this) {
        Muscle.BACK -> ExerciseBodyRegion.BACK
        Muscle.CHEST -> ExerciseBodyRegion.CHEST
        Muscle.SHOULDERS -> ExerciseBodyRegion.SHOULDERS
        Muscle.BICEPS, Muscle.TRICEPS -> ExerciseBodyRegion.UPPER_ARMS
        Muscle.ABS -> ExerciseBodyRegion.CORE
        Muscle.GLUTES, Muscle.QUADS, Muscle.HAMSTRINGS,
        Muscle.ADDUCTORS, Muscle.ABDUCTORS -> ExerciseBodyRegion.UPPER_LEGS
        Muscle.CALVES -> ExerciseBodyRegion.LOWER_LEGS
        Muscle.FOREARMS -> ExerciseBodyRegion.FOREARMS
        Muscle.CARDIO -> ExerciseBodyRegion.CARDIO
        Muscle.FULL_BODY -> ExerciseBodyRegion.CORE
        Muscle.TRAPS -> ExerciseBodyRegion.BACK
    }

    private fun ExerciseCatalogImportEntity.toManifest() = ExerciseImportManifest(
        source = source,
        importVersion = importVersion,
        compactSha256 = compactSha256,
        recordCount = recordCount,
        normalizationRulesVersion = normalizationRulesVersion
    )

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** Prefer the packaged gzip asset; keep the plain JSON path as a compatibility fallback. */
    private fun readCompactAsset(assets: AssetManager): ByteArray {
        val gzip = runCatching { assets.open("exercise_catalog/exercises.compact.json.gz") }.getOrNull()
        if (gzip != null) return gzip.use { GZIPInputStream(it).use { stream -> stream.readBytes() } }
        return assets.open("exercise_catalog/exercises.compact.json").use { it.readBytes() }
    }
}
