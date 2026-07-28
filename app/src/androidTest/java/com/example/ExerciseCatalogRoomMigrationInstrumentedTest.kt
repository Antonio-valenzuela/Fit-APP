package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.model.BuiltInExerciseEquipmentProfiles
import com.example.data.model.ExerciseBodyRegion
import com.example.data.model.ExerciseCatalogEntity
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.ExerciseLocationCompatibility
import com.example.data.model.Muscle
import com.example.data.model.ExerciseSearchContentEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogRoomMigrationInstrumentedTest {
    private val databaseName = "exercise_catalog_migration_test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration10To11PreservesSentinelRowsAndCreatesAllCatalogTables() {
        helper.createDatabase(databaseName, 10).apply {
            execSQL("INSERT INTO users(uid, name, email, age, weightKg, heightCm, gender, activityLevel, goal, targetCalories, targetProteinG, targetCarbsG, targetFatG, disclaimerAccepted, isPremium, trainingDiscipline) VALUES ('sentinel', 'S', 's@example.test', 30, 70.0, 170.0, 'x', 'Moderado', 'Mantener Peso', 2000, 140, 220, 65, 0, 0, 'GYM')")
            execSQL("INSERT INTO food_logs(id, mealName, mealType, calories, proteinG, carbsG, fatG, timestamp) VALUES (77, 'Sentinel', 'Comida', 1, 1, 1, 1, 123)")
            execSQL("INSERT INTO food_entries(id, dateKey, mealType, foodName, portionLabel, calories, proteinGrams, carbsGrams, fatGrams, source, notes, createdAt, updatedAt) VALUES (77, '2026-07-15', 'Comida', 'Sentinel', 'Plato', 1, 1, 1, 1, 'MANUAL', '', 123, 123)")
            execSQL("INSERT INTO food_items(id, name, caloriesPerServing, proteinGrams, carbsGrams, fatGrams, servingLabel, isFavorite, isCustom, createdAt) VALUES (77, 'Sentinel', 1, 1, 1, 1, 'Porción', 0, 1, 123)")
            execSQL("INSERT INTO workout_logs(id, routineName, durationMinutes, caloriesBurned, exercisesText, timestamp) VALUES (77, 'Sentinel', 1, 1, 'squat', 123)")
            execSQL("INSERT INTO activity_sessions(id, discipline, sportType, title, distanceKm, durationMinutes, caloriesBurned, routePointsJson, startedAt, endedAt, durationSeconds, averagePaceMinPerKm, averageSpeedKmh, source, notes, timestamp) VALUES (77, 'RUNNING', 'RUNNING', 'Sentinel', 0, 1, 1, '', 123, 123, 1, 1, 1, 'test', '', 123)")
            execSQL("INSERT INTO health_logs(id, type, value, secondaryValue, unit, context, note, dateKey, createdAt, updatedAt, pulse, isAutoWorkout, glucoseLevel, glucoseIsPreMeal, systolicPressure, diastolicPressure, waterIntakeMl, sleepDurationHours, sleepQualityScale, energyLevel, moodLevel, painFatigueLevel, painFatigueNote, steps, weightKg, timestamp) VALUES (77, 'TEST', 1, 1, 'u', 'c', '', '2026-07-15', 123, 123, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, '', 1, 1, 123)")
            execSQL("INSERT INTO daily_activity(id, userId, date, hasFoodLog, hasWaterLog, hasWorkoutLog, hasWeightLog, hasSleepLog, hasCheckIn, hasMissionCompleted, isActiveDay, createdAt, updatedAt) VALUES (77, 'sentinel', '2026-07-15', 1, 0, 0, 0, 0, 0, 0, 1, 123, 123)")
            execSQL("INSERT INTO streaks(userId, currentStreak, bestStreak, lastActiveDate, updatedAt) VALUES ('sentinel', 1, 1, '2026-07-15', 123)")
            execSQL("INSERT INTO daily_missions(id, userId, date, missionKey, title, description, completed, completedAt) VALUES (77, 'sentinel', '2026-07-15', 'test', 'Sentinel', '', 0, NULL)")
            execSQL("INSERT INTO achievements(id, userId, achievementKey, title, description, unlockedAt) VALUES (77, 'sentinel', 'test', 'Sentinel', '', 123)")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 11, true, AppDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT name FROM users WHERE uid = 'sentinel'").use { cursor -> assertTrue(cursor.moveToFirst()) }
            db.query("SELECT id FROM food_logs WHERE id = 77").use { cursor -> assertTrue(cursor.moveToFirst()) }
            listOf(
                "food_entries", "food_items", "workout_logs", "activity_sessions", "health_logs",
                "daily_activity", "streaks", "daily_missions", "achievements"
            ).forEach { table ->
                db.query("SELECT 1 FROM `$table` LIMIT 1").use { cursor -> assertTrue(table, cursor.moveToFirst()) }
            }
            val expectedTables = listOf(
                "users", "food_logs", "food_entries", "food_items", "workout_logs", "activity_sessions",
                "health_logs", "daily_activity", "streaks", "daily_missions", "achievements",
                "exercise_catalog", "exercise_source_mappings", "exercise_secondary_muscles",
                "exercise_equipment_requirements", "exercise_search_content", "exercise_catalog_fts",
                "exercise_catalog_imports"
            )
            expectedTables.forEach { table ->
                db.query("SELECT name FROM sqlite_master WHERE name = '$table'").use { cursor -> assertTrue(table, cursor.moveToFirst()) }
            }
            db.query("PRAGMA foreign_key_list(exercise_source_mappings)").use { cursor ->
                val onUpdate = cursor.getColumnIndexOrThrow("on_update")
                assertTrue(cursor.moveToFirst())
                assertEquals("NO ACTION", cursor.getString(onUpdate))
            }
            listOf(
                "index_exercise_catalog_catalogId",
                "index_exercise_catalog_primaryMuscle",
                "index_exercise_secondary_muscles_muscle",
                "index_exercise_equipment_requirements_equipmentType"
            ).forEach { index ->
                db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = '$index'").use { cursor -> assertTrue(index, cursor.moveToFirst()) }
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'room_fts_content_sync_exercise_catalog_fts%'").use { cursor ->
                var count = 0
                while (cursor.moveToNext()) count++
                assertEquals(4, count)
            }
        }
    }

    @Test
    fun searchIndexFollowsContentEntityForInsertUpdateDeleteAndRebuild() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_10_11)
            .build()
        try {
            val dao = db.exerciseCatalogDao()
            val rowId = dao.insertCatalog(
                ExerciseCatalogEntity(
                    catalogId = "test:press",
                    nameEs = "Press de prueba",
                    nameEn = null,
                    normalizedName = "press de prueba",
                    instructionsEs = "Instrucciones",
                    instructionsEn = null,
                    primaryMuscle = Muscle.CHEST,
                    bodyRegion = ExerciseBodyRegion.CHEST,
                    primaryEquipmentType = ExerciseEquipmentType.BODY_WEIGHT,
                    locationCompatibility = ExerciseLocationCompatibility.HOME_COMPATIBLE
                )
            )
            dao.insertSearchContent(ExerciseSearchContentEntity(rowId, "Press de prueba", null, "press de prueba", "press de banca"))
            val noBuiltInEquipmentMatches =
                listOf(BuiltInExerciseEquipmentProfiles.NO_EQUIPMENT_FILTER_MATCH_ID)
            assertEquals(1, dao.searchAny("press", noBuiltInEquipmentMatches, null, null, null, 10, 0).size)
            dao.updateSearchContent(rowId, "Sentadilla de prueba", null, "sentadilla de prueba", "sentadilla")
            assertEquals(0, dao.searchAny("press", noBuiltInEquipmentMatches, null, null, null, 10, 0).size)
            assertEquals(1, dao.searchAny("sentadilla", noBuiltInEquipmentMatches, null, null, null, 10, 0).size)
            dao.deleteSearchContent(rowId)
            assertEquals(0, dao.searchAny("sentadilla", noBuiltInEquipmentMatches, null, null, null, 10, 0).size)
            dao.insertSearchContent(ExerciseSearchContentEntity(rowId, "Press de prueba", null, "press de prueba", "press de banca"))
            dao.rebuildSearchIndex()
            assertEquals(1, dao.searchAny("press", noBuiltInEquipmentMatches, null, null, null, 10, 0).size)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }
}
