package com.example.features.gym.domain.repository

import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow

interface GymRepository {
    fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>>
    fun getUserFlow(): Flow<UserEntity?>
    suspend fun addWorkoutLog(log: WorkoutLogEntity)
    suspend fun deleteWorkoutLog(log: WorkoutLogEntity)
}
