package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.FoodLogEntity
import com.example.data.model.FoodEntryEntity
import com.example.data.model.FoodItemEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.ActivitySessionEntity
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity

import com.example.data.model.DailyActivityEntity
import com.example.data.model.StreakEntity
import com.example.data.model.DailyMissionEntity
import com.example.data.model.AchievementEntity

@Database(
    entities = [
        UserEntity::class,
        FoodLogEntity::class,
        FoodEntryEntity::class,
        FoodItemEntity::class,
        WorkoutLogEntity::class,
        ActivitySessionEntity::class,
        HealthLogEntity::class,
        DailyActivityEntity::class,
        StreakEntity::class,
        DailyMissionEntity::class,
        AchievementEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun foodDao(): FoodDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun foodItemDao(): FoodItemDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun activitySessionDao(): ActivitySessionDao
    abstract fun healthDao(): HealthDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun streakDao(): StreakDao
    abstract fun dailyMissionDao(): DailyMissionDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitplan_mx_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_logs_timestamp` ON `food_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_logs_timestamp` ON `workout_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_logs_timestamp` ON `health_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_activity_userId_date` ON `daily_activity` (`userId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_missions_userId_date` ON `daily_missions` (`userId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_missions_userId_date_missionKey` ON `daily_missions` (`userId`, `date`, `missionKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_achievements_userId` ON `achievements` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_achievements_userId_achievementKey` ON `achievements` (`userId`, `achievementKey`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "health_logs", "energyLevel", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "health_logs", "moodLevel", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "health_logs", "painFatigueLevel", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "health_logs", "painFatigueNote", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "health_logs", "steps", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "health_logs", "type", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "health_logs", "value", "REAL NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "health_logs", "secondaryValue", "REAL NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "health_logs", "unit", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "health_logs", "context", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "health_logs", "note", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "health_logs", "dateKey", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "health_logs", "createdAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "health_logs", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "health_logs", "pulse", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "health_logs", "isAutoWorkout", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_logs_dateKey` ON `health_logs` (`dateKey`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "users", "trainingDiscipline", "TEXT NOT NULL DEFAULT 'GYM'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `discipline` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `distanceKm` REAL NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `caloriesBurned` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_sessions_discipline` ON `activity_sessions` (`discipline`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_sessions_timestamp` ON `activity_sessions` (`timestamp`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "activity_sessions", "sportType", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "activity_sessions", "routePointsJson", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "activity_sessions", "startedAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "activity_sessions", "endedAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "activity_sessions", "durationSeconds", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "activity_sessions", "averagePaceMinPerKm", "REAL NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "activity_sessions", "averageSpeedKmh", "REAL NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "activity_sessions", "source", "TEXT NOT NULL DEFAULT 'local_manual'")
                db.execSQL("UPDATE `activity_sessions` SET `sportType` = `discipline` WHERE `sportType` = ''")
                db.execSQL("UPDATE `activity_sessions` SET `durationSeconds` = `durationMinutes` * 60 WHERE `durationSeconds` = 0")
                db.execSQL("UPDATE `activity_sessions` SET `startedAt` = `timestamp` WHERE `startedAt` = 0")
                db.execSQL("UPDATE `activity_sessions` SET `endedAt` = `timestamp` WHERE `endedAt` = 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateKey` TEXT NOT NULL,
                        `mealType` TEXT NOT NULL,
                        `foodName` TEXT NOT NULL,
                        `portionLabel` TEXT NOT NULL,
                        `calories` INTEGER NOT NULL,
                        `proteinGrams` REAL NOT NULL,
                        `carbsGrams` REAL NOT NULL,
                        `fatGrams` REAL NOT NULL,
                        `saturatedFatGrams` REAL,
                        `fiberGrams` REAL,
                        `sugarGrams` REAL,
                        `sodiumMg` REAL,
                        `source` TEXT NOT NULL,
                        `barcode` TEXT,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_dateKey` ON `food_entries` (`dateKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_mealType` ON `food_entries` (`mealType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_barcode` ON `food_entries` (`barcode`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `brand` TEXT,
                        `barcode` TEXT,
                        `caloriesPerServing` INTEGER NOT NULL,
                        `proteinGrams` REAL NOT NULL,
                        `carbsGrams` REAL NOT NULL,
                        `fatGrams` REAL NOT NULL,
                        `saturatedFatGrams` REAL,
                        `fiberGrams` REAL,
                        `sugarGrams` REAL,
                        `sodiumMg` REAL,
                        `servingLabel` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `isCustom` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_items_barcode` ON `food_items` (`barcode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_isFavorite` ON `food_items` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_name` ON `food_items` (`name`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "food_entries", "saturatedFatGrams", "REAL")
                addColumnIfMissing(db, "food_items", "saturatedFatGrams", "REAL")
                addColumnIfMissing(db, "food_items", "fiberGrams", "REAL")
                addColumnIfMissing(db, "food_items", "sugarGrams", "REAL")
                addColumnIfMissing(db, "food_items", "sodiumMg", "REAL")
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            definition: String
        ) {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == columnName) {
                        return
                    }
                }
            }
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $definition")
        }
    }
}
