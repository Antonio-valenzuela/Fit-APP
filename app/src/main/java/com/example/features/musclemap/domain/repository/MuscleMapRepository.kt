package com.example.features.musclemap.domain.repository

import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow

interface MuscleMapRepository {
    fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>>
    fun getUserFlow(): Flow<UserEntity?>
}
