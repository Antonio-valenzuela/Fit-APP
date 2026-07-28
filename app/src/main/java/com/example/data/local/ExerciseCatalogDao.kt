package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Transaction
import com.example.data.model.ExerciseCatalogEntity
import com.example.data.model.ExerciseCatalogImportEntity
import com.example.data.model.ExerciseEquipmentRequirementEntity
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.ExerciseLocationCompatibility
import com.example.data.model.ExerciseSearchContentEntity
import com.example.data.model.ExerciseSecondaryMuscleEntity
import com.example.data.model.ExerciseSourceMappingEntity

data class ExerciseCatalogDetails(
    @Embedded val catalog: ExerciseCatalogEntity,
    @Relation(parentColumn = "catalogId", entityColumn = "catalogId")
    val secondaryMuscles: List<ExerciseSecondaryMuscleEntity>,
    @Relation(parentColumn = "catalogId", entityColumn = "catalogId")
    val equipmentRequirements: List<ExerciseEquipmentRequirementEntity>
)

data class CatalogMuscleTuple(
    val catalogId: String,
    val normalizedName: String,
    val primaryMuscle: String
)

@Dao
interface ExerciseCatalogDao {
    @Query("SELECT * FROM exercise_catalog WHERE catalogId = :catalogId LIMIT 1")
    suspend fun getByCatalogId(catalogId: String): ExerciseCatalogEntity?

    @Transaction
    @Query("SELECT * FROM exercise_catalog WHERE catalogId = :catalogId LIMIT 1")
    suspend fun getDetails(catalogId: String): ExerciseCatalogDetails?

    @Query("""
        SELECT c.* FROM exercise_catalog c
        WHERE (:muscle IS NULL OR c.primaryMuscle = :muscle OR EXISTS (
            SELECT 1 FROM exercise_secondary_muscles sm
            WHERE sm.catalogId = c.catalogId AND sm.muscle = :muscle
        ))
          AND (:bodyRegion IS NULL OR c.bodyRegion = :bodyRegion)
          AND (:equipmentType IS NULL OR c.primaryEquipmentType = :equipmentType OR EXISTS (
            SELECT 1 FROM exercise_equipment_requirements er
            WHERE er.catalogId = c.catalogId AND er.equipmentType = :equipmentType
          ) OR c.catalogId IN (:equipmentFilterBuiltInIds))
        ORDER BY c.normalizedName, c.catalogId LIMIT :limit OFFSET :offset
    """)
    suspend fun browseAny(
        equipmentFilterBuiltInIds: List<String>,
        muscle: String?,
        bodyRegion: String?,
        equipmentType: String?,
        limit: Int,
        offset: Int
    ): List<ExerciseCatalogEntity>

    @Query("""
        SELECT c.* FROM exercise_catalog c
        WHERE c.locationCompatibility IN ('HOME_COMPATIBLE', 'BOTH')
          AND (:muscle IS NULL OR c.primaryMuscle = :muscle OR EXISTS (
            SELECT 1 FROM exercise_secondary_muscles sm
            WHERE sm.catalogId = c.catalogId AND sm.muscle = :muscle
          ))
          AND (:bodyRegion IS NULL OR c.bodyRegion = :bodyRegion)
          AND (:equipmentType IS NULL OR c.primaryEquipmentType = :equipmentType OR EXISTS (
            SELECT 1 FROM exercise_equipment_requirements er
            WHERE er.catalogId = c.catalogId AND er.equipmentType = :equipmentType
          ) OR c.catalogId IN (:equipmentFilterBuiltInIds))
          AND (
              (EXISTS (
                  SELECT 1 FROM exercise_source_mappings built_in
                  WHERE built_in.source = 'FITPLAN_BUILT_IN' AND built_in.catalogId = c.catalogId
              ) AND c.catalogId IN (:compatibleBuiltInIds))
              OR
              (NOT EXISTS (
                  SELECT 1 FROM exercise_source_mappings built_in
                  WHERE built_in.source = 'FITPLAN_BUILT_IN' AND built_in.catalogId = c.catalogId
              ) AND c.primaryEquipmentType = 'BODY_WEIGHT' AND NOT EXISTS (
                  SELECT 1 FROM exercise_equipment_requirements r
                  WHERE r.catalogId = c.catalogId
              ))
          )
        ORDER BY c.normalizedName, c.catalogId LIMIT :limit OFFSET :offset
    """)
    suspend fun browseHomeWithoutEquipment(
        compatibleBuiltInIds: List<String>,
        equipmentFilterBuiltInIds: List<String>,
        muscle: String?,
        bodyRegion: String?,
        equipmentType: String?,
        limit: Int,
        offset: Int
    ): List<ExerciseCatalogEntity>

    /** All collection bindings are non-empty by contract; this query never binds an empty IN list. */
    @Query("""
        SELECT c.* FROM exercise_catalog c
        WHERE c.locationCompatibility IN ('HOME_COMPATIBLE', 'BOTH')
          AND (:muscle IS NULL OR c.primaryMuscle = :muscle OR EXISTS (
            SELECT 1 FROM exercise_secondary_muscles sm
            WHERE sm.catalogId = c.catalogId AND sm.muscle = :muscle
          ))
          AND (:bodyRegion IS NULL OR c.bodyRegion = :bodyRegion)
          AND (:equipmentType IS NULL OR c.primaryEquipmentType = :equipmentType OR EXISTS (
            SELECT 1 FROM exercise_equipment_requirements er
            WHERE er.catalogId = c.catalogId AND er.equipmentType = :equipmentType
          ) OR c.catalogId IN (:equipmentFilterBuiltInIds))
          AND (
              (EXISTS (
                  SELECT 1 FROM exercise_source_mappings built_in
                  WHERE built_in.source = 'FITPLAN_BUILT_IN' AND built_in.catalogId = c.catalogId
              ) AND c.catalogId IN (:compatibleBuiltInIds))
              OR
              (NOT EXISTS (
                  SELECT 1 FROM exercise_source_mappings built_in
                  WHERE built_in.source = 'FITPLAN_BUILT_IN' AND built_in.catalogId = c.catalogId
              ) AND NOT EXISTS (
                  SELECT 1 FROM exercise_equipment_requirements r
                  WHERE r.catalogId = c.catalogId
                    AND r.equipmentType NOT IN (:availableEquipment)
              ))
          )
        ORDER BY c.normalizedName, c.catalogId LIMIT :limit OFFSET :offset
    """)
    suspend fun browseHomeWithEquipment(
        availableEquipment: List<String>,
        compatibleBuiltInIds: List<String>,
        equipmentFilterBuiltInIds: List<String>,
        muscle: String?,
        bodyRegion: String?,
        equipmentType: String?,
        limit: Int,
        offset: Int
    ): List<ExerciseCatalogEntity>

    @Query("""
        SELECT c.* FROM exercise_catalog c
        WHERE c.locationCompatibility IN ('GYM_COMPATIBLE', 'BOTH')
          AND (:muscle IS NULL OR c.primaryMuscle = :muscle OR EXISTS (
            SELECT 1 FROM exercise_secondary_muscles sm
            WHERE sm.catalogId = c.catalogId AND sm.muscle = :muscle
          ))
          AND (:bodyRegion IS NULL OR c.bodyRegion = :bodyRegion)
          AND (:equipmentType IS NULL OR c.primaryEquipmentType = :equipmentType OR EXISTS (
            SELECT 1 FROM exercise_equipment_requirements er
            WHERE er.catalogId = c.catalogId AND er.equipmentType = :equipmentType
          ) OR c.catalogId IN (:equipmentFilterBuiltInIds))
        ORDER BY c.normalizedName, c.catalogId LIMIT :limit OFFSET :offset
    """)
    suspend fun browseGym(
        equipmentFilterBuiltInIds: List<String>,
        muscle: String?,
        bodyRegion: String?,
        equipmentType: String?,
        limit: Int,
        offset: Int
    ): List<ExerciseCatalogEntity>

    @Query("""
        SELECT c.* FROM exercise_catalog c
        JOIN exercise_catalog_fts fts ON fts.rowid = c.rowId
        WHERE exercise_catalog_fts MATCH :query
          AND (:muscle IS NULL OR c.primaryMuscle = :muscle OR EXISTS (
            SELECT 1 FROM exercise_secondary_muscles sm
            WHERE sm.catalogId = c.catalogId AND sm.muscle = :muscle
          ))
          AND (:bodyRegion IS NULL OR c.bodyRegion = :bodyRegion)
          AND (:equipmentType IS NULL OR c.primaryEquipmentType = :equipmentType OR EXISTS (
            SELECT 1 FROM exercise_equipment_requirements er
            WHERE er.catalogId = c.catalogId AND er.equipmentType = :equipmentType
          ) OR c.catalogId IN (:equipmentFilterBuiltInIds))
        ORDER BY c.normalizedName, c.catalogId LIMIT :limit OFFSET :offset
    """)
    suspend fun searchAny(
        query: String,
        equipmentFilterBuiltInIds: List<String>,
        muscle: String?,
        bodyRegion: String?,
        equipmentType: String?,
        limit: Int,
        offset: Int
    ): List<ExerciseCatalogEntity>

    @Query("""
        SELECT c.* FROM exercise_catalog c
        JOIN exercise_catalog_fts fts ON fts.rowid = c.rowId
        WHERE exercise_catalog_fts MATCH :query
          AND c.locationCompatibility IN ('HOME_COMPATIBLE', 'BOTH')
          AND (:muscle IS NULL OR c.primaryMuscle = :muscle OR EXISTS (
            SELECT 1 FROM exercise_secondary_muscles sm
            WHERE sm.catalogId = c.catalogId AND sm.muscle = :muscle
          ))
          AND (:bodyRegion IS NULL OR c.bodyRegion = :bodyRegion)
          AND (:equipmentType IS NULL OR c.primaryEquipmentType = :equipmentType OR EXISTS (
            SELECT 1 FROM exercise_equipment_requirements er
            WHERE er.catalogId = c.catalogId AND er.equipmentType = :equipmentType
          ) OR c.catalogId IN (:equipmentFilterBuiltInIds))
          AND (
              (EXISTS (
                  SELECT 1 FROM exercise_source_mappings built_in
                  WHERE built_in.source = 'FITPLAN_BUILT_IN' AND built_in.catalogId = c.catalogId
              ) AND c.catalogId IN (:compatibleBuiltInIds))
              OR
              (NOT EXISTS (
                  SELECT 1 FROM exercise_source_mappings built_in
                  WHERE built_in.source = 'FITPLAN_BUILT_IN' AND built_in.catalogId = c.catalogId
              ) AND c.primaryEquipmentType = 'BODY_WEIGHT' AND NOT EXISTS (
                  SELECT 1 FROM exercise_equipment_requirements r WHERE r.catalogId = c.catalogId
              ))
          )
        ORDER BY c.normalizedName, c.catalogId LIMIT :limit OFFSET :offset
    """)
    suspend fun searchHomeWithoutEquipment(
        query: String,
        compatibleBuiltInIds: List<String>,
        equipmentFilterBuiltInIds: List<String>,
        muscle: String?,
        bodyRegion: String?,
        equipmentType: String?,
        limit: Int,
        offset: Int
    ): List<ExerciseCatalogEntity>

    @Query("""
        SELECT c.* FROM exercise_catalog c
        JOIN exercise_catalog_fts fts ON fts.rowid = c.rowId
        WHERE exercise_catalog_fts MATCH :query
          AND c.locationCompatibility IN ('HOME_COMPATIBLE', 'BOTH')
          AND (:muscle IS NULL OR c.primaryMuscle = :muscle OR EXISTS (
            SELECT 1 FROM exercise_secondary_muscles sm
            WHERE sm.catalogId = c.catalogId AND sm.muscle = :muscle
          ))
          AND (:bodyRegion IS NULL OR c.bodyRegion = :bodyRegion)
          AND (:equipmentType IS NULL OR c.primaryEquipmentType = :equipmentType OR EXISTS (
            SELECT 1 FROM exercise_equipment_requirements er
            WHERE er.catalogId = c.catalogId AND er.equipmentType = :equipmentType
          ) OR c.catalogId IN (:equipmentFilterBuiltInIds))
          AND (
              (EXISTS (
                  SELECT 1 FROM exercise_source_mappings built_in
                  WHERE built_in.source = 'FITPLAN_BUILT_IN' AND built_in.catalogId = c.catalogId
              ) AND c.catalogId IN (:compatibleBuiltInIds))
              OR
              (NOT EXISTS (
                  SELECT 1 FROM exercise_source_mappings built_in
                  WHERE built_in.source = 'FITPLAN_BUILT_IN' AND built_in.catalogId = c.catalogId
              ) AND NOT EXISTS (
                  SELECT 1 FROM exercise_equipment_requirements r
                  WHERE r.catalogId = c.catalogId AND r.equipmentType NOT IN (:availableEquipment)
              ))
          )
        ORDER BY c.normalizedName, c.catalogId LIMIT :limit OFFSET :offset
    """)
    suspend fun searchHomeWithEquipment(
        query: String,
        availableEquipment: List<String>,
        compatibleBuiltInIds: List<String>,
        equipmentFilterBuiltInIds: List<String>,
        muscle: String?,
        bodyRegion: String?,
        equipmentType: String?,
        limit: Int,
        offset: Int
    ): List<ExerciseCatalogEntity>

    @Query("""
        SELECT c.* FROM exercise_catalog c
        JOIN exercise_catalog_fts fts ON fts.rowid = c.rowId
        WHERE exercise_catalog_fts MATCH :query
          AND c.locationCompatibility IN ('GYM_COMPATIBLE', 'BOTH')
          AND (:muscle IS NULL OR c.primaryMuscle = :muscle OR EXISTS (
            SELECT 1 FROM exercise_secondary_muscles sm
            WHERE sm.catalogId = c.catalogId AND sm.muscle = :muscle
          ))
          AND (:bodyRegion IS NULL OR c.bodyRegion = :bodyRegion)
          AND (:equipmentType IS NULL OR c.primaryEquipmentType = :equipmentType OR EXISTS (
            SELECT 1 FROM exercise_equipment_requirements er
            WHERE er.catalogId = c.catalogId AND er.equipmentType = :equipmentType
          ) OR c.catalogId IN (:equipmentFilterBuiltInIds))
        ORDER BY c.normalizedName, c.catalogId LIMIT :limit OFFSET :offset
    """)
    suspend fun searchGym(
        query: String,
        equipmentFilterBuiltInIds: List<String>,
        muscle: String?,
        bodyRegion: String?,
        equipmentType: String?,
        limit: Int,
        offset: Int
    ): List<ExerciseCatalogEntity>

    @Query("SELECT catalogId, normalizedName, primaryMuscle FROM exercise_catalog")
    suspend fun getAllCatalogMuscles(): List<CatalogMuscleTuple>

    @Query("SELECT catalogId, muscle FROM exercise_secondary_muscles")
    suspend fun getAllSecondaryMuscles(): List<ExerciseSecondaryMuscleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCatalog(entity: ExerciseCatalogEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSecondaryMuscles(rows: List<ExerciseSecondaryMuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEquipmentRequirements(rows: List<ExerciseEquipmentRequirementEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEquipmentRequirementsIfMissing(rows: List<ExerciseEquipmentRequirementEntity>)

    @Query("UPDATE exercise_catalog SET primaryEquipmentType = :primaryEquipmentType, locationCompatibility = :locationCompatibility WHERE catalogId = :catalogId")
    suspend fun updateCatalogEquipmentMetadata(
        catalogId: String,
        primaryEquipmentType: ExerciseEquipmentType,
        locationCompatibility: ExerciseLocationCompatibility
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSearchContent(entity: ExerciseSearchContentEntity)

    @Query("UPDATE exercise_search_content SET nameEs = :nameEs, nameEn = :nameEn, normalizedName = :normalizedName, aliases = :aliases WHERE rowid = :rowId")
    suspend fun updateSearchContent(rowId: Long, nameEs: String?, nameEn: String?, normalizedName: String, aliases: String)

    @Query("DELETE FROM exercise_search_content WHERE rowid = :rowId")
    suspend fun deleteSearchContent(rowId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSourceMappings(rows: List<ExerciseSourceMappingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportManifest(entity: ExerciseCatalogImportEntity)

    @Query("SELECT * FROM exercise_catalog_imports WHERE source = :source LIMIT 1")
    suspend fun getImportManifest(source: String): ExerciseCatalogImportEntity?

    @Query("SELECT COUNT(*) FROM exercise_source_mappings WHERE source = :source AND importVersion = :importVersion")
    suspend fun countMappings(source: String, importVersion: String): Int

    @Query("SELECT COUNT(*) FROM exercise_catalog")
    suspend fun countCatalogRows(): Int

    @Query("SELECT COUNT(*) FROM exercise_source_mappings WHERE source = :source AND importStatus = :status")
    suspend fun countMappingsByStatus(source: String, status: String): Int

    @Query("SELECT COUNT(*) FROM exercise_source_mappings WHERE source = :source AND catalogId IS NULL")
    suspend fun countMappingsWithoutCatalog(source: String): Int

    @Query("SELECT COUNT(*) FROM exercise_source_mappings WHERE source = :source AND catalogId IS NOT NULL")
    suspend fun countMappingsWithCatalog(source: String): Int

    /** Maintenance-only FTS4 rebuild; normal inserts/updates/deletes target contentEntity. */
    @Query("INSERT INTO exercise_catalog_fts(exercise_catalog_fts) VALUES ('rebuild')")
    suspend fun rebuildSearchIndex()
}
