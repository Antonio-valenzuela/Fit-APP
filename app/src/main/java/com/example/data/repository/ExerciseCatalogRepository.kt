package com.example.data.repository

import com.example.data.local.ExerciseCatalogDao
import com.example.data.local.ExerciseCatalogDetails
import com.example.data.model.ExerciseCatalogEntity
import com.example.data.model.ExerciseCatalogFilter
import com.example.data.model.ExerciseCatalogItemView
import com.example.data.model.ExerciseCatalogLanguage
import com.example.data.model.ExerciseCatalogLocationFilter
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.BuiltInExerciseEquipmentProfiles
import com.example.data.model.sanitizeExerciseFtsQuery
import com.example.data.model.toItemView

/** Read-only catalog boundary. It does not replace TrainingCatalog or trigger bootstrap. */
interface ExerciseCatalogReader {
    suspend fun getByCatalogId(catalogId: String): ExerciseCatalogEntity?
    suspend fun getDetails(catalogId: String): ExerciseCatalogDetails?
    suspend fun browseAny(equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int): List<ExerciseCatalogEntity>
    suspend fun browseHomeWithoutEquipment(compatibleBuiltInIds: List<String>, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int): List<ExerciseCatalogEntity>
    suspend fun browseHomeWithEquipment(availableEquipment: List<String>, compatibleBuiltInIds: List<String>, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int): List<ExerciseCatalogEntity>
    suspend fun browseGym(equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int): List<ExerciseCatalogEntity>
    suspend fun searchAny(query: String, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int): List<ExerciseCatalogEntity>
    suspend fun searchHomeWithoutEquipment(query: String, compatibleBuiltInIds: List<String>, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int): List<ExerciseCatalogEntity>
    suspend fun searchHomeWithEquipment(query: String, availableEquipment: List<String>, compatibleBuiltInIds: List<String>, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int): List<ExerciseCatalogEntity>
    suspend fun searchGym(query: String, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int): List<ExerciseCatalogEntity>
}

class RoomExerciseCatalogReader(private val dao: ExerciseCatalogDao) : ExerciseCatalogReader {
    override suspend fun getByCatalogId(catalogId: String) = dao.getByCatalogId(catalogId)
    override suspend fun getDetails(catalogId: String) = dao.getDetails(catalogId)
    override suspend fun browseAny(equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int) =
        dao.browseAny(equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
    override suspend fun browseHomeWithoutEquipment(compatibleBuiltInIds: List<String>, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int) =
        dao.browseHomeWithoutEquipment(compatibleBuiltInIds, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
    override suspend fun browseHomeWithEquipment(availableEquipment: List<String>, compatibleBuiltInIds: List<String>, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int) =
        dao.browseHomeWithEquipment(availableEquipment, compatibleBuiltInIds, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
    override suspend fun browseGym(equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int) =
        dao.browseGym(equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
    override suspend fun searchAny(query: String, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int) =
        dao.searchAny(query, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
    override suspend fun searchHomeWithoutEquipment(query: String, compatibleBuiltInIds: List<String>, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int) =
        dao.searchHomeWithoutEquipment(query, compatibleBuiltInIds, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
    override suspend fun searchHomeWithEquipment(query: String, availableEquipment: List<String>, compatibleBuiltInIds: List<String>, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int) =
        dao.searchHomeWithEquipment(query, availableEquipment, compatibleBuiltInIds, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
    override suspend fun searchGym(query: String, equipmentFilterBuiltInIds: List<String>, muscle: String?, bodyRegion: String?, equipmentType: String?, limit: Int, offset: Int) =
        dao.searchGym(query, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
}

class ExerciseCatalogRepository(private val reader: ExerciseCatalogReader) {
    suspend fun getDetail(
        catalogId: String,
        language: ExerciseCatalogLanguage = ExerciseCatalogLanguage.SPANISH
    ): ExerciseCatalogItemView? = reader.getDetails(catalogId)?.toView(language)

    suspend fun browse(
        filter: ExerciseCatalogFilter = ExerciseCatalogFilter(),
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
        language: ExerciseCatalogLanguage = ExerciseCatalogLanguage.SPANISH
    ): List<ExerciseCatalogItemView> {
        require(limit > 0) { "limit must be positive" }
        require(offset >= 0) { "offset cannot be negative" }
        val normalizedQuery = filter.text?.let(::sanitizeExerciseFtsQuery)
        val equipment = filter.availableEquipment.map(ExerciseEquipmentType::name).sorted()
        val compatibleBuiltInIds = BuiltInExerciseEquipmentProfiles
            .compatibleHomeCatalogIds(filter.availableEquipment)
        check(compatibleBuiltInIds.isNotEmpty()) { "HOME built-in compatibility list cannot be empty" }
        val muscle = filter.muscle?.name
        val bodyRegion = filter.bodyRegion?.name
        val equipmentType = filter.equipment?.name
        val equipmentFilterBuiltInIds = BuiltInExerciseEquipmentProfiles
            .nonEmptyCatalogIdsMatchingEquipmentFilter(filter.equipment)
        check(equipmentFilterBuiltInIds.isNotEmpty()) {
            "Equipment filter built-in list must bind an explicit sentinel instead of empty IN"
        }
        val rows = if (normalizedQuery == null) {
            when (filter.location) {
                ExerciseCatalogLocationFilter.ANY -> reader.browseAny(equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
                ExerciseCatalogLocationFilter.HOME -> if (equipment.isEmpty()) {
                    reader.browseHomeWithoutEquipment(compatibleBuiltInIds, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
                } else {
                    reader.browseHomeWithEquipment(equipment, compatibleBuiltInIds, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
                }
                ExerciseCatalogLocationFilter.GYM -> reader.browseGym(equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
            }
        } else {
            when (filter.location) {
                ExerciseCatalogLocationFilter.ANY -> reader.searchAny(normalizedQuery, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
                ExerciseCatalogLocationFilter.HOME -> if (equipment.isEmpty()) {
                    reader.searchHomeWithoutEquipment(normalizedQuery, compatibleBuiltInIds, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
                } else {
                    reader.searchHomeWithEquipment(normalizedQuery, equipment, compatibleBuiltInIds, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
                }
                ExerciseCatalogLocationFilter.GYM -> reader.searchGym(normalizedQuery, equipmentFilterBuiltInIds, muscle, bodyRegion, equipmentType, limit, offset)
            }
        }
        return rows.map { it.toItemView(language = language) }
    }

    suspend fun getCompatible(
        location: ExerciseCatalogLocationFilter,
        availableEquipment: Set<ExerciseEquipmentType> = emptySet(),
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
        language: ExerciseCatalogLanguage = ExerciseCatalogLanguage.SPANISH
    ): List<ExerciseCatalogItemView> = browse(
        ExerciseCatalogFilter(location = location, availableEquipment = availableEquipment),
        limit,
        offset,
        language
    )

    private fun ExerciseCatalogDetails.toView(language: ExerciseCatalogLanguage): ExerciseCatalogItemView =
        catalog.toItemView(secondaryMuscles, equipmentRequirements, language)

    companion object {
        const val DEFAULT_PAGE_SIZE = 40
    }
}
