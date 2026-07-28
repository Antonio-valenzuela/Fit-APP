package com.example.features.musclemap.domain.repository

import com.example.data.model.Muscle
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow

data class CatalogMuscleMatch(
    val catalogId: String,
    val activations: List<Pair<Muscle, Float>>
)

interface MuscleMapRepository {
    fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>>
    fun getUserFlow(): Flow<UserEntity?>
    suspend fun bulkResolveExerciseMuscles(keys: Set<String>): Map<String, CatalogMuscleMatch> = emptyMap()
}
