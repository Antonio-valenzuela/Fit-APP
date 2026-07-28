package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    fun getUserFlow(uid: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUser(uid: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users WHERE uid = :uid")
    suspend fun deleteUser(uid: String)
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_logs WHERE ownerId = :ownerId ORDER BY timestamp DESC")
    fun getFoodLogsFlow(ownerId: String): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs WHERE ownerId = :ownerId AND timestamp >= :startDay AND timestamp <= :endDay ORDER BY timestamp DESC")
    fun getFoodLogsForDayFlow(ownerId: String, startDay: Long, endDay: Long): Flow<List<FoodLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodLog(log: FoodLogEntity)

    @Query("DELETE FROM food_logs WHERE ownerId = :ownerId AND id = :id")
    suspend fun deleteFoodLog(ownerId: String, id: Int)

    @Query("DELETE FROM food_logs WHERE ownerId = :ownerId")
    suspend fun clearAll(ownerId: String)
}

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entries WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun getFoodEntriesFlow(ownerId: String): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE ownerId = :ownerId AND dateKey = :dateKey ORDER BY createdAt DESC")
    fun getFoodEntriesForDateFlow(ownerId: String, dateKey: String): Flow<List<FoodEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodEntry(entry: FoodEntryEntity): Long

    @Query("DELETE FROM food_entries WHERE ownerId = :ownerId AND id = :id")
    suspend fun deleteFoodEntry(ownerId: String, id: Long)

    @Query("DELETE FROM food_entries WHERE ownerId = :ownerId")
    suspend fun clearAll(ownerId: String)
}

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun getFoodItemsFlow(ownerId: String): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE ownerId = :ownerId AND isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteFoodItemsFlow(ownerId: String): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE ownerId = :ownerId AND barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(ownerId: String, barcode: String): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(item: FoodItemEntity): Long

    @Query("UPDATE food_items SET isFavorite = :favorite WHERE ownerId = :ownerId AND id = :id")
    suspend fun setFavorite(ownerId: String, id: Long, favorite: Boolean)

    @Query("DELETE FROM food_items WHERE ownerId = :ownerId")
    suspend fun clearAll(ownerId: String)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_logs WHERE ownerId = :ownerId ORDER BY timestamp DESC")
    fun getWorkoutLogsFlow(ownerId: String): Flow<List<WorkoutLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutLog(log: WorkoutLogEntity)

    @Query("DELETE FROM workout_logs WHERE ownerId = :ownerId AND id = :id")
    suspend fun deleteWorkoutLog(ownerId: String, id: Int)

    @Query("DELETE FROM workout_logs WHERE ownerId = :ownerId")
    suspend fun clearAll(ownerId: String)
}

@Dao
interface ActivitySessionDao {
    @Query("SELECT * FROM activity_sessions WHERE ownerId = :ownerId ORDER BY timestamp DESC")
    fun getActivitySessionsFlow(ownerId: String): Flow<List<ActivitySessionEntity>>

    @Query("SELECT * FROM activity_sessions WHERE ownerId = :ownerId AND discipline = :discipline ORDER BY timestamp DESC")
    fun getActivitySessionsForDisciplineFlow(ownerId: String, discipline: String): Flow<List<ActivitySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivitySession(session: ActivitySessionEntity)

    @Query("DELETE FROM activity_sessions WHERE ownerId = :ownerId AND id = :id")
    suspend fun deleteActivitySession(ownerId: String, id: Long)

    @Query("DELETE FROM activity_sessions WHERE ownerId = :ownerId")
    suspend fun clearAll(ownerId: String)
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_logs WHERE ownerId = :ownerId ORDER BY timestamp DESC")
    fun getHealthLogsFlow(ownerId: String): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs WHERE ownerId = :ownerId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHealthLogsFlow(ownerId: String, limit: Int): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs WHERE ownerId = :ownerId AND timestamp >= :startDay AND timestamp <= :endDay ORDER BY timestamp DESC")
    fun getHealthLogsForDayFlow(ownerId: String, startDay: Long, endDay: Long): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs WHERE ownerId = :ownerId AND dateKey = :dateKey ORDER BY timestamp DESC")
    fun getHealthLogsForDateKeyFlow(ownerId: String, dateKey: String): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs WHERE ownerId = :ownerId AND dateKey = :dateKey AND type = :type AND context = :context LIMIT 1")
    suspend fun getHealthLogForDateTypeContext(ownerId: String, dateKey: String, type: String, context: String): HealthLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthLog(log: HealthLogEntity)

    @Query("SELECT COUNT(*) FROM health_logs WHERE ownerId = :ownerId AND id = :id")
    suspend fun ownsHealthLog(ownerId: String, id: Int): Int

    @Transaction
    suspend fun updateHealthLog(ownerId: String, log: HealthLogEntity): Boolean {
        if (log.id == 0 || ownsHealthLog(ownerId, log.id) == 0) return false
        insertHealthLog(log.copy(ownerId = ownerId))
        return true
    }

    @Query("DELETE FROM health_logs WHERE ownerId = :ownerId AND id = :id")
    suspend fun deleteHealthLog(ownerId: String, id: Int)

    @Query("DELETE FROM health_logs WHERE ownerId = :ownerId")
    suspend fun clearAll(ownerId: String)
}

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM daily_activity WHERE userId = :userId AND date = :date LIMIT 1")
    suspend fun getActivityForDay(userId: String, date: String): DailyActivityEntity?

    @Query("SELECT * FROM daily_activity WHERE userId = :userId AND date = :date LIMIT 1")
    fun getActivityForDayFlow(userId: String, date: String): Flow<DailyActivityEntity?>

    @Query("SELECT * FROM daily_activity WHERE userId = :userId ORDER BY date DESC")
    fun getAllActivitiesFlow(userId: String): Flow<List<DailyActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: DailyActivityEntity)
}

@Dao
interface StreakDao {
    @Query("SELECT * FROM streaks WHERE userId = :userId LIMIT 1")
    suspend fun getStreak(userId: String): StreakEntity?

    @Query("SELECT * FROM streaks WHERE userId = :userId LIMIT 1")
    fun getStreakFlow(userId: String): Flow<StreakEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreak(streak: StreakEntity)
}

@Dao
interface DailyMissionDao {
    @Query("SELECT * FROM daily_missions WHERE userId = :userId AND date = :date")
    suspend fun getMissionsForDay(userId: String, date: String): List<DailyMissionEntity>

    @Query("SELECT * FROM daily_missions WHERE userId = :userId AND date = :date")
    fun getMissionsForDayFlow(userId: String, date: String): Flow<List<DailyMissionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMissions(missions: List<DailyMissionEntity>)

    @Query("UPDATE daily_missions SET completed = :completed, completedAt = :completedAt WHERE userId = :userId AND id = :id")
    suspend fun updateMissionStatus(userId: String, id: Long, completed: Boolean, completedAt: Long?)
    
    @Query("UPDATE daily_missions SET completed = :completed, completedAt = :completedAt WHERE userId = :userId AND date = :date AND missionKey = :missionKey")
    suspend fun updateMissionStatusByKey(userId: String, date: String, missionKey: String, completed: Boolean, completedAt: Long?)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE userId = :userId")
    fun getAchievementsFlow(userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE userId = :userId")
    suspend fun getAchievements(userId: String): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: AchievementEntity)
}

@Dao
interface AccountDataDao {
    @Query("DELETE FROM users WHERE uid = :ownerId")
    suspend fun deleteUser(ownerId: String)

    @Query("DELETE FROM food_logs WHERE ownerId = :ownerId")
    suspend fun deleteFoodLogs(ownerId: String)

    @Query("DELETE FROM food_entries WHERE ownerId = :ownerId")
    suspend fun deleteFoodEntries(ownerId: String)

    @Query("DELETE FROM food_items WHERE ownerId = :ownerId")
    suspend fun deleteFoodItems(ownerId: String)

    @Query("DELETE FROM workout_logs WHERE ownerId = :ownerId")
    suspend fun deleteWorkoutLogs(ownerId: String)

    @Query("DELETE FROM activity_sessions WHERE ownerId = :ownerId")
    suspend fun deleteActivitySessions(ownerId: String)

    @Query("DELETE FROM health_logs WHERE ownerId = :ownerId")
    suspend fun deleteHealthLogs(ownerId: String)

    @Query("DELETE FROM daily_activity WHERE userId = :ownerId")
    suspend fun deleteDailyActivity(ownerId: String)

    @Query("DELETE FROM streaks WHERE userId = :ownerId")
    suspend fun deleteStreaks(ownerId: String)

    @Query("DELETE FROM daily_missions WHERE userId = :ownerId")
    suspend fun deleteDailyMissions(ownerId: String)

    @Query("DELETE FROM achievements WHERE userId = :ownerId")
    suspend fun deleteAchievements(ownerId: String)

    @Transaction
    suspend fun deleteOwnerData(ownerId: String) {
        deleteUser(ownerId)
        deleteFoodLogs(ownerId)
        deleteFoodEntries(ownerId)
        deleteFoodItems(ownerId)
        deleteWorkoutLogs(ownerId)
        deleteActivitySessions(ownerId)
        deleteHealthLogs(ownerId)
        deleteDailyActivity(ownerId)
        deleteStreaks(ownerId)
        deleteDailyMissions(ownerId)
        deleteAchievements(ownerId)
    }
}
