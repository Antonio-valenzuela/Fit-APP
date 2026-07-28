package com.example.features.musclemap.data

import android.content.Context
import com.example.data.local.ExerciseCatalogBootstrapper
import com.example.data.local.ExerciseCatalogDao
import com.example.data.model.ExerciseCatalogAliasManifest
import com.example.data.model.Muscle
import com.example.data.model.normalizeExerciseSearchText
import com.example.features.musclemap.domain.repository.CatalogMuscleMatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseCatalogMuscleResolver @Inject constructor(
    private val dao: ExerciseCatalogDao,
    private val bootstrapper: ExerciseCatalogBootstrapper,
    @ApplicationContext private val context: Context
) {
    private val cacheMutex = Mutex()

    @Volatile
    private var cachedIndex: Map<String, CatalogMuscleMatch>? = null

    suspend fun resolve(keys: Set<String>): Map<String, CatalogMuscleMatch> {
        val normalizedKeys = keys.asSequence()
            .map(::normalizeExerciseSearchText)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
        if (normalizedKeys.isEmpty()) return emptyMap()

        val index = try {
            getOrLoadIndex()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return emptyMap()
        }

        return buildMap {
            normalizedKeys.forEach { key ->
                index[key]?.let { match -> put(key, match) }
            }
        }
    }

    private suspend fun getOrLoadIndex(): Map<String, CatalogMuscleMatch> {
        cachedIndex?.let { return it }
        return cacheMutex.withLock {
            cachedIndex ?: withContext(Dispatchers.IO) {
                loadIndex()
            }.also { loaded -> cachedIndex = loaded }
        }
    }

    private suspend fun loadIndex(): Map<String, CatalogMuscleMatch> {
        bootstrapper.importIfNeeded(context)

        val catalogRows = dao.getAllCatalogMuscles()
            .sortedWith(compareBy({ it.normalizedName }, { it.catalogId }))
        val secondaryByCatalogId = dao.getAllSecondaryMuscles()
            .groupBy { it.catalogId }

        val matchesByCatalogId = catalogRows.associate { row ->
            val primary = Muscle.valueOf(row.primaryMuscle)
            val activations = buildList {
                if (primary != Muscle.CARDIO && primary != Muscle.FULL_BODY) {
                    add(primary to PRIMARY_ACTIVATION)
                }
                secondaryByCatalogId[row.catalogId].orEmpty()
                    .asSequence()
                    .map { it.muscle }
                    .filter { it != primary }
                    .distinct()
                    .sortedBy { it.name }
                    .forEach { secondary -> add(secondary to SECONDARY_ACTIVATION) }
            }
            row.catalogId to CatalogMuscleMatch(
                catalogId = row.catalogId,
                activations = activations
            )
        }

        val candidatesByKey = linkedMapOf<String, MutableMap<String, CatalogMuscleMatch>>()
        fun register(rawKey: String, catalogId: String) {
            val key = normalizeExerciseSearchText(rawKey)
            val match = matchesByCatalogId[catalogId]
            if (key.isBlank() || match == null) return
            candidatesByKey.getOrPut(key) { linkedMapOf() }[catalogId] = match
        }

        catalogRows.forEach { row -> register(row.normalizedName, row.catalogId) }
        ExerciseCatalogAliasManifest.default.entries
            .sortedWith(compareBy({ normalizeExerciseSearchText(it.key) }, { it.value }))
            .forEach { (alias, catalogId) -> register(alias, catalogId) }

        return candidatesByKey.toSortedMap().mapNotNull { (key, matches) ->
            matches.values.singleOrNull()?.let { key to it }
        }.toMap()
    }

    private companion object {
        const val PRIMARY_ACTIVATION = 1.0f
        const val SECONDARY_ACTIVATION = 0.5f
    }
}
