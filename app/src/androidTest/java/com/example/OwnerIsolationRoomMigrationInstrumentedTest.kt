package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.model.LEGACY_OWNER_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerIsolationRoomMigrationInstrumentedTest {
    private val databaseName = "owner_isolation_migration_test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration11To12PreservesSensitiveRowsAndScopesThemToLegacyOwner() {
        helper.createDatabase(databaseName, 11).apply {
            insertSensitiveSentinels(this)
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            12,
            true,
            AppDatabase.MIGRATION_11_12
        ).use { db ->
            listOf(
                "food_logs",
                "food_entries",
                "food_items",
                "workout_logs",
                "activity_sessions",
                "health_logs"
            ).forEach { table ->
                db.query("SELECT ownerId FROM `$table` WHERE id = 77").use { cursor ->
                    assertTrue("$table sentinel row must survive", cursor.moveToFirst())
                    assertEquals("$table owner", LEGACY_OWNER_ID, cursor.getString(0))
                }
            }

            db.query("SELECT name, email, weightKg FROM users WHERE uid = ?", arrayOf(LEGACY_OWNER_ID)).use { cursor ->
                assertTrue("Legacy profile must survive", cursor.moveToFirst())
                assertEquals("Legacy Person", cursor.getString(0))
                assertEquals("legacy@example.test", cursor.getString(1))
                assertEquals(72f, cursor.getFloat(2))
            }
            listOf("daily_activity", "streaks", "daily_missions", "achievements").forEach { table ->
                db.query("SELECT userId FROM `$table` WHERE userId = ?", arrayOf(LEGACY_OWNER_ID)).use { cursor ->
                    assertTrue("$table progress must survive", cursor.moveToFirst())
                    assertEquals(LEGACY_OWNER_ID, cursor.getString(0))
                }
                db.query("SELECT userId FROM `$table` WHERE userId = 'local_user'").use { cursor ->
                    assertTrue("$table must not retain local_user", !cursor.moveToFirst())
                }
            }

            listOf(
                "index_food_logs_ownerId_timestamp",
                "index_food_entries_ownerId_dateKey",
                "index_food_entries_ownerId_mealType",
                "index_food_entries_ownerId_barcode",
                "index_food_items_ownerId_barcode",
                "index_food_items_ownerId_isFavorite",
                "index_food_items_ownerId_name",
                "index_workout_logs_ownerId_timestamp",
                "index_activity_sessions_ownerId_discipline",
                "index_activity_sessions_ownerId_timestamp",
                "index_health_logs_ownerId_timestamp",
                "index_health_logs_ownerId_dateKey"
            ).forEach { index ->
                db.query(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
                    arrayOf(index)
                ).use { cursor -> assertTrue("Missing $index", cursor.moveToFirst()) }
            }
        }
    }

    private fun insertSensitiveSentinels(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO users(uid, name, email, age, weightKg, heightCm, gender, activityLevel, goal, targetCalories, targetProteinG, targetCarbsG, targetFatG, disclaimerAccepted, isPremium, trainingDiscipline) VALUES ('local_user', 'Legacy Person', 'legacy@example.test', 30, 72, 170, 'Otro', 'Moderado', 'Salud', 2000, 120, 220, 65, 1, 0, 'GYM')")
        db.execSQL("INSERT INTO food_logs(id, mealName, mealType, calories, proteinG, carbsG, fatG, timestamp) VALUES (77, 'Sentinel', 'Comida', 1, 1, 1, 1, 123)")
        db.execSQL("INSERT INTO food_entries(id, dateKey, mealType, foodName, portionLabel, calories, proteinGrams, carbsGrams, fatGrams, source, notes, createdAt, updatedAt) VALUES (77, '2026-07-15', 'Comida', 'Sentinel', 'Plato', 1, 1, 1, 1, 'MANUAL', '', 123, 123)")
        db.execSQL("INSERT INTO food_items(id, name, caloriesPerServing, proteinGrams, carbsGrams, fatGrams, servingLabel, isFavorite, isCustom, createdAt) VALUES (77, 'Sentinel', 1, 1, 1, 1, 'Porción', 0, 1, 123)")
        db.execSQL("INSERT INTO workout_logs(id, routineName, durationMinutes, caloriesBurned, exercisesText, timestamp) VALUES (77, 'Sentinel', 1, 1, 'squat', 123)")
        db.execSQL("INSERT INTO activity_sessions(id, discipline, sportType, title, distanceKm, durationMinutes, caloriesBurned, routePointsJson, startedAt, endedAt, durationSeconds, averagePaceMinPerKm, averageSpeedKmh, source, notes, timestamp) VALUES (77, 'RUNNING', 'RUNNING', 'Sentinel', 0, 1, 1, '', 123, 123, 1, 1, 1, 'test', '', 123)")
        db.execSQL("INSERT INTO health_logs(id, type, value, secondaryValue, unit, context, note, dateKey, createdAt, updatedAt, pulse, isAutoWorkout, glucoseLevel, glucoseIsPreMeal, systolicPressure, diastolicPressure, waterIntakeMl, sleepDurationHours, sleepQualityScale, energyLevel, moodLevel, painFatigueLevel, painFatigueNote, steps, weightKg, timestamp) VALUES (77, 'TEST', 1, 1, 'u', 'c', '', '2026-07-15', 123, 123, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, '', 1, 1, 123)")
        db.execSQL("INSERT INTO daily_activity(id, userId, date, hasFoodLog, hasWaterLog, hasWorkoutLog, hasWeightLog, hasSleepLog, hasCheckIn, hasMissionCompleted, isActiveDay, createdAt, updatedAt) VALUES (77, 'local_user', '2026-07-15', 1, 1, 1, 1, 1, 1, 1, 1, 123, 123)")
        db.execSQL("INSERT INTO streaks(userId, currentStreak, bestStreak, lastActiveDate, updatedAt) VALUES ('local_user', 7, 9, '2026-07-15', 123)")
        db.execSQL("INSERT INTO daily_missions(id, userId, date, missionKey, title, description, completed, completedAt) VALUES (77, 'local_user', '2026-07-15', 'legacy', 'Legacy', 'Legacy', 1, 123)")
        db.execSQL("INSERT INTO achievements(id, userId, achievementKey, title, description, unlockedAt) VALUES (77, 'local_user', 'legacy', 'Legacy', 'Legacy', 123)")
    }
}
