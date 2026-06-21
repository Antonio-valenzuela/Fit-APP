package com.example.data.repository

import com.example.data.local.FoodDao
import com.example.data.local.FoodEntryDao
import com.example.data.local.FoodItemDao
import com.example.data.local.HealthDao
import com.example.data.local.ActivitySessionDao
import com.example.data.local.UserDao
import com.example.data.local.WorkoutDao
import com.example.data.model.ActivitySessionEntity
import com.example.data.model.FoodLogEntity
import com.example.data.model.FoodEntryEntity
import com.example.data.model.FoodItemEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class FitPlanRepository(
    private val userDao: UserDao,
    private val foodDao: FoodDao,
    private val foodEntryDao: FoodEntryDao,
    private val foodItemDao: FoodItemDao,
    private val workoutDao: WorkoutDao,
    private val activitySessionDao: ActivitySessionDao,
    private val healthDao: HealthDao
) {
    // User Operations
    val currentUser: Flow<UserEntity?> = userDao.getUserFlow()

    suspend fun getUserDirectly(): UserEntity? {
        return userDao.getUser()
    }

    suspend fun saveUser(user: UserEntity) {
        userDao.insertUser(user)
    }

    suspend fun deleteCurrentProfile() {
        userDao.deleteUser()
    }

    // Food Operations
    val allFoodLogs: Flow<List<FoodLogEntity>> = foodDao.getFoodLogsFlow()
    val allFoodEntries: Flow<List<FoodEntryEntity>> = foodEntryDao.getFoodEntriesFlow()
    val allFoodItems: Flow<List<FoodItemEntity>> = foodItemDao.getFoodItemsFlow()
    val favoriteFoodItems: Flow<List<FoodItemEntity>> = foodItemDao.getFavoriteFoodItemsFlow()

    fun getTodayFoodLogs(): Flow<List<FoodLogEntity>> {
        val (start, end) = getDayRange(System.currentTimeMillis())
        return foodDao.getFoodLogsForDayFlow(start, end)
    }

    fun getTodayFoodEntries(): Flow<List<FoodEntryEntity>> {
        return foodEntryDao.getFoodEntriesForDateFlow(LocalDateUtils.getTodayString())
    }

    suspend fun addFoodLog(log: FoodLogEntity) {
        foodDao.insertFoodLog(log)
    }

    suspend fun deleteFoodLog(log: FoodLogEntity) {
        foodDao.deleteFoodLog(log)
    }

    suspend fun addFoodEntry(entry: FoodEntryEntity) {
        foodEntryDao.insertFoodEntry(entry)
    }

    suspend fun deleteFoodEntry(entry: FoodEntryEntity) {
        foodEntryDao.deleteFoodEntry(entry)
    }

    suspend fun saveFoodItem(item: FoodItemEntity) {
        foodItemDao.insertFoodItem(item)
    }

    suspend fun setFoodItemFavorite(id: Long, favorite: Boolean) {
        foodItemDao.setFavorite(id, favorite)
    }

    suspend fun getFoodItemByBarcode(barcode: String): FoodItemEntity? {
        return foodItemDao.getByBarcode(barcode)
    }

    // Workout Operations
    val allWorkoutLogs: Flow<List<WorkoutLogEntity>> = workoutDao.getWorkoutLogsFlow()

    suspend fun addWorkoutLog(log: WorkoutLogEntity) {
        workoutDao.insertWorkoutLog(log)
    }

    suspend fun deleteWorkoutLog(log: WorkoutLogEntity) {
        workoutDao.deleteWorkoutLog(log)
    }

    // Discipline activity operations
    val allActivitySessions: Flow<List<ActivitySessionEntity>> = activitySessionDao.getActivitySessionsFlow()

    fun getActivitySessionsForDiscipline(discipline: String): Flow<List<ActivitySessionEntity>> {
        return activitySessionDao.getActivitySessionsForDisciplineFlow(discipline)
    }

    suspend fun addActivitySession(session: ActivitySessionEntity) {
        activitySessionDao.insertActivitySession(session)
    }

    suspend fun deleteActivitySession(session: ActivitySessionEntity) {
        activitySessionDao.deleteActivitySession(session)
    }

    // Health Operations
    val allHealthLogs: Flow<List<HealthLogEntity>> = healthDao.getHealthLogsFlow()

    fun getRecentHealthLogs(limit: Int): Flow<List<HealthLogEntity>> {
        return healthDao.getRecentHealthLogsFlow(limit.coerceIn(1, 100))
    }

    fun getTodayHealthLogs(): Flow<List<HealthLogEntity>> {
        val (start, end) = getDayRange(System.currentTimeMillis())
        return healthDao.getHealthLogsForDayFlow(start, end)
    }

    fun getHealthLogsForDateKey(dateKey: String): Flow<List<HealthLogEntity>> {
        return healthDao.getHealthLogsForDateKeyFlow(dateKey)
    }

    suspend fun getHealthLogForDateTypeContext(dateKey: String, type: String, context: String): HealthLogEntity? {
        return healthDao.getHealthLogForDateTypeContext(dateKey, type, context)
    }

    suspend fun addHealthLog(log: HealthLogEntity) {
        healthDao.insertHealthLog(log)
    }

    suspend fun updateHealthLog(log: HealthLogEntity) {
        healthDao.updateHealthLog(log)
    }

    suspend fun deleteHealthLog(log: HealthLogEntity) {
        healthDao.deleteHealthLog(log)
    }

    // Account reset and full wipe to satisfy Google Play deletion compliance
    suspend fun wipeUserData() {
        userDao.deleteUser()
        foodDao.clearAll()
        foodEntryDao.clearAll()
        foodItemDao.clearAll()
        workoutDao.clearAll()
        activitySessionDao.clearAll()
        healthDao.clearAll()
    }

    // Helper for computing daily epoch boundaries
    private fun getDayRange(timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val end = calendar.timeInMillis
        return Pair(start, end)
    }
}
