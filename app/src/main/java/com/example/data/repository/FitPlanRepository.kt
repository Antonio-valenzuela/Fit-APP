package com.example.data.repository

import com.example.data.local.FoodDao
import com.example.data.local.FoodEntryDao
import com.example.data.local.FoodItemDao
import com.example.data.local.HealthDao
import com.example.data.local.ActivitySessionDao
import com.example.data.local.UserDao
import com.example.data.local.WorkoutDao
import com.example.data.local.AccountDataDao
import com.example.data.model.ActivitySessionEntity
import com.example.data.model.FoodLogEntity
import com.example.data.model.FoodEntryEntity
import com.example.data.model.FoodItemEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.data.model.isValidOwnerId
import com.example.data.model.requireActiveOwnerForWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar

class FitPlanRepository(
    private val userDao: UserDao,
    private val foodDao: FoodDao,
    private val foodEntryDao: FoodEntryDao,
    private val foodItemDao: FoodItemDao,
    private val workoutDao: WorkoutDao,
    private val activitySessionDao: ActivitySessionDao,
    private val healthDao: HealthDao,
    private val activeOwnerId: StateFlow<String?>,
    private val accountDataDao: AccountDataDao
) {
    // User Operations
    val currentUser: Flow<UserEntity?> = activeOwnerId.flatMapLatest { ownerId ->
        if (ownerId == null) flowOf(null) else userDao.getUserFlow(ownerId)
    }

    suspend fun getUserDirectly(): UserEntity? {
        return ownerOrNull()?.let { ownerId -> userDao.getUser(ownerId) }
    }

    suspend fun saveUser(user: UserEntity) {
        val ownerId = requireActiveOwnerForWrite(
            activeOwnerId = activeOwnerId.value,
            entityOwnerId = user.uid,
            hasPersistentId = user.uid.isNotBlank()
        )
        userDao.insertUser(user.copy(uid = ownerId))
    }

    suspend fun deleteCurrentProfile() {
        ownerOrNull()?.let { ownerId -> userDao.deleteUser(ownerId) }
    }

    // Food Operations
    val allFoodLogs: Flow<List<FoodLogEntity>> = ownerListFlow(foodDao::getFoodLogsFlow)
    val allFoodEntries: Flow<List<FoodEntryEntity>> = ownerListFlow(foodEntryDao::getFoodEntriesFlow)
    val allFoodItems: Flow<List<FoodItemEntity>> = ownerListFlow(foodItemDao::getFoodItemsFlow)
    val favoriteFoodItems: Flow<List<FoodItemEntity>> = ownerListFlow(foodItemDao::getFavoriteFoodItemsFlow)

    fun getTodayFoodLogs(): Flow<List<FoodLogEntity>> {
        val (start, end) = getDayRange(System.currentTimeMillis())
        return activeOwnerId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList()) else foodDao.getFoodLogsForDayFlow(ownerId, start, end)
        }
    }

    fun getTodayFoodEntries(): Flow<List<FoodEntryEntity>> {
        val dateKey = LocalDateUtils.getTodayString()
        return activeOwnerId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList()) else foodEntryDao.getFoodEntriesForDateFlow(ownerId, dateKey)
        }
    }

    suspend fun addFoodLog(log: FoodLogEntity) {
        val ownerId = ownerForWrite(log.ownerId, log.id != 0)
        foodDao.insertFoodLog(log.copy(ownerId = ownerId))
    }

    suspend fun deleteFoodLog(log: FoodLogEntity) {
        ownerOrNull()?.let { ownerId -> foodDao.deleteFoodLog(ownerId, log.id) }
    }

    suspend fun addFoodEntry(entry: FoodEntryEntity) {
        val ownerId = ownerForWrite(entry.ownerId, entry.id != 0L)
        foodEntryDao.insertFoodEntry(entry.copy(ownerId = ownerId))
    }

    suspend fun deleteFoodEntry(entry: FoodEntryEntity) {
        ownerOrNull()?.let { ownerId -> foodEntryDao.deleteFoodEntry(ownerId, entry.id) }
    }

    suspend fun saveFoodItem(item: FoodItemEntity) {
        val ownerId = ownerForWrite(item.ownerId, item.id != 0L)
        foodItemDao.insertFoodItem(item.copy(ownerId = ownerId))
    }

    suspend fun setFoodItemFavorite(id: Long, favorite: Boolean) {
        ownerOrNull()?.let { ownerId -> foodItemDao.setFavorite(ownerId, id, favorite) }
    }

    suspend fun getFoodItemByBarcode(barcode: String): FoodItemEntity? {
        return ownerOrNull()?.let { ownerId -> foodItemDao.getByBarcode(ownerId, barcode) }
    }

    // Workout Operations
    val allWorkoutLogs: Flow<List<WorkoutLogEntity>> = ownerListFlow(workoutDao::getWorkoutLogsFlow)

    suspend fun addWorkoutLog(log: WorkoutLogEntity) {
        val ownerId = ownerForWrite(log.ownerId, log.id != 0)
        workoutDao.insertWorkoutLog(log.copy(ownerId = ownerId))
    }

    suspend fun deleteWorkoutLog(log: WorkoutLogEntity) {
        ownerOrNull()?.let { ownerId -> workoutDao.deleteWorkoutLog(ownerId, log.id) }
    }

    // Discipline activity operations
    val allActivitySessions: Flow<List<ActivitySessionEntity>> = ownerListFlow(activitySessionDao::getActivitySessionsFlow)

    fun getActivitySessionsForDiscipline(discipline: String): Flow<List<ActivitySessionEntity>> {
        return activeOwnerId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList())
            else activitySessionDao.getActivitySessionsForDisciplineFlow(ownerId, discipline)
        }
    }

    suspend fun addActivitySession(session: ActivitySessionEntity) {
        val ownerId = ownerForWrite(session.ownerId, session.id != 0L)
        activitySessionDao.insertActivitySession(session.copy(ownerId = ownerId))
    }

    suspend fun deleteActivitySession(session: ActivitySessionEntity) {
        ownerOrNull()?.let { ownerId -> activitySessionDao.deleteActivitySession(ownerId, session.id) }
    }

    // Health Operations
    val allHealthLogs: Flow<List<HealthLogEntity>> = ownerListFlow(healthDao::getHealthLogsFlow)

    fun getRecentHealthLogs(limit: Int): Flow<List<HealthLogEntity>> {
        return activeOwnerId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList())
            else healthDao.getRecentHealthLogsFlow(ownerId, limit.coerceIn(1, 100))
        }
    }

    fun getTodayHealthLogs(): Flow<List<HealthLogEntity>> {
        val (start, end) = getDayRange(System.currentTimeMillis())
        return activeOwnerId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList()) else healthDao.getHealthLogsForDayFlow(ownerId, start, end)
        }
    }

    fun getHealthLogsForDateKey(dateKey: String): Flow<List<HealthLogEntity>> {
        return activeOwnerId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList()) else healthDao.getHealthLogsForDateKeyFlow(ownerId, dateKey)
        }
    }

    suspend fun getHealthLogForDateTypeContext(dateKey: String, type: String, context: String): HealthLogEntity? {
        return ownerOrNull()?.let { ownerId ->
            healthDao.getHealthLogForDateTypeContext(ownerId, dateKey, type, context)
        }
    }

    suspend fun addHealthLog(log: HealthLogEntity) {
        val ownerId = ownerForWrite(log.ownerId, log.id != 0)
        healthDao.insertHealthLog(log.copy(ownerId = ownerId))
    }

    suspend fun updateHealthLog(log: HealthLogEntity) {
        val ownerId = ownerForWrite(log.ownerId, log.id != 0)
        healthDao.updateHealthLog(ownerId, log)
    }

    suspend fun deleteHealthLog(log: HealthLogEntity) {
        ownerOrNull()?.let { ownerId -> healthDao.deleteHealthLog(ownerId, log.id) }
    }

    // Account reset and full wipe to satisfy Google Play deletion compliance
    suspend fun wipeUserData() {
        val ownerId = ownerOrNull() ?: return
        accountDataDao.deleteOwnerData(ownerId)
    }

    private fun ownerOrNull(): String? = activeOwnerId.value?.takeIf(::isValidOwnerId)

    private fun ownerForWrite(entityOwnerId: String, hasPersistentId: Boolean): String =
        requireActiveOwnerForWrite(activeOwnerId.value, entityOwnerId, hasPersistentId)

    private fun <T> ownerListFlow(query: (String) -> Flow<List<T>>): Flow<List<T>> {
        return activeOwnerId.flatMapLatest { ownerId ->
            if (!isValidOwnerId(ownerId)) flowOf(emptyList()) else query(requireNotNull(ownerId))
        }
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
