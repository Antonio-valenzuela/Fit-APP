package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppDatabase
import com.example.data.local.ExerciseCatalogBootstrapper
import com.example.data.model.BuiltInExerciseEquipmentProfiles
import com.example.data.model.ExerciseBodyRegion
import com.example.data.model.ExerciseCatalogEntity
import com.example.data.model.ExerciseCatalogFilter
import com.example.data.model.ExerciseCatalogLocationFilter
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.ExerciseEquipmentRequirementEntity
import com.example.data.model.ExerciseImportPlan
import com.example.data.model.ExerciseLocationCompatibility
import com.example.data.model.Muscle
import com.example.data.repository.ExerciseCatalogRepository
import com.example.data.repository.RoomExerciseCatalogReader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogBootstrapInstrumentedTest {
    private val databaseName = "exercise_catalog_bootstrap_test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun bootstrapIsIdempotentAndKeepsExpectedMappingsAndCanonicals() = runBlocking {
        val bootstrapper = ExerciseCatalogBootstrapper(database)
        assertEquals(ExerciseImportPlan.IMPORT, bootstrapper.importIfNeeded(context))
        assertEquals(ExerciseImportPlan.NO_OP, bootstrapper.importIfNeeded(context))

        val dao = database.exerciseCatalogDao()
        assertEquals(1324, dao.countMappings("exercises_dataset", "exercise-catalog-2026-07-15-v1"))
        assertEquals(1310, dao.countMappingsByStatus("exercises_dataset", "ACTIVE"))
        assertEquals(9, dao.countMappingsByStatus("exercises_dataset", "MERGED"))
        assertEquals(5, dao.countMappingsByStatus("exercises_dataset", "REVIEW"))
        assertEquals(0, dao.countMappingsByStatus("exercises_dataset", "DISCARDED"))
        assertEquals(5, dao.countMappingsWithoutCatalog("exercises_dataset"))
        assertEquals(1319, dao.countMappingsWithCatalog("exercises_dataset"))
        assertEquals(27, dao.countMappingsWithCatalog("FITPLAN_BUILT_IN"))
        assertEquals(1337, dao.countCatalogRows())
        assertNotNull(dao.getImportManifest("exercises_dataset"))

        listOf("0697", "0716", "1403", "1496", "1766").forEach { sourceId ->
            assertEquals(null, dao.getByCatalogId("exercises_dataset:$sourceId"))
        }
        val mergedRows = database.openHelper.readableDatabase.query(
            "SELECT sourceOriginalId, catalogId FROM exercise_source_mappings WHERE source = 'exercises_dataset' AND importStatus = 'MERGED'"
        ).use { cursor ->
            val sourceIndex = cursor.getColumnIndexOrThrow("sourceOriginalId")
            val catalogIndex = cursor.getColumnIndexOrThrow("catalogId")
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(sourceIndex) to cursor.getString(catalogIndex))
            }
        }
        assertEquals(9, mergedRows.size)
        assertTrue(mergedRows.all { it.second.isNotBlank() })
        assertTrue(mergedRows.none { it.second.startsWith("exercises_dataset:") && it.first == it.second.substringAfter(':') })

        database.close()
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        try {
            assertEquals(1337, reopened.exerciseCatalogDao().countCatalogRows())
            assertNotNull(reopened.exerciseCatalogDao().getImportManifest("exercises_dataset"))
            assertEquals(
                ExerciseImportPlan.NO_OP,
                ExerciseCatalogBootstrapper(reopened).importIfNeeded(context)
            )
        } finally {
            reopened.close()
        }
    }

    @Test
    fun bootstrapRollsBackOnCanonicalConflict() = runBlocking {
        val dao = database.exerciseCatalogDao()
        dao.insertCatalog(
            ExerciseCatalogEntity(
                catalogId = "exercises_dataset:0001",
                nameEs = "Conflicto",
                nameEn = null,
                normalizedName = "conflicto",
                instructionsEs = "Conflicto",
                instructionsEn = null,
                primaryMuscle = Muscle.CHEST,
                bodyRegion = ExerciseBodyRegion.CHEST,
                primaryEquipmentType = ExerciseEquipmentType.BODY_WEIGHT,
                locationCompatibility = ExerciseLocationCompatibility.HOME_COMPATIBLE
            )
        )
        var failed = false
        try {
            ExerciseCatalogBootstrapper(database).importIfNeeded(context)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Expected canonical conflict to abort the transaction", failed)
        assertEquals(1, dao.countCatalogRows())
        assertEquals(null, dao.getImportManifest("exercises_dataset"))
        assertEquals(0, dao.countMappings("exercises_dataset", "exercise-catalog-2026-07-15-v1"))
    }

    @Test
    fun filtersAndSpanishAliasesWorkAfterBootstrap() = runBlocking {
        ExerciseCatalogBootstrapper(database).importIfNeeded(context)
        val dao = database.exerciseCatalogDao()
        val repository = ExerciseCatalogRepository(RoomExerciseCatalogReader(dao))
        val cumulativeRequirementId = "test:cumulative-equipment"
        dao.insertCatalog(
            ExerciseCatalogEntity(
                catalogId = cumulativeRequirementId,
                nameEs = "Prueba de requisitos acumulativos",
                nameEn = null,
                normalizedName = "prueba requisitos acumulativos",
                instructionsEs = "Caso sintético de prueba.",
                instructionsEn = null,
                primaryMuscle = Muscle.CHEST,
                bodyRegion = ExerciseBodyRegion.CHEST,
                primaryEquipmentType = ExerciseEquipmentType.DUMBBELL,
                locationCompatibility = ExerciseLocationCompatibility.BOTH
            )
        )
        dao.insertEquipmentRequirements(
            listOf(
                ExerciseEquipmentRequirementEntity(cumulativeRequirementId, ExerciseEquipmentType.DUMBBELL),
                ExerciseEquipmentRequirementEntity(cumulativeRequirementId, ExerciseEquipmentType.BENCH)
            )
        )
        val noEquipment = repository.browse(
            ExerciseCatalogFilter(location = ExerciseCatalogLocationFilter.HOME),
            limit = 5000
        )
        assertTrue(noEquipment.isNotEmpty())
        assertTrue(noEquipment.all { it.locationCompatibility in setOf(ExerciseLocationCompatibility.HOME_COMPATIBLE, ExerciseLocationCompatibility.BOTH) })
        assertTrue(noEquipment.any { it.primaryEquipmentType == ExerciseEquipmentType.BODY_WEIGHT })
        assertFalse(noEquipment.any { it.catalogId == "leg_curl" })
        assertEquals(
            BuiltInExerciseEquipmentProfiles.compatibleHomeCatalogIds(emptySet()).toSet(),
            noEquipment.map { it.catalogId }.filter { it in BuiltInExerciseEquipmentProfiles.catalogIds }.toSet()
        )

        val dumbbellOnly = repository.browse(
            ExerciseCatalogFilter(
                location = ExerciseCatalogLocationFilter.HOME,
                availableEquipment = setOf(ExerciseEquipmentType.DUMBBELL)
            ),
            limit = 5000
        )
        assertTrue(dumbbellOnly.all { it.locationCompatibility in setOf(ExerciseLocationCompatibility.HOME_COMPATIBLE, ExerciseLocationCompatibility.BOTH) })
        assertFalse(hasRequirementOutside(dumbbellOnly.map { it.catalogId }, setOf(ExerciseEquipmentType.DUMBBELL.name)))
        assertEquals(
            BuiltInExerciseEquipmentProfiles.compatibleHomeCatalogIds(setOf(ExerciseEquipmentType.DUMBBELL)).toSet(),
            dumbbellOnly.map { it.catalogId }.filter { it in BuiltInExerciseEquipmentProfiles.catalogIds }.toSet()
        )

        val dumbbellAndBench = repository.browse(
            ExerciseCatalogFilter(
                location = ExerciseCatalogLocationFilter.HOME,
                availableEquipment = setOf(ExerciseEquipmentType.DUMBBELL, ExerciseEquipmentType.BENCH)
            ),
            limit = 5000
        )
        assertFalse(hasRequirementOutside(dumbbellAndBench.map { it.catalogId }, setOf(ExerciseEquipmentType.DUMBBELL.name, ExerciseEquipmentType.BENCH.name)))
        assertFalse(dumbbellOnly.any { it.catalogId == cumulativeRequirementId })
        assertTrue(dumbbellAndBench.any { it.catalogId == cumulativeRequirementId })
        assertEquals(
            BuiltInExerciseEquipmentProfiles.compatibleHomeCatalogIds(
                setOf(ExerciseEquipmentType.DUMBBELL, ExerciseEquipmentType.BENCH)
            ).toSet(),
            dumbbellAndBench.map { it.catalogId }.filter { it in BuiltInExerciseEquipmentProfiles.catalogIds }.toSet()
        )

        val gym = repository.browse(
            ExerciseCatalogFilter(location = ExerciseCatalogLocationFilter.GYM),
            limit = 5000
        )
        assertTrue(gym.all { it.locationCompatibility in setOf(ExerciseLocationCompatibility.GYM_COMPATIBLE, ExerciseLocationCompatibility.BOTH) })
        assertEquals(
            BuiltInExerciseEquipmentProfiles.catalogIds,
            gym.map { it.catalogId }.filter { it in BuiltInExerciseEquipmentProfiles.catalogIds }.toSet()
        )
        val any = repository.browse(
            ExerciseCatalogFilter(location = ExerciseCatalogLocationFilter.ANY),
            limit = 5000
        )
        assertEquals(
            BuiltInExerciseEquipmentProfiles.catalogIds,
            any.map { it.catalogId }.filter { it in BuiltInExerciseEquipmentProfiles.catalogIds }.toSet()
        )

        val anyDumbbell = repository.browse(
            ExerciseCatalogFilter(
                location = ExerciseCatalogLocationFilter.ANY,
                equipment = ExerciseEquipmentType.DUMBBELL
            ),
            limit = 5000
        )
        assertTrue(anyDumbbell.any { it.catalogId == "bench_press" })

        val homeBodyWeight = repository.browse(
            ExerciseCatalogFilter(
                location = ExerciseCatalogLocationFilter.HOME,
                equipment = ExerciseEquipmentType.BODY_WEIGHT
            ),
            limit = 5000
        )
        assertTrue(homeBodyWeight.any { it.catalogId == "squat" })

        val homeDumbbellAlternative = repository.browse(
            ExerciseCatalogFilter(
                location = ExerciseCatalogLocationFilter.HOME,
                equipment = ExerciseEquipmentType.DUMBBELL,
                availableEquipment = setOf(ExerciseEquipmentType.DUMBBELL)
            ),
            limit = 5000
        )
        assertTrue(homeDumbbellAlternative.any { it.catalogId == "bench_press" })

        val gymDumbbell = repository.browse(
            ExerciseCatalogFilter(
                location = ExerciseCatalogLocationFilter.GYM,
                equipment = ExerciseEquipmentType.DUMBBELL
            ),
            limit = 5000
        )
        assertTrue(gymDumbbell.any { it.catalogId == "bench_press" })

        val searchAnyDumbbell = repository.browse(
            ExerciseCatalogFilter(
                text = "press de banca",
                location = ExerciseCatalogLocationFilter.ANY,
                equipment = ExerciseEquipmentType.DUMBBELL
            ),
            limit = 50
        )
        assertTrue(searchAnyDumbbell.any { it.catalogId == "bench_press" })

        val searchHomeBodyWeight = repository.browse(
            ExerciseCatalogFilter(
                text = "sentadilla",
                location = ExerciseCatalogLocationFilter.HOME,
                equipment = ExerciseEquipmentType.BODY_WEIGHT
            ),
            limit = 50
        )
        assertTrue(searchHomeBodyWeight.any { it.catalogId == "squat" })

        val searchHomeDumbbell = repository.browse(
            ExerciseCatalogFilter(
                text = "press de banca",
                location = ExerciseCatalogLocationFilter.HOME,
                equipment = ExerciseEquipmentType.DUMBBELL,
                availableEquipment = setOf(ExerciseEquipmentType.DUMBBELL)
            ),
            limit = 50
        )
        assertTrue(searchHomeDumbbell.any { it.catalogId == "bench_press" })

        val searchGymDumbbell = repository.browse(
            ExerciseCatalogFilter(
                text = "press de banca",
                location = ExerciseCatalogLocationFilter.GYM,
                equipment = ExerciseEquipmentType.DUMBBELL
            ),
            limit = 50
        )
        assertTrue(searchGymDumbbell.any { it.catalogId == "bench_press" })

        listOf(
            "press de banca", "peso muerto", "sentadilla", "flexiones", "lagartijas",
            "elevaciones laterales", "curl de biceps", "curl de bíceps", "dominadas",
            "remo", "zancadas", "plancha"
        ).forEach { query ->
            assertTrue("Alias no encontrado: $query", repository.browse(ExerciseCatalogFilter(text = query), 50, 0).isNotEmpty())
        }
        assertTrue(repository.browse(ExerciseCatalogFilter(text = "\"(*)--"), 5, 0).isNotEmpty())
        assertTrue(repository.browse(ExerciseCatalogFilter(location = ExerciseCatalogLocationFilter.ANY, text = ""), 5, 0).isNotEmpty())
    }

    @Test
    fun matchingManifestRepairsBuiltInMetadataAndRequirementsBeforeReturningNoOp() = runBlocking {
        val bootstrapper = ExerciseCatalogBootstrapper(database)
        assertEquals(ExerciseImportPlan.IMPORT, bootstrapper.importIfNeeded(context))
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM exercise_equipment_requirements WHERE catalogId IN " +
                "(SELECT catalogId FROM exercise_source_mappings WHERE source = 'FITPLAN_BUILT_IN')"
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE exercise_catalog SET primaryEquipmentType = 'BODY_WEIGHT', " +
                "locationCompatibility = 'HOME_COMPATIBLE' WHERE catalogId IN " +
                "(SELECT catalogId FROM exercise_source_mappings WHERE source = 'FITPLAN_BUILT_IN')"
        )

        assertEquals(ExerciseImportPlan.NO_OP, bootstrapper.importIfNeeded(context))

        val dao = database.exerciseCatalogDao()
        BuiltInExerciseEquipmentProfiles.byCatalogId.forEach { (catalogId, profile) ->
            val row = requireNotNull(dao.getByCatalogId(catalogId))
            assertEquals(profile.primaryEquipmentType, row.primaryEquipmentType)
            assertEquals(ExerciseLocationCompatibility.BOTH, row.locationCompatibility)
            assertEquals(
                profile.allOf.filterNot { it == ExerciseEquipmentType.BODY_WEIGHT }.toSet(),
                requirementTypesFor(catalogId)
            )
        }
        assertEquals(27, dao.countMappingsWithCatalog("FITPLAN_BUILT_IN"))
    }

    private fun hasRequirementOutside(catalogIds: List<String>, available: Set<String>): Boolean {
        if (catalogIds.isEmpty()) return false
        val placeholders = available.joinToString(",") { "'$it'" }
        val ids = catalogIds.joinToString(",") { "'${it.replace("'", "''")}'" }
        database.openHelper.readableDatabase.query(
            "SELECT 1 FROM exercise_equipment_requirements WHERE catalogId IN ($ids) AND equipmentType NOT IN ($placeholders) LIMIT 1"
        ).use { return it.moveToFirst() }
    }

    private fun requirementTypesFor(catalogId: String): Set<ExerciseEquipmentType> {
        database.openHelper.readableDatabase.query(
            "SELECT equipmentType FROM exercise_equipment_requirements WHERE catalogId = ? ORDER BY equipmentType",
            arrayOf(catalogId)
        ).use { cursor ->
            val equipmentIndex = cursor.getColumnIndexOrThrow("equipmentType")
            return buildSet {
                while (cursor.moveToNext()) add(ExerciseEquipmentType.valueOf(cursor.getString(equipmentIndex)))
            }
        }
    }
}
