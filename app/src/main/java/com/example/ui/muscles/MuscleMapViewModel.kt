package com.example.ui.muscles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BodyModel
import com.example.data.model.Muscle
import com.example.data.model.MuscleBalanceRules.ANTAGONIST_PAIRS
import com.example.data.model.MuscleImbalance
import com.example.data.model.MuscleIntensity
import com.example.data.model.MuscleRecommendation
import com.example.data.model.MuscleTrainingProfiles.ALL_MUSCLES
import com.example.data.model.MuscleTrainingProfiles.CARDIO_PROFILE
import com.example.data.model.MuscleTrainingProfiles.EXERCISE_MUSCLE_MAP
import com.example.data.model.MuscleTrainingProfiles.FUNCTIONAL_STRENGTH_PROFILE
import com.example.data.model.MuscleTrainingProfiles.GENERAL_TRAINING_PROFILE
import com.example.data.model.MuscleTrainingProfiles.MOBILITY_PROFILE
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.data.model.normalizeExerciseSearchText
import com.example.features.musclemap.domain.ExerciseNameCandidates
import com.example.features.musclemap.domain.ExerciseTextParser
import com.example.features.musclemap.domain.repository.CatalogMuscleMatch
import com.example.features.musclemap.domain.repository.MuscleMapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

data class MuscleMapUiState(
    val heatmap: Map<Muscle, MuscleIntensity> = emptyMap(),
    val imbalances: List<MuscleImbalance> = emptyList(),
    val recommendation: MuscleRecommendation? = null,
    val selectedBodyModel: BodyModel = BodyModel.MALE,
    val selectedMuscle: Muscle? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val hasWorkoutData: Boolean = false
)

@HiltViewModel
class MuscleMapViewModel @Inject constructor(
    private val repository: MuscleMapRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MuscleMapUiState())
    val state: StateFlow<MuscleMapUiState> = _state.asStateFlow()

    init {
        observeLocalTrainingData()
    }

    fun selectMuscle(muscle: Muscle?) {
        _state.value = _state.value.copy(selectedMuscle = muscle)
    }

    fun selectBodyModel(model: BodyModel) {
        _state.value = _state.value.copy(selectedBodyModel = model)
    }

    private fun observeLocalTrainingData() {
        viewModelScope.launch {
            combine(
                repository.getWorkoutLogsFlow(),
                repository.getUserFlow()
            ) { workouts, user ->
                buildUiState(workouts, user)
            }
            .catch { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Error al cargar datos del mapa muscular: ${e.message}"
                )
            }
            .collect { nextState ->
                _state.value = nextState.copy(
                    selectedMuscle = _state.value.selectedMuscle?.takeIf { nextState.heatmap.containsKey(it) }
                )
            }
        }
    }

    private suspend fun buildUiState(workouts: List<WorkoutLogEntity>, user: UserEntity?): MuscleMapUiState {
        val recentCutoff = System.currentTimeMillis() - DAYS_28_MS
        val recentWorkouts = workouts.filter { it.timestamp >= recentCutoff }
        val candidatesByWorkout = recentWorkouts.map { workout ->
            workout to ExerciseTextParser.parse(workout.exercisesText)
        }
        val candidateKeys = linkedSetOf<String>()
        candidatesByWorkout.forEach { (_, candidates) ->
            candidates.forEach { candidate -> candidateKeys.addAll(candidate.allKeys) }
        }
        val catalogMatches = repository.bulkResolveExerciseMuscles(candidateKeys)
        val volumeByMuscle = mutableMapOf<Muscle, Float>()
        val sessionsByMuscle = mutableMapOf<Muscle, MutableSet<Long>>()
        val lastTrainedByMuscle = mutableMapOf<Muscle, Long>()

        for ((workout, candidates) in candidatesByWorkout) {
            val parsed = parseWorkout(workout, candidates, catalogMatches)
            for ((muscle, activation) in parsed.muscles) {
                val adjusted = parsed.volume * activation
                volumeByMuscle[muscle] = (volumeByMuscle[muscle] ?: 0f) + adjusted
                sessionsByMuscle.getOrPut(muscle) { mutableSetOf() }.add(workout.timestamp)
                lastTrainedByMuscle[muscle] = maxOf(lastTrainedByMuscle[muscle] ?: 0L, workout.timestamp)
            }
        }

        val maxVolume = volumeByMuscle.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val dateFormat = SimpleDateFormat("dd MMM", Locale.forLanguageTag("es-MX"))
        val heatmap = volumeByMuscle.mapValues { (muscle, volume) ->
            MuscleIntensity(
                muscleGroup = muscle,
                intensity = ((volume / maxVolume) * 100).toInt().coerceIn(0, 100),
                lastTrained = lastTrainedByMuscle[muscle]?.let { dateFormat.format(Date(it)) },
                sessions = sessionsByMuscle[muscle]?.size ?: 0,
                totalVolume = volume
            )
        }

        val imbalances = detectImbalances(volumeByMuscle)
        val recommendation = recommendNextMuscle(volumeByMuscle, lastTrainedByMuscle, imbalances)

        return MuscleMapUiState(
            heatmap = heatmap,
            imbalances = imbalances,
            recommendation = recommendation,
            selectedBodyModel = bodyModelFromUser(user),
            isLoading = false,
            hasWorkoutData = workouts.isNotEmpty()
        )
    }

    private fun parseWorkout(
        workout: WorkoutLogEntity,
        candidates: List<ExerciseNameCandidates>,
        catalogMatches: Map<String, CatalogMuscleMatch>
    ): ParsedWorkout {
        val metricsText = normalize(workout.exercisesText)
        val sets = parseNumberAfterLabel(metricsText, "series")?.toInt()?.coerceAtLeast(1) ?: 3
        val reps = parseNumberAfterLabel(metricsText, "reps")?.toInt()?.coerceAtLeast(1) ?: 10
        val weight = parseNumberAfterLabel(metricsText, "peso")?.coerceAtLeast(1f) ?: 1f
        val rpe = parseNumberAfterLabel(metricsText, "rpe")?.coerceIn(1f, 10f) ?: 7f
        val baseVolume = (sets * reps * weight * (0.75f + rpe / 20f)).coerceAtLeast(workout.durationMinutes * 4f)

        val activationsByMuscle = linkedMapOf<Muscle, Float>()
        var hasExerciseMatch = false
        candidates.forEach { candidate ->
            val resolved = resolveCandidate(candidate, catalogMatches)
            hasExerciseMatch = hasExerciseMatch || resolved.matched
            resolved.activations.forEach { (muscle, activation) ->
                activationsByMuscle[muscle] = (activationsByMuscle[muscle] ?: 0f) + activation
            }
        }

        val muscles = if (hasExerciseMatch) {
            activationsByMuscle.entries
                .sortedBy { it.key.ordinal }
                .map { it.key to it.value }
        } else {
            val profileText = normalize("${workout.routineName} ${workout.exercisesText}")
            when {
                profileText.contains("cardio") -> CARDIO_PROFILE
                profileText.contains("movilidad") || profileText.contains("postura") -> MOBILITY_PROFILE
                profileText.contains("fuerza") -> FUNCTIONAL_STRENGTH_PROFILE
                else -> GENERAL_TRAINING_PROFILE
            }
        }

        return ParsedWorkout(volume = baseVolume, muscles = muscles)
    }

    private fun resolveCandidate(
        candidate: ExerciseNameCandidates,
        catalogMatches: Map<String, CatalogMuscleMatch>
    ): ResolvedCandidate {
        catalogMatches[candidate.whole]?.let { wholeMatch ->
            return ResolvedCandidate(matched = true, activations = wholeMatch.activations)
        }

        var matched = false
        val activations = mutableListOf<Pair<Muscle, Float>>()
        val fallbackKeys = candidate.commaFallbacks.ifEmpty { listOf(candidate.whole) }
        fallbackKeys.forEach { fallbackKey ->
            val catalogMatch = catalogMatches[fallbackKey]
            if (catalogMatch != null) {
                matched = true
                activations += catalogMatch.activations
            } else {
                val legacyMatch = findLegacyMatch(fallbackKey)
                if (legacyMatch != null) {
                    matched = true
                    activations += legacyMatch
                }
            }
        }
        return ResolvedCandidate(matched = matched, activations = activations)
    }

    private fun findLegacyMatch(candidateKey: String): List<Pair<Muscle, Float>>? {
        val paddedCandidate = " $candidateKey "
        return LEGACY_EXERCISE_MATCHES.firstOrNull { (key, _) ->
            paddedCandidate.contains(" $key ")
        }?.second
    }

    private fun detectImbalances(volumes: Map<Muscle, Float>): List<MuscleImbalance> {
        return ANTAGONIST_PAIRS.mapNotNull { pair ->
            val volA = volumes[pair.a] ?: 0f
            val volB = volumes[pair.b] ?: 0f
            if (volA == 0f && volB == 0f) return@mapNotNull null

            val ratio = if (volB > 0f) volA / volB else 99f
            val deviation = abs(ratio - pair.idealRatio) / pair.idealRatio
            if (deviation <= 0.30f) return@mapNotNull null

            val weak = if (ratio > pair.idealRatio) pair.b else pair.a
            val strong = if (ratio > pair.idealRatio) pair.a else pair.b
            val severity = if (deviation > 0.60f) "high" else "medium"
            MuscleImbalance(
                pair = pair.name,
                weakMuscle = weak,
                strongMuscle = strong,
                severity = severity,
                recommendation = "Agrega mas trabajo de ${weak.displayName} para balancear tu entrenamiento."
            )
        }.sortedByDescending { if (it.severity == "high") 1 else 0 }
    }

    private fun recommendNextMuscle(
        volumes: Map<Muscle, Float>,
        lastTrained: Map<Muscle, Long>,
        imbalances: List<MuscleImbalance>
    ): MuscleRecommendation {
        imbalances.firstOrNull()?.let { imbalance ->
            return MuscleRecommendation(
                muscle = imbalance.weakMuscle,
                reason = imbalance.recommendation,
                urgency = imbalance.severity
            )
        }

        val now = System.currentTimeMillis()
        val leastRecent = ALL_MUSCLES.maxByOrNull { muscle ->
            now - (lastTrained[muscle] ?: 0L)
        } ?: Muscle.BACK

        val reason = if (volumes.isEmpty()) {
            "Registra tu primer entrenamiento para que FitPlan calcule tu balance."
        } else {
            "Es el grupo con menos trabajo reciente en tu historial."
        }

        return MuscleRecommendation(leastRecent, reason, "low")
    }

    private fun bodyModelFromUser(user: UserEntity?): BodyModel {
        val gender = normalize(user?.gender.orEmpty())
        return if (gender.contains("femenino") || gender.contains("mujer")) BodyModel.FEMALE else BodyModel.MALE
    }

    private fun parseNumberAfterLabel(text: String, label: String): Float? {
        val regex = Regex("$label\\s*:?\\s*([0-9]+(?:[.,][0-9]+)?)")
        return regex.find(text)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull()
    }

    private fun normalize(value: String): String {
        val noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase(Locale.ROOT)
    }

    private data class ParsedWorkout(
        val volume: Float,
        val muscles: List<Pair<Muscle, Float>>
    )

    private data class ResolvedCandidate(
        val matched: Boolean,
        val activations: List<Pair<Muscle, Float>>
    )

    companion object {
        private const val DAYS_28_MS = 28L * 24L * 60L * 60L * 1000L
        private val LEGACY_EXERCISE_MATCHES = EXERCISE_MUSCLE_MAP.entries
            .map { (key, activations) -> normalizeExerciseSearchText(key) to activations }
            .sortedWith(compareByDescending<Pair<String, List<Pair<Muscle, Float>>>> { it.first.length }
                .thenBy { it.first })
    }
}
