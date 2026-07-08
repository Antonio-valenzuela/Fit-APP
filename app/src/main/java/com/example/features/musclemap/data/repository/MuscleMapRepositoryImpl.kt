package com.example.features.musclemap.data.repository

import com.example.data.local.UserDao
import com.example.data.local.WorkoutDao
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.features.musclemap.domain.repository.MuscleMapRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MuscleMapRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userDao: UserDao
) : MuscleMapRepository {
    override fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>> = workoutDao.getWorkoutLogsFlow()
    override fun getUserFlow(): Flow<UserEntity?> = userDao.getUserFlow()
}
