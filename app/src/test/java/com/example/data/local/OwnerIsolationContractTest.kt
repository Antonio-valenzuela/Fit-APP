package com.example.data.local

import com.example.data.model.ActivitySessionEntity
import com.example.data.model.FoodEntryEntity
import com.example.data.model.FoodItemEntity
import com.example.data.model.FoodLogEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.WorkoutLogEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OwnerIsolationContractTest {
    private val ownerScopedEntities = listOf(
        FoodLogEntity::class.java,
        FoodEntryEntity::class.java,
        FoodItemEntity::class.java,
        WorkoutLogEntity::class.java,
        ActivitySessionEntity::class.java,
        HealthLogEntity::class.java
    )

    private val sensitiveDaoNames = listOf(
        "FoodDao",
        "FoodEntryDao",
        "FoodItemDao",
        "WorkoutDao",
        "ActivitySessionDao",
        "HealthDao"
    )

    @Test
    fun everySensitiveEntityCarriesAnOpaqueOwner() {
        ownerScopedEntities.forEach { entity ->
            assertTrue(
                "${entity.simpleName} must persist ownerId",
                entity.declaredFields.any { it.name == "ownerId" && it.type == String::class.java }
            )
        }
    }

    @Test
    fun everySensitiveDaoQueryAndMutationIsOwnerScoped() {
        val daoSource = source("src/main/java/com/example/data/local/Daos.kt")
        sensitiveDaoNames.forEach { daoName ->
            val daoBlock = daoSource
                .substringAfter("interface $daoName {")
                .substringBefore("\n}")
            val queries = Regex("@Query\\(\"([^\"]+)\"\\)")
                .findAll(daoBlock)
                .map { match -> match.groupValues[1] }
                .toList()
            assertTrue("$daoName must declare owner-scoped queries", queries.isNotEmpty())
            queries.forEach { query ->
                assertTrue(
                    "$daoName query is not filtered by ownerId: $query",
                    query.contains("ownerId", ignoreCase = true)
                )
            }
            assertFalse("$daoName uses an unscoped @Delete", daoBlock.contains("@Delete"))
            assertFalse("$daoName uses an unscoped @Update", daoBlock.contains("@Update"))
        }

        val dailyMissionBlock = daoSource
            .substringAfter("interface DailyMissionDao {")
            .substringBefore("\n}")
        Regex("@Query\\(\"([^\"]+)\"\\)")
            .findAll(dailyMissionBlock)
            .map { match -> match.groupValues[1] }
            .filter { query -> query.startsWith("UPDATE", ignoreCase = true) }
            .forEach { query ->
                assertTrue("Daily mission writes must include userId", query.contains("userId", ignoreCase = true))
            }
    }

    @Test
    fun roomMigrationAssignsUnownedRowsOnlyToTheControlledLegacyOwner() {
        val databaseSource = source("src/main/java/com/example/data/local/AppDatabase.kt")
        assertTrue(databaseSource.contains("version = 12"))
        assertTrue(databaseSource.contains("MIGRATION_11_12"))
        assertTrue(databaseSource.contains("legacy_owner_v1"))
        val migrationSource = databaseSource
            .substringAfter("MIGRATION_11_12")
            .substringBefore("private fun addColumnIfMissing")
        listOf(
            "food_logs",
            "food_entries",
            "food_items",
            "workout_logs",
            "activity_sessions",
            "health_logs"
        ).forEach { table ->
            assertTrue("Migration must owner-scope $table", migrationSource.contains("`$table`"))
        }
        assertFalse(databaseSource.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun roomMigrationRekeysTheLegacyProfileAndEveryExistingUserScopedProgressTable() {
        val databaseSource = source("src/main/java/com/example/data/local/AppDatabase.kt")
        val migrationSource = databaseSource
            .substringAfter("MIGRATION_11_12")
            .substringBefore("private fun addColumnIfMissing")

        assertTrue(
            "Migration must preserve the legacy profile under the controlled owner",
            migrationSource.contains("UPDATE `users` SET `uid` = 'legacy_owner_v1' WHERE `uid` = 'local_user'")
        )
        listOf("daily_activity", "streaks", "daily_missions", "achievements").forEach { table ->
            assertTrue(
                "Migration must include $table in the legacy rekey set",
                migrationSource.contains("\"$table\"")
            )
        }
        assertTrue(
            "Migration must rekey every listed progress table from local_user",
            migrationSource.contains(
                "UPDATE `\$table` SET `userId` = 'legacy_owner_v1' WHERE `userId` = 'local_user'"
            )
        )
    }

    @Test
    fun legacyLocalUserDefaultsAreNotAcceptedAsAnOwner() {
        val daoSource = source("src/main/java/com/example/data/local/Daos.kt")
        val entitySource = source("src/main/java/com/example/data/model/Entities.kt")
        assertFalse(daoSource.contains("\"local_user\""))
        assertFalse(entitySource.contains("@PrimaryKey val uid: String = \"local_user\""))
    }

    private fun source(path: String): String {
        val file = File(path)
        assertTrue("Missing source file: ${file.absolutePath}", file.isFile)
        return file.readText()
    }
}
