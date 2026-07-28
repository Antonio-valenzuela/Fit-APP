package com.example.ui.catalog

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExerciseCatalogBootstrapper
import com.example.data.model.BuiltInExerciseEquipmentProfiles
import com.example.data.model.ExerciseBodyRegion
import com.example.data.model.ExerciseCatalogFilter
import com.example.data.model.ExerciseCatalogItemView
import com.example.data.model.ExerciseCatalogLocationFilter
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.ExerciseLocationCompatibility
import com.example.data.model.Muscle
import com.example.data.repository.ExerciseCatalogRepository
import com.example.ui.workout.TrainingCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseCatalogUiItem(
    val catalogId: String,
    val name: String,
    val instructions: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val bodyRegion: String,
    val equipment: String,
    val equipmentRequirements: List<String>,
    val locationCompatibility: String,
    val hasSpanishTranslation: Boolean
)

data class ExerciseCatalogUiState(
    val query: String = "",
    val location: ExerciseCatalogLocationFilter = ExerciseCatalogLocationFilter.ANY,
    val muscle: Muscle? = null,
    val bodyRegion: ExerciseBodyRegion? = null,
    val equipment: ExerciseEquipmentType? = null,
    val availableEquipment: Set<ExerciseEquipmentType> = emptySet(),
    val items: List<ExerciseCatalogUiItem> = emptyList(),
    val detail: ExerciseCatalogUiItem? = null,
    val isLoading: Boolean = true,
    val usingFallback: Boolean = false,
    val fallbackReason: String? = null
)

@HiltViewModel
class ExerciseCatalogViewModel @Inject constructor(
    private val repository: ExerciseCatalogRepository,
    private val bootstrapper: ExerciseCatalogBootstrapper,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(ExerciseCatalogUiState())
    val state: StateFlow<ExerciseCatalogUiState> = _state.asStateFlow()

    private val roomBootstrap = viewModelScope.async(start = CoroutineStart.LAZY) {
        bootstrapper.importIfNeeded(context)
    }
    private var initialLocationApplied = false
    private var loadJob: Job? = null

    init {
        reload()
    }

    fun applyInitialTrainingPlace(place: String) {
        if (initialLocationApplied) return
        initialLocationApplied = true
        val location = when (place.lowercase(Locale.ROOT)) {
            "casa" -> ExerciseCatalogLocationFilter.HOME
            "gimnasio" -> ExerciseCatalogLocationFilter.GYM
            else -> ExerciseCatalogLocationFilter.ANY
        }
        _state.update { it.copy(location = location) }
        reload()
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
        reload()
    }

    fun selectLocation(location: ExerciseCatalogLocationFilter) {
        _state.update { it.copy(location = location) }
        reload()
    }

    fun selectMuscle(muscle: Muscle?) {
        _state.update { it.copy(muscle = muscle) }
        reload()
    }

    fun selectBodyRegion(bodyRegion: ExerciseBodyRegion?) {
        _state.update { it.copy(bodyRegion = bodyRegion) }
        reload()
    }

    fun selectEquipment(equipment: ExerciseEquipmentType?) {
        _state.update { it.copy(equipment = equipment) }
        reload()
    }

    fun clearFilters() {
        _state.update {
            it.copy(
                location = ExerciseCatalogLocationFilter.ANY,
                muscle = null,
                bodyRegion = null,
                equipment = null,
                availableEquipment = emptySet()
            )
        }
        reload()
    }

    fun clearEquipment() {
        _state.update { it.copy(availableEquipment = emptySet()) }
        reload()
    }

    fun toggleEquipment(equipment: ExerciseEquipmentType) {
        _state.update { current ->
            val next = current.availableEquipment.toMutableSet().apply {
                if (!add(equipment)) remove(equipment)
            }
            current.copy(availableEquipment = next)
        }
        reload()
    }

    fun loadDetail(catalogId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = runCatching {
                ensureRoomReady()
                repository.getDetail(catalogId) ?: error("Catalog item not found")
            }.onFailure { if (it is CancellationException) throw it }
            result.fold(
                onSuccess = { detail ->
                    _state.update {
                        it.copy(
                            detail = detail.toUiItem(),
                            isLoading = false,
                            usingFallback = false,
                            fallbackReason = null
                        )
                    }
                },
                onFailure = { error ->
                    val fallback = TrainingCatalog.exerciseById(catalogId)?.toUiItem()
                    recordFallback(error)
                    _state.update { it.copy(detail = fallback, isLoading = false) }
                }
            )
        }
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val snapshot = _state.value
            _state.update { it.copy(isLoading = true) }
            val result = runCatching {
                ensureRoomReady()
                repository.browse(
                    filter = ExerciseCatalogFilter(
                        text = snapshot.query,
                        muscle = snapshot.muscle,
                        bodyRegion = snapshot.bodyRegion,
                        equipment = snapshot.equipment,
                        location = snapshot.location,
                        availableEquipment = snapshot.availableEquipment
                    ),
                    limit = EXERCISE_CATALOG_UI_LIMIT
                )
            }.onFailure { if (it is CancellationException) throw it }
            result.fold(
                onSuccess = { rows ->
                    _state.update {
                        it.copy(
                            items = rows.map { it.toUiItem() },
                            isLoading = false,
                            usingFallback = false,
                            fallbackReason = null
                        )
                    }
                },
                onFailure = { error ->
                    recordFallback(error)
                    _state.update {
                        it.copy(
                            items = fallbackItems(snapshot),
                            isLoading = false
                        )
                    }
                }
            )
        }
    }

    private suspend fun ensureRoomReady() {
        roomBootstrap.await()
    }

    private fun recordFallback(error: Throwable) {
        val reason = error::class.java.simpleName.ifBlank { "CatalogUnavailable" }
        Log.w("ExerciseCatalog", "Room catalog unavailable; using TrainingCatalog fallback ($reason)")
        _state.update { it.copy(usingFallback = true, fallbackReason = reason) }
    }

    private fun fallbackItems(snapshot: ExerciseCatalogUiState): List<ExerciseCatalogUiItem> {
        return TrainingCatalog.exercises.asSequence()
            .filter { exercise -> TrainingCatalogFallbackPolicy.matches(exercise, snapshot) }
            .map { it.toUiItem() }
            .take(40)
            .toList()
    }

    private fun ExerciseCatalogItemView.toUiItem() = ExerciseCatalogUiItem(
        catalogId = catalogId,
        name = name,
        instructions = instructions,
        primaryMuscle = primaryMuscle.displayName(),
        secondaryMuscles = secondaryMuscles.map { it.displayName() },
        bodyRegion = bodyRegion.displayName(),
        equipment = primaryEquipmentType.displayName(),
        equipmentRequirements = equipmentRequirements.map { it.displayName() },
        locationCompatibility = locationCompatibility.displayName(),
        hasSpanishTranslation = hasSpanishTranslation
    )

    private fun com.example.ui.workout.ExerciseCatalogItem.toUiItem() = ExerciseCatalogUiItem(
        catalogId = id,
        name = name,
        instructions = instructions,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = secondaryMuscles,
        bodyRegion = TrainingCatalogFallbackPolicy.bodyRegionFor(primaryMuscle).displayName(),
        equipment = equipment,
        equipmentRequirements = TrainingCatalogFallbackPolicy.detailEquipmentRequirements(this),
        locationCompatibility = TrainingCatalogFallbackPolicy.locationDescription(this),
        hasSpanishTranslation = true
    )

    private fun ExerciseLocationCompatibility.displayName(): String = when (this) {
        ExerciseLocationCompatibility.HOME_COMPATIBLE -> "Casa"
        ExerciseLocationCompatibility.GYM_COMPATIBLE -> "Gimnasio"
        ExerciseLocationCompatibility.BOTH -> "Casa y gimnasio"
    }

    private fun Enum<*>.displayName(): String = name
        .lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    private companion object {
        const val EXERCISE_CATALOG_UI_LIMIT = 2_000
    }

}

internal object TrainingCatalogFallbackPolicy {
    fun matches(
        exercise: com.example.ui.workout.ExerciseCatalogItem,
        state: ExerciseCatalogUiState
    ): Boolean {
        val query = normalizeCatalogText(state.query)
        val searchableNames = sequenceOf(exercise.name) + FREQUENT_ALIASES_BY_ID.orEmpty(exercise.id)
        if (query.isNotBlank() && searchableNames.none { normalizeCatalogText(it).contains(query) }) {
            return false
        }

        val primaryMuscle = Muscle.fromLegacyName(exercise.primaryMuscle)
        val secondaryMuscles = exercise.secondaryMuscles.mapNotNull(Muscle::fromLegacyName)
        if (state.muscle != null && state.muscle != primaryMuscle && state.muscle !in secondaryMuscles) {
            return false
        }
        if (state.bodyRegion != null && state.bodyRegion != bodyRegionFor(exercise.primaryMuscle)) {
            return false
        }

        val equipmentTypes = equipmentTypesFor(exercise)
        if (state.equipment != null && state.equipment !in equipmentTypes) return false

        return when (state.location) {
            ExerciseCatalogLocationFilter.ANY,
            ExerciseCatalogLocationFilter.GYM -> true
            ExerciseCatalogLocationFilter.HOME ->
                BuiltInExerciseEquipmentProfiles.isHomeCompatible(
                    exercise.id,
                    state.availableEquipment
                )
        }
    }

    fun bodyRegionFor(primaryMuscle: String): ExerciseBodyRegion = when (Muscle.fromLegacyName(primaryMuscle)) {
        Muscle.CHEST -> ExerciseBodyRegion.CHEST
        Muscle.BACK, Muscle.TRAPS -> ExerciseBodyRegion.BACK
        Muscle.SHOULDERS -> ExerciseBodyRegion.SHOULDERS
        Muscle.BICEPS, Muscle.TRICEPS -> ExerciseBodyRegion.UPPER_ARMS
        Muscle.FOREARMS -> ExerciseBodyRegion.FOREARMS
        Muscle.ABS -> ExerciseBodyRegion.CORE
        Muscle.GLUTES, Muscle.QUADS, Muscle.HAMSTRINGS,
        Muscle.ADDUCTORS, Muscle.ABDUCTORS -> ExerciseBodyRegion.UPPER_LEGS
        Muscle.CALVES -> ExerciseBodyRegion.LOWER_LEGS
        Muscle.CARDIO, Muscle.FULL_BODY, null -> ExerciseBodyRegion.CARDIO
    }

    fun detailEquipmentRequirements(
        exercise: com.example.ui.workout.ExerciseCatalogItem
    ): List<String> {
        val types = equipmentTypesFor(exercise)
        return if (types.all { it == ExerciseEquipmentType.BODY_WEIGHT }) {
            emptyList()
        } else {
            listOf(exercise.equipment)
        }
    }

    fun locationDescription(
        exercise: com.example.ui.workout.ExerciseCatalogItem
    ): String = if (ExerciseEquipmentType.BODY_WEIGHT in equipmentTypesFor(exercise)) {
        "Casa y gimnasio"
    } else {
        "Casa equipada o gimnasio"
    }

    private fun equipmentTypesFor(
        exercise: com.example.ui.workout.ExerciseCatalogItem
    ): Set<ExerciseEquipmentType> = BuiltInExerciseEquipmentProfiles.getValue(exercise.id).let { profile ->
        profile.anyOf + profile.allOf
    }

    private fun Map<String, List<String>>.orEmpty(key: String): Sequence<String> =
        get(key).orEmpty().asSequence()

    private val FREQUENT_ALIASES_BY_ID = mapOf(
        "bench_press" to listOf("press de banca"),
        "romanian_deadlift" to listOf("peso muerto"),
        "squat" to listOf("sentadilla"),
        "push_up" to listOf("flexiones"),
        "lateral_raise" to listOf("elevaciones laterales")
    )
}

private fun normalizeCatalogText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
