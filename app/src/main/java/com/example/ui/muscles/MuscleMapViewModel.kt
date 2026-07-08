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

    private fun buildUiState(workouts: List<WorkoutLogEntity>, user: UserEntity?): MuscleMapUiState {
        val recentCutoff = System.currentTimeMillis() - DAYS_28_MS
        val recentWorkouts = workouts.filter { it.timestamp >= recentCutoff }
        val volumeByMuscle = mutableMapOf<Muscle, Float>()
        val sessionsByMuscle = mutableMapOf<Muscle, MutableSet<Long>>()
        val lastTrainedByMuscle = mutableMapOf<Muscle, Long>()

        for (workout in recentWorkouts) {
            val parsed = parseWorkout(workout)
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

    private fun parseWorkout(workout: WorkoutLogEntity): ParsedWorkout {
        val text = normalize("${workout.routineName} ${workout.exercisesText}")
        val sets = parseNumberAfterLabel(text, "series")?.toInt()?.coerceAtLeast(1) ?: 3
        val reps = parseNumberAfterLabel(text, "reps")?.toInt()?.coerceAtLeast(1) ?: 10
        val weight = parseNumberAfterLabel(text, "peso")?.coerceAtLeast(1f) ?: 1f
        val rpe = parseNumberAfterLabel(text, "rpe")?.coerceIn(1f, 10f) ?: 7f
        val baseVolume = (sets * reps * weight * (0.75f + rpe / 20f)).coerceAtLeast(workout.durationMinutes * 4f)

        val directMatches = EXERCISE_MUSCLE_MAP.filterKeys { exercise ->
            text.contains(normalize(exercise))
        }

        val muscles = when {
            directMatches.isNotEmpty() -> directMatches.values.flatten()
            text.contains("cardio") -> CARDIO_PROFILE
            text.contains("movilidad") || text.contains("postura") -> MOBILITY_PROFILE
            text.contains("fuerza") -> FUNCTIONAL_STRENGTH_PROFILE
            else -> GENERAL_TRAINING_PROFILE
        }

        return ParsedWorkout(volume = baseVolume, muscles = muscles)
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

    companion object {
        private const val DAYS_28_MS = 28L * 24L * 60L * 60L * 1000L
    }
}
