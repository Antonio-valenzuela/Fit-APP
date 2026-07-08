package com.example.features.gym.data.repository

import com.example.data.local.UserDao
import com.example.data.local.WorkoutDao
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.features.gym.domain.repository.GymRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GymRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userDao: UserDao
) : GymRepository {
    override fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>> = workoutDao.getWorkoutLogsFlow()
    override fun getUserFlow(): Flow<UserEntity?> = userDao.getUserFlow()
    override suspend fun addWorkoutLog(log: WorkoutLogEntity) {
        workoutDao.insertWorkoutLog(log)
    }
    override suspend fun deleteWorkoutLog(log: WorkoutLogEntity) {
        workoutDao.deleteWorkoutLog(log)
    }
}
