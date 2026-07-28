package com.example.features.gym.data.repository

import com.example.data.local.UserDao
import com.example.data.local.WorkoutDao
import com.example.data.local.PreferencesManager
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.data.model.requireActiveOwnerForWrite
import com.example.features.gym.domain.repository.GymRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class GymRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userDao: UserDao,
    private val preferences: PreferencesManager
) : GymRepository {
    override fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>> {
        val ownerId = preferences.getActiveOwnerId() ?: return flowOf(emptyList())
        return workoutDao.getWorkoutLogsFlow(ownerId)
    }

    override fun getUserFlow(): Flow<UserEntity?> {
        val ownerId = preferences.getActiveOwnerId() ?: return flowOf(null)
        return userDao.getUserFlow(ownerId)
    }

    override suspend fun addWorkoutLog(log: WorkoutLogEntity) {
        val ownerId = requireActiveOwnerForWrite(
            activeOwnerId = preferences.getActiveOwnerId(),
            entityOwnerId = log.ownerId,
            hasPersistentId = log.id != 0
        )
        workoutDao.insertWorkoutLog(log.copy(ownerId = ownerId))
    }

    override suspend fun deleteWorkoutLog(log: WorkoutLogEntity) {
        val ownerId = preferences.getActiveOwnerId() ?: return
        workoutDao.deleteWorkoutLog(ownerId, log.id)
    }
}
