package com.example.features.musclemap.data.repository

import com.example.data.local.UserDao
import com.example.data.local.WorkoutDao
import com.example.data.local.PreferencesManager
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.features.musclemap.data.ExerciseCatalogMuscleResolver
import com.example.features.musclemap.domain.repository.CatalogMuscleMatch
import com.example.features.musclemap.domain.repository.MuscleMapRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class MuscleMapRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userDao: UserDao,
    private val catalogMuscleResolver: ExerciseCatalogMuscleResolver,
    private val preferences: PreferencesManager
) : MuscleMapRepository {
    override fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>> {
        val ownerId = preferences.getActiveOwnerId() ?: return flowOf(emptyList())
        return workoutDao.getWorkoutLogsFlow(ownerId)
    }

    override fun getUserFlow(): Flow<UserEntity?> {
        val ownerId = preferences.getActiveOwnerId() ?: return flowOf(null)
        return userDao.getUserFlow(ownerId)
    }
    override suspend fun bulkResolveExerciseMuscles(keys: Set<String>): Map<String, CatalogMuscleMatch> =
        catalogMuscleResolver.resolve(keys)
}
