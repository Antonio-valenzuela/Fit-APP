package com.example.data.repository

import com.example.data.local.ExerciseCatalogDetails
import com.example.data.model.ExerciseBodyRegion
import com.example.data.model.BuiltInExerciseEquipmentProfiles
import com.example.data.model.ExerciseCatalogEntity
import com.example.data.model.ExerciseCatalogFilter
import com.example.data.model.ExerciseCatalogLanguage
import com.example.data.model.ExerciseCatalogLocationFilter
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.ExerciseLocationCompatibility
import com.example.data.model.ExerciseSecondaryMuscleEntity
import com.example.data.model.Muscle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogRepositoryTest {
    @Test
    fun fallsBackToEnglishWithoutInventingSpanish() = runTest {
        val row = entity(
            catalogId = "exercises_dataset:0001",
            nameEs = null,
            nameEn = "Push up",
            instructionsEs = null,
            instructionsEn = "Keep your body straight."
        )
        val view = ExerciseCatalogRepository(FakeReader(rows = listOf(row)))
            .getDetail(row.catalogId)

        requireNotNull(view)
        assertEquals("Push up", view.name)
        assertEquals("Keep your body straight.", view.instructions)
        assertTrue(!view.hasSpanishTranslation)
    }

    @Test
    fun routesHomeQueriesWithoutBindingAnEmptyEquipmentList() = runTest {
        val reader = FakeReader(rows = listOf(entity("home")))
        val repository = ExerciseCatalogRepository(reader)

        repository.browse(ExerciseCatalogFilter(location = ExerciseCatalogLocationFilter.HOME))
        assertEquals("browseHomeWithoutEquipment", reader.lastCall)
        assertEquals(7, reader.lastBuiltInIds.size)
        assertTrue(reader.lastBuiltInIds.isNotEmpty())

        repository.browse(
            ExerciseCatalogFilter(
                location = ExerciseCatalogLocationFilter.HOME,
                availableEquipment = setOf(ExerciseEquipmentType.BENCH, ExerciseEquipmentType.DUMBBELL)
            )
        )
        assertEquals("browseHomeWithEquipment:BENCH,DUMBBELL", reader.lastCall)
        assertEquals(19, reader.lastBuiltInIds.size)
        assertEquals(
            BuiltInExerciseEquipmentProfiles.compatibleHomeCatalogIds(
                setOf(ExerciseEquipmentType.BENCH, ExerciseEquipmentType.DUMBBELL)
            ),
            reader.lastBuiltInIds
        )
    }

    @Test
    fun sanitizesFtsTextAndPassesFiltersToSqlBeforePagination() = runTest {
        val row = entity("bench", primaryMuscle = Muscle.CHEST, bodyRegion = ExerciseBodyRegion.CHEST)
        val reader = FakeReader(rows = listOf(row))
        val result = ExerciseCatalogRepository(reader).browse(
            ExerciseCatalogFilter(
                text = "  PRÉSS!!! \"banca\" ",
                muscle = Muscle.CHEST,
                bodyRegion = ExerciseBodyRegion.CHEST,
                equipment = ExerciseEquipmentType.BARBELL
            )
        )

        assertEquals(listOf("press* banca*"), reader.searches)
        assertEquals(Triple("CHEST", "CHEST", "BARBELL"), reader.lastFilters)
        assertTrue("squat" in reader.lastEquipmentFilterBuiltInIds)
        assertEquals(listOf("bench"), result.map { it.catalogId })
    }

    @Test
    fun routesAlternativeEquipmentBuiltInsThroughEveryBrowseAndSearchLocation() = runTest {
        val reader = FakeReader(rows = listOf(entity("bench_press")))
        val repository = ExerciseCatalogRepository(reader)
        val expectedAlternative = "bench_press"

        ExerciseCatalogLocationFilter.entries.forEach { location ->
            repository.browse(
                ExerciseCatalogFilter(
                    location = location,
                    equipment = ExerciseEquipmentType.DUMBBELL,
                    availableEquipment = if (location == ExerciseCatalogLocationFilter.HOME) {
                        setOf(ExerciseEquipmentType.DUMBBELL)
                    } else {
                        emptySet()
                    }
                )
            )
            assertTrue("browse $location omitted OR alternative", expectedAlternative in reader.lastEquipmentFilterBuiltInIds)

            repository.browse(
                ExerciseCatalogFilter(
                    text = "press",
                    location = location,
                    equipment = ExerciseEquipmentType.DUMBBELL,
                    availableEquipment = if (location == ExerciseCatalogLocationFilter.HOME) {
                        setOf(ExerciseEquipmentType.DUMBBELL)
                    } else {
                        emptySet()
                    }
                )
            )
            assertTrue("search $location omitted OR alternative", expectedAlternative in reader.lastEquipmentFilterBuiltInIds)
        }
    }

    @Test
    fun routesExplicitSentinelInsteadOfAnEmptyEquipmentFilterINList() = runTest {
        val reader = FakeReader(rows = listOf(entity("home")))
        val repository = ExerciseCatalogRepository(reader)

        repository.browse(ExerciseCatalogFilter(location = ExerciseCatalogLocationFilter.ANY))

        assertEquals(
            listOf(BuiltInExerciseEquipmentProfiles.NO_EQUIPMENT_FILTER_MATCH_ID),
            reader.lastEquipmentFilterBuiltInIds
        )
    }

    private fun entity(
        catalogId: String,
        nameEs: String? = "Press banca",
        nameEn: String? = null,
        instructionsEs: String? = "Empuja con control.",
        instructionsEn: String? = null,
        primaryMuscle: Muscle = Muscle.CHEST,
        bodyRegion: ExerciseBodyRegion = ExerciseBodyRegion.CHEST
    ) = ExerciseCatalogEntity(
        catalogId = catalogId,
        nameEs = nameEs,
        nameEn = nameEn,
        normalizedName = "press banca",
        instructionsEs = instructionsEs,
        instructionsEn = instructionsEn,
        primaryMuscle = primaryMuscle,
        bodyRegion = bodyRegion,
        primaryEquipmentType = ExerciseEquipmentType.BARBELL,
        locationCompatibility = ExerciseLocationCompatibility.BOTH
    )

    private class FakeReader(private val rows: List<ExerciseCatalogEntity>) : ExerciseCatalogReader {
        var lastCall: String? = null
        var lastFilters: Triple<String?, String?, String?>? = null
        var lastBuiltInIds: List<String> = emptyList()
        var lastEquipmentFilterBuiltInIds: List<String> = emptyList()
        val searches = mutableListOf<String>()

        private fun recordFilters(muscle: String?, bodyRegion: String?, equipmentType: String?) {
            lastFilters = Triple(muscle, bodyRegion, equipmentType)
        }

        override suspend fun getByCatalogId(catalogId: String) = rows.firstOrNull { it.catalogId == catalogId }
        override suspend fun getDetails(catalogId: String) = rows.firstOrNull { it.catalogId == catalogId }?.let {
            ExerciseCatalogDetails(it, listOf(ExerciseSecondaryMuscleEntity(it.catalogId, Muscle.TRICEPS)), emptyList())
        }
        override suspend fun browseAny(
            equipmentFilterBuiltInIds: List<String>,
            muscle: String?,
            bodyRegion: String?,
            equipmentType: String?,
            limit: Int,
            offset: Int
        ): List<ExerciseCatalogEntity> {
            lastCall = "browseAny"
            lastEquipmentFilterBuiltInIds = equipmentFilterBuiltInIds
            recordFilters(muscle, bodyRegion, equipmentType)
            return rows
        }
        override suspend fun browseHomeWithoutEquipment(
            compatibleBuiltInIds: List<String>,
            equipmentFilterBuiltInIds: List<String>,
            muscle: String?,
            bodyRegion: String?,
            equipmentType: String?,
            limit: Int,
            offset: Int
        ): List<ExerciseCatalogEntity> {
            lastCall = "browseHomeWithoutEquipment"
            lastBuiltInIds = compatibleBuiltInIds
            lastEquipmentFilterBuiltInIds = equipmentFilterBuiltInIds
            recordFilters(muscle, bodyRegion, equipmentType)
            return rows
        }
        override suspend fun browseHomeWithEquipment(
            availableEquipment: List<String>,
            compatibleBuiltInIds: List<String>,
            equipmentFilterBuiltInIds: List<String>,
            muscle: String?,
            bodyRegion: String?,
            equipmentType: String?,
            limit: Int,
            offset: Int
        ): List<ExerciseCatalogEntity> {
            lastCall = "browseHomeWithEquipment:${availableEquipment.joinToString(",")}"
            lastBuiltInIds = compatibleBuiltInIds
            lastEquipmentFilterBuiltInIds = equipmentFilterBuiltInIds
            recordFilters(muscle, bodyRegion, equipmentType)
            return rows
        }
        override suspend fun browseGym(
            equipmentFilterBuiltInIds: List<String>,
            muscle: String?,
            bodyRegion: String?,
            equipmentType: String?,
            limit: Int,
            offset: Int
        ): List<ExerciseCatalogEntity> {
            lastCall = "browseGym"
            lastEquipmentFilterBuiltInIds = equipmentFilterBuiltInIds
            recordFilters(muscle, bodyRegion, equipmentType)
            return rows
        }
        override suspend fun searchAny(
            query: String,
            equipmentFilterBuiltInIds: List<String>,
            muscle: String?,
            bodyRegion: String?,
            equipmentType: String?,
            limit: Int,
            offset: Int
        ): List<ExerciseCatalogEntity> {
            searches += query
            lastCall = "searchAny"
            lastEquipmentFilterBuiltInIds = equipmentFilterBuiltInIds
            recordFilters(muscle, bodyRegion, equipmentType)
            return rows
        }
        override suspend fun searchHomeWithoutEquipment(
            query: String,
            compatibleBuiltInIds: List<String>,
            equipmentFilterBuiltInIds: List<String>,
            muscle: String?,
            bodyRegion: String?,
            equipmentType: String?,
            limit: Int,
            offset: Int
        ): List<ExerciseCatalogEntity> {
            searches += query
            lastCall = "searchHomeWithoutEquipment"
            lastBuiltInIds = compatibleBuiltInIds
            lastEquipmentFilterBuiltInIds = equipmentFilterBuiltInIds
            recordFilters(muscle, bodyRegion, equipmentType)
            return rows
        }
        override suspend fun searchHomeWithEquipment(
            query: String,
            availableEquipment: List<String>,
            compatibleBuiltInIds: List<String>,
            equipmentFilterBuiltInIds: List<String>,
            muscle: String?,
            bodyRegion: String?,
            equipmentType: String?,
            limit: Int,
            offset: Int
        ): List<ExerciseCatalogEntity> {
            searches += query
            lastCall = "searchHomeWithEquipment"
            lastBuiltInIds = compatibleBuiltInIds
            lastEquipmentFilterBuiltInIds = equipmentFilterBuiltInIds
            recordFilters(muscle, bodyRegion, equipmentType)
            return rows
        }
        override suspend fun searchGym(
            query: String,
            equipmentFilterBuiltInIds: List<String>,
            muscle: String?,
            bodyRegion: String?,
            equipmentType: String?,
            limit: Int,
            offset: Int
        ): List<ExerciseCatalogEntity> {
            searches += query
            lastCall = "searchGym"
            lastEquipmentFilterBuiltInIds = equipmentFilterBuiltInIds
            recordFilters(muscle, bodyRegion, equipmentType)
            return rows
        }
    }
}
