package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
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
    fun getUserFlow(uid: String = "local_user"): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUser(uid: String = "local_user"): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users WHERE uid = :uid")
    suspend fun deleteUser(uid: String = "local_user")
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_logs ORDER BY timestamp DESC")
    fun getFoodLogsFlow(): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs WHERE timestamp >= :startDay AND timestamp <= :endDay ORDER BY timestamp DESC")
    fun getFoodLogsForDayFlow(startDay: Long, endDay: Long): Flow<List<FoodLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodLog(log: FoodLogEntity)

    @Delete
    suspend fun deleteFoodLog(log: FoodLogEntity)

    @Query("DELETE FROM food_logs")
    suspend fun clearAll()
}

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entries ORDER BY createdAt DESC")
    fun getFoodEntriesFlow(): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE dateKey = :dateKey ORDER BY createdAt DESC")
    fun getFoodEntriesForDateFlow(dateKey: String): Flow<List<FoodEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodEntry(entry: FoodEntryEntity): Long

    @Delete
    suspend fun deleteFoodEntry(entry: FoodEntryEntity)

    @Query("DELETE FROM food_entries")
    suspend fun clearAll()
}

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items ORDER BY createdAt DESC")
    fun getFoodItemsFlow(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteFoodItemsFlow(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(item: FoodItemEntity): Long

    @Query("UPDATE food_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM food_items")
    suspend fun clearAll()
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_logs ORDER BY timestamp DESC")
    fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutLog(log: WorkoutLogEntity)

    @Delete
    suspend fun deleteWorkoutLog(log: WorkoutLogEntity)

    @Query("DELETE FROM workout_logs")
    suspend fun clearAll()
}

@Dao
interface ActivitySessionDao {
    @Query("SELECT * FROM activity_sessions ORDER BY timestamp DESC")
    fun getActivitySessionsFlow(): Flow<List<ActivitySessionEntity>>

    @Query("SELECT * FROM activity_sessions WHERE discipline = :discipline ORDER BY timestamp DESC")
    fun getActivitySessionsForDisciplineFlow(discipline: String): Flow<List<ActivitySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivitySession(session: ActivitySessionEntity)

    @Delete
    suspend fun deleteActivitySession(session: ActivitySessionEntity)

    @Query("DELETE FROM activity_sessions")
    suspend fun clearAll()
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_logs ORDER BY timestamp DESC")
    fun getHealthLogsFlow(): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHealthLogsFlow(limit: Int): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs WHERE timestamp >= :startDay AND timestamp <= :endDay ORDER BY timestamp DESC")
    fun getHealthLogsForDayFlow(startDay: Long, endDay: Long): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs WHERE dateKey = :dateKey ORDER BY timestamp DESC")
    fun getHealthLogsForDateKeyFlow(dateKey: String): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs WHERE dateKey = :dateKey AND type = :type AND context = :context LIMIT 1")
    suspend fun getHealthLogForDateTypeContext(dateKey: String, type: String, context: String): HealthLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthLog(log: HealthLogEntity)

    @Update
    suspend fun updateHealthLog(log: HealthLogEntity)

    @Delete
    suspend fun deleteHealthLog(log: HealthLogEntity)

    @Query("DELETE FROM health_logs")
    suspend fun clearAll()
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

    @Query("UPDATE daily_missions SET completed = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun updateMissionStatus(id: Long, completed: Boolean, completedAt: Long?)
    
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
