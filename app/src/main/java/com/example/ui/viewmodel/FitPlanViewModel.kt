package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.FoodImageEstimate
import com.example.data.analytics.ProductAnalytics
import com.example.data.api.GeminiService
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.ActivitySessionEntity
import com.example.data.model.ActivityTrackingRules
import com.example.data.model.FoodDiaryRules
import com.example.data.model.FoodEntryDraft
import com.example.data.model.FoodEntryEntity
import com.example.data.model.FoodItemEntity
import com.example.data.model.FoodValidationResult
import com.example.data.model.FoodLogEntity
import com.example.data.model.GpsRoutePoint
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserDiscipline
import com.example.data.model.TodayWellnessSummary
import com.example.data.model.WellnessEntryDraft
import com.example.data.model.WellnessEntryRules
import com.example.data.model.WellnessEntryType
import com.example.data.model.WorkoutLogEntity
import com.example.data.repository.LocalDateUtils
import com.example.data.model.FeatureSettings
import com.example.data.model.HybridPlanSettings
import com.example.data.model.AppPerformanceMode
import com.example.data.model.PerformanceUserPreferences
import com.example.data.model.VisualPerformanceProfile
import com.example.data.repository.FitPlanRepository
import com.example.data.repository.PerformanceSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

class FitPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = FitPlanRepository(
        db.userDao(),
        db.foodDao(),
        db.foodEntryDao(),
        db.foodItemDao(),
        db.workoutDao(),
        db.activitySessionDao(),
        db.healthDao()
    )
    private val prefs = PreferencesManager(application)
    private val performanceRepository = PerformanceSettingsRepository(application)
    private val geminiService = GeminiService()

    // Preferences states
    private val _isLoggedIn = MutableStateFlow(prefs.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(prefs.isOnboardingCompleted())
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isPremiumActive = MutableStateFlow(prefs.isPremiumUnlocked())
    val isPremiumActive: StateFlow<Boolean> = _isPremiumActive.asStateFlow()

    private val _useMetric = MutableStateFlow(prefs.useMetricSettings())
    val useMetric: StateFlow<Boolean> = _useMetric.asStateFlow()

    private val _defaultGlassMl = MutableStateFlow(prefs.getDefaultGlassMl())
    val defaultGlassMl: StateFlow<Int> = _defaultGlassMl.asStateFlow()

    private val _hybridPlanSettings = MutableStateFlow(prefs.getHybridPlanSettings())
    val hybridPlanSettings: StateFlow<HybridPlanSettings> = _hybridPlanSettings.asStateFlow()

    private val _themeSelector = MutableStateFlow(prefs.getThemeSelector())
    val themeSelector: StateFlow<String> = _themeSelector.asStateFlow()

    private val _featureSettings = MutableStateFlow(prefs.getFeatureSettings())
    val featureSettings: StateFlow<FeatureSettings> = _featureSettings.asStateFlow()

    private val _performancePreferences = MutableStateFlow(performanceRepository.getPreferences())
    val performancePreferences: StateFlow<PerformanceUserPreferences> = _performancePreferences.asStateFlow()

    private val _visualPerformanceProfile = MutableStateFlow(performanceRepository.buildProfile())
    val visualPerformanceProfile: StateFlow<VisualPerformanceProfile> = _visualPerformanceProfile.asStateFlow()

    // Room Database states
    val userProfile: StateFlow<UserEntity?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayFoodLogs: StateFlow<List<FoodLogEntity>> = repository.getTodayFoodLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFoodLogs: StateFlow<List<FoodLogEntity>> = repository.allFoodLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayFoodEntries: StateFlow<List<FoodEntryEntity>> = repository.getTodayFoodEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentFoodEntries: StateFlow<List<FoodEntryEntity>> = repository.allFoodEntries
        .map { it.take(200) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFoodItems: StateFlow<List<FoodItemEntity>> = repository.allFoodItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteFoodItems: StateFlow<List<FoodItemEntity>> = repository.favoriteFoodItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayHealthLogs: StateFlow<List<HealthLogEntity>> = repository.getTodayHealthLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkoutLogs: StateFlow<List<WorkoutLogEntity>> = repository.allWorkoutLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allActivitySessions: StateFlow<List<ActivitySessionEntity>> = repository.allActivitySessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHealthLogs: StateFlow<List<HealthLogEntity>> = repository.allHealthLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getRecentHealthLogs(limit: Int): Flow<List<HealthLogEntity>> {
        return repository.getRecentHealthLogs(limit)
    }

    fun observeTodayEntries(): StateFlow<List<HealthLogEntity>> = todayHealthLogs

    fun getTodayWellnessSummary(): TodayWellnessSummary {
        return WellnessEntryRules.buildTodaySummary(todayHealthLogs.value, hasWorkoutToday())
    }

    fun recalculateDailySummary(): TodayWellnessSummary = getTodayWellnessSummary()

    // AI Advice States
    private val _preventiveAlerts = MutableStateFlow<String?>(null)
    val preventiveAlerts: StateFlow<String?> = _preventiveAlerts.asStateFlow()

    private val _alertsLoading = MutableStateFlow(false)
    val alertsLoading: StateFlow<Boolean> = _alertsLoading.asStateFlow()

    private val _habitsAdvice = MutableStateFlow<String?>(null)
    val habitsAdvice: StateFlow<String?> = _habitsAdvice.asStateFlow()

    private val _habitsLoading = MutableStateFlow(false)
    val habitsLoading: StateFlow<Boolean> = _habitsLoading.asStateFlow()

    private val _fitCoreAdvice = MutableStateFlow<String?>(null)
    val fitCoreAdvice: StateFlow<String?> = _fitCoreAdvice.asStateFlow()

    private val _fitCoreLoading = MutableStateFlow(false)
    val fitCoreLoading: StateFlow<Boolean> = _fitCoreLoading.asStateFlow()

    init {
        // Preload user data if logged in but empty in DB
        viewModelScope.launch {
            if (_isLoggedIn.value && repository.getUserDirectly() == null) {
                repository.saveUser(UserEntity(
                    uid = "local_user",
                    name = "Usuario FitPlan",
                    email = prefs.getUserEmail() ?: "user@fitplan.mx",
                    disclaimerAccepted = true
                ))
            }
        }
    }

    // Authentication Actions
    fun loginUser(email: String, name: String) {
        viewModelScope.launch {
            prefs.setLoggedIn(true, email)
            _isLoggedIn.value = true

            // Setup local profile skeleton
            val existing = repository.getUserDirectly()
            if (existing == null) {
                repository.saveUser(UserEntity(
                    uid = "local_user",
                    name = name,
                    email = email
                ))
            } else {
                repository.saveUser(existing.copy(name = name, email = email))
            }
        }
    }

    fun logoutUser() {
        prefs.setLoggedIn(false)
        prefs.setOnboardingCompleted(false)
        _isLoggedIn.value = false
        _isOnboardingCompleted.value = false
    }

    fun restartOnboarding() {
        prefs.setOnboardingCompleted(false)
        _isOnboardingCompleted.value = false
    }

    // Onboarding setup with Harris-Benedict metabolic equation
    fun completeOnboarding(
        name: String,
        age: Int,
        weightKg: Float,
        heightCm: Float,
        gender: String,
        activityLevel: String,
        goal: String,
        disclaimerAccepted: Boolean,
        trainingDiscipline: String = UserDiscipline.GYM.name
    ) {
        viewModelScope.launch {
            // Formula Harris-Benedict (BMR Estimation)
            val bmr = if (gender == "Masculino") {
                88.362f + (13.397f * weightKg) + (4.799f * heightCm) - (5.677f * age)
            } else {
                447.593f + (9.247f * weightKg) + (3.098f * heightCm) - (4.330f * age)
            }

            // Activity level multipliers
            val multiplier = when (activityLevel) {
                "Sedentario" -> 1.2f
                "Ligero" -> 1.375f
                "Moderado" -> 1.55f
                "Activo" -> 1.725f
                else -> 1.2f
            }

            val maintenanceCalories = bmr * multiplier

            // Targets calculation based on Goals
            val normalizedGoal = goal.lowercase()
            val targetCalories = when {
                normalizedGoal.contains("perder") || normalizedGoal.contains("bajar") || normalizedGoal.contains("grasa") -> (maintenanceCalories - 500f).coerceAtLeast(1200f).toInt()
                normalizedGoal.contains("ganar") -> (maintenanceCalories + 400f).toInt()
                else -> maintenanceCalories.toInt() // "Mantener Peso"
            }

            // Standard macro-nutrient breakdown policies
            val targetProtein = when {
                normalizedGoal.contains("perder") || normalizedGoal.contains("bajar") || normalizedGoal.contains("grasa") -> (weightKg * 2.2f).toInt().coerceIn(60, 250)
                normalizedGoal.contains("ganar") -> (weightKg * 2.0f).toInt().coerceIn(60, 250)
                else -> (weightKg * 1.6f).toInt().coerceIn(50, 200)
            }

            val targetFat = when {
                normalizedGoal.contains("perder") || normalizedGoal.contains("bajar") || normalizedGoal.contains("grasa") -> (weightKg * 0.8f).toInt().coerceIn(30, 100)
                normalizedGoal.contains("ganar") -> (weightKg * 1.0f).toInt().coerceIn(35, 120)
                else -> (weightKg * 0.9f).toInt().coerceIn(30, 110)
            }

            // Carbs are remainder: Calories = (P*4) + (F*9) + (C*4) -> C = (Calories - P*4 - F*9) / 4
            val deficitCaloriesFromCarbs = targetCalories - (targetProtein * 4) - (targetFat * 9)
            val targetCarbs = (deficitCaloriesFromCarbs / 4).coerceAtLeast(50)

            val updatedUser = UserEntity(
                name = name,
                email = prefs.getUserEmail() ?: "user@fitplan.mx",
                age = age,
                weightKg = weightKg,
                heightCm = heightCm,
                gender = gender,
                activityLevel = activityLevel,
                goal = goal,
                targetCalories = targetCalories,
                targetProteinG = targetProtein,
                targetCarbsG = targetCarbs,
                targetFatG = targetFat,
                disclaimerAccepted = disclaimerAccepted,
                isPremium = prefs.isPremiumUnlocked(),
                trainingDiscipline = UserDiscipline.fromStoredName(trainingDiscipline).name
            )

            repository.saveUser(updatedUser)
            prefs.setOnboardingCompleted(true)
            _isOnboardingCompleted.value = true
            ProductAnalytics.track(
                "plan_created",
                mapOf("goal" to goal.take(60), "activity_level" to activityLevel)
            )
        }
    }

    // Nutrition Logging
    fun addMealLog(name: String, cals: Int, proteins: Float, carbs: Float, fats: Float, type: String) {
        viewModelScope.launch {
            repository.addFoodLog(
                FoodLogEntity(
                    mealName = name,
                    mealType = type,
                    calories = cals,
                    proteinG = proteins,
                    carbsG = carbs,
                    fatG = fats
                )
            )
            triggerGamificationHook("log_food")
            ProductAnalytics.track("nutrition_logged", mapOf("meal_type" to type))
        }
    }

    fun addFoodEntry(draft: FoodEntryDraft, saveAsFavorite: Boolean = false): FoodValidationResult {
        val normalizedDraft = draft.copy(dateKey = draft.dateKey.ifBlank { LocalDateUtils.getTodayString() })
        val validation = FoodDiaryRules.validate(normalizedDraft)
        if (!validation.isValid) return validation
        val entry = FoodDiaryRules.toFoodEntry(normalizedDraft)
        viewModelScope.launch {
            repository.addFoodEntry(entry)
            repository.addFoodLog(
                FoodLogEntity(
                    mealName = "${entry.foodName} (${entry.portionLabel})",
                    mealType = entry.mealType,
                    calories = entry.calories,
                    proteinG = entry.proteinGrams,
                    carbsG = entry.carbsGrams,
                    fatG = entry.fatGrams,
                    timestamp = entry.createdAt
                )
            )
            repository.saveFoodItem(FoodDiaryRules.toFoodItem(entry, favorite = saveAsFavorite))
            triggerGamificationHook("log_food")
            ProductAnalytics.track(
                "nutrition_logged",
                mapOf("meal_type" to entry.mealType, "source" to entry.source)
            )
        }
        return validation
    }

    fun removeFoodEntry(entry: FoodEntryEntity) {
        viewModelScope.launch {
            repository.deleteFoodEntry(entry)
        }
    }

    suspend fun findFoodByBarcode(barcode: String): FoodItemEntity? {
        return repository.getFoodItemByBarcode(barcode.trim())
    }

    fun saveFoodItemAsFavorite(item: FoodItemEntity, favorite: Boolean = true) {
        viewModelScope.launch {
            if (item.id > 0) {
                repository.setFoodItemFavorite(item.id, favorite)
            } else {
                repository.saveFoodItem(item.copy(isFavorite = favorite))
            }
        }
    }

    fun saveFoodItem(item: FoodItemEntity) {
        viewModelScope.launch {
            repository.saveFoodItem(item)
            ProductAnalytics.track("food_item_saved", mapOf("source" to if (item.barcode.isNullOrBlank()) "manual_or_ocr" else "barcode"))
        }
    }

    suspend fun estimateMealFromPhoto(imageBytes: ByteArray, mimeType: String = "image/jpeg"): Result<FoodImageEstimate> {
        return geminiService.estimateFoodFromImage(imageBytes, mimeType)
    }

    fun removeMealLog(log: FoodLogEntity) {
        viewModelScope.launch {
            repository.deleteFoodLog(log)
        }
    }

    // Exercises / Routine tracking
    fun registerWorkout(name: String, duration: Int, calories: Int, repsExercises: String) {
        viewModelScope.launch {
            repository.addWorkoutLog(
                WorkoutLogEntity(
                    routineName = name,
                    durationMinutes = duration,
                    caloriesBurned = calories,
                    exercisesText = repsExercises
                )
            )
            markWorkoutCompletedFromTrainingSession()
            triggerGamificationHook("log_workout")
            ProductAnalytics.track("workout_completed", mapOf("routine" to name, "duration" to duration.toString()))
        }
    }

    fun removeWorkoutLog(log: WorkoutLogEntity) {
        viewModelScope.launch {
            repository.deleteWorkoutLog(log)
        }
    }

    fun updateTrainingDiscipline(discipline: UserDiscipline) {
        viewModelScope.launch {
            val current = repository.getUserDirectly()
            if (current != null) {
                repository.saveUser(current.copy(trainingDiscipline = discipline.name))
            } else {
                repository.saveUser(
                    UserEntity(
                        uid = "local_user",
                        name = "Usuario FitPlan",
                        email = prefs.getUserEmail() ?: "user@fitplan.mx",
                        trainingDiscipline = discipline.name
                    )
                )
            }
            ProductAnalytics.track("discipline_updated", mapOf("discipline" to discipline.name))
        }
    }

    fun updatePrimaryGoal(goal: String) {
        viewModelScope.launch {
            val current = repository.getUserDirectly() ?: UserEntity(
                uid = "local_user",
                name = "Usuario FitPlan",
                email = prefs.getUserEmail() ?: "user@fitplan.mx"
            )
            repository.saveUser(current.copy(goal = goal))
            ProductAnalytics.track("goal_updated", mapOf("goal" to goal.take(60)))
        }
    }

    fun addActivitySession(
        discipline: UserDiscipline,
        title: String,
        distanceKm: Float,
        durationMinutes: Int,
        caloriesBurned: Int,
        notes: String = ""
    ) {
        viewModelScope.launch {
            repository.addActivitySession(
                ActivitySessionEntity(
                    discipline = discipline.name,
                    sportType = discipline.name,
                    title = title,
                    distanceKm = distanceKm.coerceAtLeast(0f),
                    durationMinutes = durationMinutes.coerceAtLeast(0),
                    caloriesBurned = caloriesBurned.coerceAtLeast(0),
                    startedAt = System.currentTimeMillis(),
                    endedAt = System.currentTimeMillis(),
                    durationSeconds = durationMinutes.coerceAtLeast(0).toLong() * 60L,
                    averagePaceMinPerKm = if (discipline == UserDiscipline.RUNNING && distanceKm > 0f && durationMinutes > 0) {
                        durationMinutes / distanceKm.toDouble()
                    } else {
                        0.0
                    },
                    averageSpeedKmh = if (discipline == UserDiscipline.CYCLING && distanceKm > 0f && durationMinutes > 0) {
                        distanceKm / (durationMinutes / 60.0)
                    } else {
                        0.0
                    },
                    notes = notes
                )
            )
            triggerGamificationHook("log_workout")
            ProductAnalytics.track(
                "discipline_session_logged",
                mapOf("discipline" to discipline.name, "duration" to durationMinutes.toString())
            )
        }
    }

    fun addGpsActivitySession(
        discipline: UserDiscipline,
        title: String,
        routePoints: List<GpsRoutePoint>,
        startedAt: Long,
        endedAt: Long,
        durationSeconds: Long,
        caloriesBurned: Int = 0,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val metrics = ActivityTrackingRules.summarize(routePoints, durationSeconds)
            if (!ActivityTrackingRules.canSave(metrics.distanceKm, metrics.durationSeconds)) return@launch
            repository.addActivitySession(
                ActivitySessionEntity(
                    discipline = discipline.name,
                    sportType = discipline.name,
                    title = title,
                    distanceKm = metrics.distanceKm.toFloat(),
                    durationMinutes = (metrics.durationSeconds / 60L).toInt(),
                    caloriesBurned = caloriesBurned.coerceAtLeast(0),
                    routePointsJson = ActivityTrackingRules.toRouteJson(routePoints),
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationSeconds = metrics.durationSeconds,
                    averagePaceMinPerKm = if (discipline == UserDiscipline.RUNNING) metrics.averagePaceMinPerKm else 0.0,
                    averageSpeedKmh = if (discipline == UserDiscipline.CYCLING) metrics.averageSpeedKmh else 0.0,
                    source = "local_gps",
                    notes = notes,
                    timestamp = endedAt
                )
            )
            triggerGamificationHook("log_workout")
            ProductAnalytics.track(
                "gps_session_logged",
                mapOf(
                    "discipline" to discipline.name,
                    "duration" to metrics.durationSeconds.toString(),
                    "distance_km" to "%.2f".format(metrics.distanceKm)
                )
            )
        }
    }

    fun removeActivitySession(session: ActivitySessionEntity) {
        viewModelScope.launch {
            repository.deleteActivitySession(session)
        }
    }

    // Health and recovery loggers (glucose, pressure, water, sleep, energy, mood)
    fun logHealthMetrics(
        glucose: Float,
        preMeal: Boolean,
        systolic: Int,
        diastolic: Int,
        waterMl: Int,
        sleepHours: Float,
        sleepQuality: Int,
        weight: Float = 0.0f,
        energyLevel: Int = 0,
        moodLevel: Int = 0,
        painFatigueLevel: Int = 0,
        painFatigueNote: String = "",
        steps: Int = 0
    ) {
        viewModelScope.launch {
            repository.addHealthLog(
                HealthLogEntity(
                    type = inferWellnessType(
                        glucose = glucose,
                        systolic = systolic,
                        waterMl = waterMl,
                        sleepHours = sleepHours,
                        weight = weight,
                        energyLevel = energyLevel,
                        steps = steps
                    ).key,
                    value = inferPrimaryValue(glucose, systolic, waterMl, sleepHours, weight, energyLevel, steps),
                    secondaryValue = diastolic.toFloat(),
                    unit = inferUnit(glucose, systolic, waterMl, sleepHours, weight, energyLevel, steps),
                    context = if (glucose > 0f) {
                        if (preMeal) "antes de comer" else "despues de comer"
                    } else "",
                    note = painFatigueNote,
                    dateKey = LocalDateUtils.getTodayString(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    glucoseLevel = glucose,
                    glucoseIsPreMeal = preMeal,
                    systolicPressure = systolic,
                    diastolicPressure = diastolic,
                    waterIntakeMl = waterMl,
                    sleepDurationHours = sleepHours,
                    sleepQualityScale = sleepQuality,
                    energyLevel = energyLevel,
                    moodLevel = moodLevel,
                    painFatigueLevel = painFatigueLevel,
                    painFatigueNote = painFatigueNote,
                    steps = steps,
                    weightKg = weight
                )
            )
            if (waterMl > 0) triggerGamificationHook("log_water")
            if (sleepHours > 0) triggerGamificationHook("log_sleep")
            if (weight > 0.0f) triggerGamificationHook("log_weight")
            if (weight > 0.0f) ProductAnalytics.track("weight_logged")
            if (energyLevel > 0 || moodLevel > 0 || painFatigueLevel > 0) {
                ProductAnalytics.track("recovery_signal_logged")
            }
            if (steps > 0) ProductAnalytics.track("steps_logged")
        }
    }

    fun markWorkoutCompletedFromTrainingSession() {
        viewModelScope.launch {
            val today = LocalDateUtils.getTodayString()
            val existing = repository.getHealthLogForDateTypeContext(
                dateKey = today,
                type = WellnessEntryType.HABIT.key,
                context = WellnessEntryRules.AUTO_WORKOUT_CONTEXT
            )
            val now = System.currentTimeMillis()
            val autoLog = (existing ?: HealthLogEntity(
                type = WellnessEntryType.HABIT.key,
                value = 1f,
                unit = "check-in",
                context = WellnessEntryRules.AUTO_WORKOUT_CONTEXT,
                note = "Entrenamiento registrado automaticamente",
                dateKey = today,
                createdAt = now,
                timestamp = now,
                isAutoWorkout = true
            )).copy(
                value = 1f,
                unit = "check-in",
                context = WellnessEntryRules.AUTO_WORKOUT_CONTEXT,
                note = "Entrenamiento registrado automaticamente",
                updatedAt = now,
                isAutoWorkout = true
            )
            repository.addHealthLog(autoLog)
        }
    }

    fun addQuickWater(ml: Int) {
        viewModelScope.launch {
            // Find if there is a log for today and update it, or add new quick log (and Room sums them in ViewModel counts)
            repository.addHealthLog(
                HealthLogEntity(
                    type = WellnessEntryType.WATER.key,
                    value = ml.toFloat(),
                    unit = "ml",
                    context = waterContextLabel(ml),
                    dateKey = LocalDateUtils.getTodayString(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    waterIntakeMl = ml
                )
            )
            triggerGamificationHook("log_water")
            ProductAnalytics.track("water_logged", mapOf("ml" to ml.toString()))
        }
    }

    fun addWaterMl(amountMl: Int) {
        addQuickWater(amountMl)
    }

    fun addWellnessEntry(draft: WellnessEntryDraft) {
        val validation = WellnessEntryRules.validate(draft)
        if (!validation.isValid) return
        viewModelScope.launch {
            val log = buildHealthLogFromDraft(draft)
            repository.addHealthLog(log)
            triggerHooksForWellnessLog(log)
            ProductAnalytics.track("wellbeing_entry_added", mapOf("type" to draft.type.key))
        }
    }

    fun updateWellnessEntry(existing: HealthLogEntity, draft: WellnessEntryDraft) {
        val validation = WellnessEntryRules.validate(draft)
        if (!validation.isValid) return
        viewModelScope.launch {
            val replacement = buildHealthLogFromDraft(
                draft = draft,
                id = existing.id,
                timestamp = existing.timestamp,
                createdAt = if (existing.createdAt > 0L) existing.createdAt else existing.timestamp
            )
            repository.updateHealthLog(replacement)
            triggerHooksForWellnessLog(replacement)
            ProductAnalytics.track("wellbeing_entry_updated", mapOf("type" to draft.type.key))
        }
    }

    fun deleteWellnessEntry(log: HealthLogEntity) {
        removeHealthLog(log)
    }

    fun updateHealthLog(log: HealthLogEntity) {
        viewModelScope.launch {
            repository.updateHealthLog(log.copy(updatedAt = System.currentTimeMillis()))
            triggerHooksForWellnessLog(log)
            ProductAnalytics.track("wellbeing_entry_updated")
        }
    }

    fun removeHealthLog(log: HealthLogEntity) {
        viewModelScope.launch {
            repository.deleteHealthLog(log)
            ProductAnalytics.track("wellbeing_entry_deleted")
        }
    }

    fun updateDefaultGlassMl(value: Int) {
        prefs.setDefaultGlassMl(value)
        _defaultGlassMl.value = prefs.getDefaultGlassMl()
        ProductAnalytics.track("default_glass_updated", mapOf("ml" to _defaultGlassMl.value.toString()))
    }

    fun updateHybridPlanSettings(settings: HybridPlanSettings) {
        prefs.setHybridPlanSettings(settings)
        _hybridPlanSettings.value = prefs.getHybridPlanSettings()
        ProductAnalytics.track("hybrid_settings_updated")
    }

    private fun buildHealthLogFromDraft(
        draft: WellnessEntryDraft,
        id: Int = 0,
        timestamp: Long = System.currentTimeMillis(),
        createdAt: Long = timestamp
    ): HealthLogEntity {
        val value = draft.valueText.trim().toFloatOrNull() ?: 0f
        val secondary = draft.secondaryValueText.trim().toFloatOrNull() ?: 0f
        val pulse = draft.pulseText.trim().toIntOrNull() ?: 0
        val now = System.currentTimeMillis()
        val normalizedContext = draft.context.trim()
        val normalizedNote = draft.note.trim()
        return HealthLogEntity(
            id = id,
            type = draft.type.key,
            value = value,
            secondaryValue = secondary,
            unit = draft.unit,
            context = normalizedContext,
            note = normalizedNote,
            dateKey = LocalDateUtils.getTodayString(),
            createdAt = createdAt,
            updatedAt = now,
            pulse = pulse,
            glucoseLevel = if (draft.type == WellnessEntryType.GLUCOSE) value else 0f,
            glucoseIsPreMeal = normalizedContext != "despues de comer",
            systolicPressure = if (draft.type == WellnessEntryType.PRESSURE) value.roundToInt() else 0,
            diastolicPressure = if (draft.type == WellnessEntryType.PRESSURE) secondary.roundToInt() else 0,
            waterIntakeMl = if (draft.type == WellnessEntryType.WATER) value.roundToInt() else 0,
            sleepDurationHours = if (draft.type == WellnessEntryType.SLEEP) value else 0f,
            sleepQualityScale = if (draft.type == WellnessEntryType.SLEEP) secondary.roundToInt().coerceIn(0, 5) else 0,
            energyLevel = if (draft.type == WellnessEntryType.ENERGY) value.roundToInt().coerceIn(1, 5) else 0,
            moodLevel = 0,
            painFatigueLevel = 0,
            painFatigueNote = normalizedNote,
            steps = if (draft.type == WellnessEntryType.STEPS) value.roundToInt() else 0,
            weightKg = if (draft.type == WellnessEntryType.WEIGHT) value else 0f,
            timestamp = timestamp
        )
    }

    private fun triggerHooksForWellnessLog(log: HealthLogEntity) {
        when (log.type) {
            WellnessEntryType.WATER.key -> triggerGamificationHook("log_water")
            WellnessEntryType.FOOD.key -> triggerGamificationHook("log_food")
            WellnessEntryType.SLEEP.key -> triggerGamificationHook("log_sleep")
            WellnessEntryType.WEIGHT.key -> triggerGamificationHook("log_weight")
            WellnessEntryType.HABIT.key -> triggerGamificationHook("log_checkin")
        }
    }

    private fun waterContextLabel(ml: Int): String = when (ml) {
        250 -> "vaso"
        500 -> "500 ml"
        600 -> "botella"
        1000 -> "1 litro"
        else -> "$ml ml"
    }

    private fun hasWorkoutToday(): Boolean {
        return allWorkoutLogs.value.any { isToday(it.timestamp) }
    }

    private fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val other = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    }

    private fun inferWellnessType(
        glucose: Float,
        systolic: Int,
        waterMl: Int,
        sleepHours: Float,
        weight: Float,
        energyLevel: Int,
        steps: Int
    ): WellnessEntryType = when {
        waterMl > 0 -> WellnessEntryType.WATER
        glucose > 0f -> WellnessEntryType.GLUCOSE
        systolic > 0 -> WellnessEntryType.PRESSURE
        sleepHours > 0f -> WellnessEntryType.SLEEP
        weight > 0f -> WellnessEntryType.WEIGHT
        steps > 0 -> WellnessEntryType.STEPS
        energyLevel > 0 -> WellnessEntryType.ENERGY
        else -> WellnessEntryType.HABIT
    }

    private fun inferPrimaryValue(glucose: Float, systolic: Int, waterMl: Int, sleepHours: Float, weight: Float, energyLevel: Int, steps: Int): Float {
        return when {
            waterMl > 0 -> waterMl.toFloat()
            glucose > 0f -> glucose
            systolic > 0 -> systolic.toFloat()
            sleepHours > 0f -> sleepHours
            weight > 0f -> weight
            steps > 0 -> steps.toFloat()
            energyLevel > 0 -> energyLevel.toFloat()
            else -> 1f
        }
    }

    private fun inferUnit(glucose: Float, systolic: Int, waterMl: Int, sleepHours: Float, weight: Float, energyLevel: Int, steps: Int): String {
        return when {
            waterMl > 0 -> "ml"
            glucose > 0f -> "mg/dL"
            systolic > 0 -> "mmHg"
            sleepHours > 0f -> "h"
            weight > 0f -> "kg"
            steps > 0 -> "pasos"
            energyLevel > 0 -> "escala"
            else -> "check-in"
        }
    }

    fun triggerGamificationHook(actionKey: String) {
        viewModelScope.launch {
            try {
                val todayStr = com.example.data.repository.LocalDateUtils.getTodayString()
                val activityDao = db.dailyActivityDao()
                val current = activityDao.getActivityForDay("local_user", todayStr) ?: com.example.data.model.DailyActivityEntity(
                    userId = "local_user",
                    date = todayStr,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val updated = when (actionKey) {
                    "log_food" -> current.copy(hasFoodLog = true, isActiveDay = true, updatedAt = System.currentTimeMillis())
                    "log_workout" -> current.copy(hasWorkoutLog = true, isActiveDay = true, updatedAt = System.currentTimeMillis())
                    "log_water" -> current.copy(hasWaterLog = true, isActiveDay = true, updatedAt = System.currentTimeMillis())
                    "log_weight" -> current.copy(hasWeightLog = true, isActiveDay = true, updatedAt = System.currentTimeMillis())
                    "log_sleep" -> current.copy(hasSleepLog = true, isActiveDay = true, updatedAt = System.currentTimeMillis())
                    "log_checkin" -> current.copy(hasCheckIn = true, isActiveDay = true, updatedAt = System.currentTimeMillis())
                    else -> current.copy(isActiveDay = true, updatedAt = System.currentTimeMillis())
                }
                activityDao.insertActivity(updated)

                val streakRepo = com.example.data.repository.StreakRepository(db.streakDao())
                val activityRepo = com.example.data.repository.DailyActivityRepository(db.dailyActivityDao())
                val streakManager = com.example.data.repository.StreakManager(streakRepo, activityRepo)
                val (newStreak, wasReset) = streakManager.updateStreak("local_user")

                val missionRepo = com.example.data.repository.DailyMissionRepository(db.dailyMissionDao())
                val achRepo = com.example.data.repository.AchievementRepository(db.achievementDao())
                val achManager = com.example.data.repository.AchievementManager(achRepo)
                val missionManager = com.example.data.repository.DailyMissionManager(missionRepo, streakManager, achManager)
                missionManager.triggerMissionCheck("local_user", actionKey)

                val userLogsCount = activityRepo.getAllActivitiesFlow("local_user").first()

                if (actionKey == "log_food") {
                    achManager.checkAndAward("local_user", "ach_first_food")
                }
                if (actionKey == "log_workout") {
                    achManager.checkAndAward("local_user", "ach_first_workout")
                }

                if (newStreak >= 3) {
                    achManager.checkAndAward("local_user", "ach_streak_3")
                }
                if (newStreak >= 7) {
                    achManager.checkAndAward("local_user", "ach_streak_7")
                }
                if (newStreak >= 14) {
                    achManager.checkAndAward("local_user", "ach_streak_14")
                }
                if (newStreak >= 30) {
                    achManager.checkAndAward("local_user", "ach_streak_30")
                }
                if (wasReset) {
                    achManager.checkAndAward("local_user", "ach_epic_return")
                }

                val streakHydration = userLogsCount.take(3).all { it.hasWaterLog }
                if (streakHydration && userLogsCount.size >= 3) {
                    achManager.checkAndAward("local_user", "ach_hydration")
                }

                val activeWeekDays = userLogsCount.take(7).count { it.isActiveDay }
                if (activeWeekDays == 7) {
                    achManager.checkAndAward("local_user", "ach_active_week")
                }
            } catch (e: Exception) {
                // Keep it completely silent on background
            }
        }
    }

    fun markDailyCheckInActions(
        waterSelected: Boolean,
        foodSelected: Boolean,
        workoutSelected: Boolean,
        sleepSelected: Boolean,
        weightSelected: Boolean
    ) {
        viewModelScope.launch {
            try {
                val actionKeys = buildList {
                    if (waterSelected) add("log_water")
                    if (foodSelected) add("log_food")
                    if (workoutSelected) add("log_workout")
                    if (sleepSelected) add("log_sleep")
                    if (weightSelected) add("log_weight")
                }

                val todayStr = com.example.data.repository.LocalDateUtils.getTodayString()
                val activityDao = db.dailyActivityDao()
                val current = activityDao.getActivityForDay("local_user", todayStr) ?: com.example.data.model.DailyActivityEntity(
                    userId = "local_user",
                    date = todayStr,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                activityDao.insertActivity(
                    current.copy(
                        hasWaterLog = current.hasWaterLog || waterSelected,
                        hasFoodLog = current.hasFoodLog || foodSelected,
                        hasWorkoutLog = current.hasWorkoutLog || workoutSelected,
                        hasSleepLog = current.hasSleepLog || sleepSelected,
                        hasWeightLog = current.hasWeightLog || weightSelected,
                        hasCheckIn = true,
                        isActiveDay = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                val streakRepo = com.example.data.repository.StreakRepository(db.streakDao())
                val activityRepo = com.example.data.repository.DailyActivityRepository(db.dailyActivityDao())
                val streakManager = com.example.data.repository.StreakManager(streakRepo, activityRepo)
                val missionRepo = com.example.data.repository.DailyMissionRepository(db.dailyMissionDao())
                val achRepo = com.example.data.repository.AchievementRepository(db.achievementDao())
                val achManager = com.example.data.repository.AchievementManager(achRepo)
                val missionManager = com.example.data.repository.DailyMissionManager(missionRepo, streakManager, achManager)

                streakManager.updateStreak("local_user")
                actionKeys.forEach { key ->
                    missionManager.triggerMissionCheck("local_user", key)
                    if (key == "log_food") achManager.checkAndAward("local_user", "ach_first_food")
                    if (key == "log_workout") achManager.checkAndAward("local_user", "ach_first_workout")
                }
            } catch (e: Exception) {
                // Daily check-in should never block the user.
            }
        }
    }

    // Local premium toggle is available only in debug builds.
    fun patchPremiumAccess(enabled: Boolean) {
        viewModelScope.launch {
            if (!BuildConfig.DEBUG) {
                prefs.setPremiumUnlocked(false)
                _isPremiumActive.value = false
                repository.getUserDirectly()?.let { repository.saveUser(it.copy(isPremium = false)) }
                return@launch
            }
            prefs.setPremiumUnlocked(enabled)
            _isPremiumActive.value = enabled
            val current = repository.getUserDirectly()
            if (current != null) {
                repository.saveUser(current.copy(isPremium = enabled))
            }
        }
    }

    fun setPremiumEntitlementFromBilling(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setPremiumUnlocked(enabled)
            _isPremiumActive.value = enabled
            repository.getUserDirectly()?.let { current ->
                repository.saveUser(current.copy(isPremium = enabled))
            }
        }
    }

    // Units setting toggle
    fun toggleUnitsPreference() {
        val currentUnitType = _useMetric.value
        prefs.setUseMetricSettings(!currentUnitType)
        _useMetric.value = !currentUnitType
    }

    // Set theme selector preference (system, dark, light)
    fun updateThemeSelector(theme: String) {
        prefs.setThemeSelector(theme)
        _themeSelector.value = theme
    }

    fun updatePerformanceMode(mode: AppPerformanceMode) {
        performanceRepository.setSelectedMode(mode)
        syncVisualFeatureFlagsFromPerformanceProfile()
        refreshPerformanceState()
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setPerformanceAnimationsDisabled(disabled: Boolean) {
        performanceRepository.setAnimationsDisabled(disabled)
        syncVisualFeatureFlagsFromPerformanceProfile()
        refreshPerformanceState()
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setPerformanceChartsDisabled(disabled: Boolean) {
        performanceRepository.setChartsDisabled(disabled)
        syncVisualFeatureFlagsFromPerformanceProfile()
        refreshPerformanceState()
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setPerformanceVisualEffectsDisabled(disabled: Boolean) {
        performanceRepository.setVisualEffectsDisabled(disabled)
        refreshPerformanceState()
    }

    fun setDashboardWidgetLimit(limit: Int?) {
        performanceRepository.setDashboardWidgetLimit(limit)
        refreshPerformanceState()
    }

    fun setHistoryItemLimit(limit: Int?) {
        performanceRepository.setHistoryItemLimit(limit)
        refreshPerformanceState()
    }

    fun setBatterySaverEnabled(enabled: Boolean) {
        performanceRepository.setBatterySaverEnabled(enabled)
        syncVisualFeatureFlagsFromPerformanceProfile()
        refreshPerformanceState()
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun resetPerformanceSettings() {
        performanceRepository.resetToAutomatic()
        syncVisualFeatureFlagsFromPerformanceProfile()
        refreshPerformanceState()
        _featureSettings.value = prefs.getFeatureSettings()
    }

    // GDPR & Google Play account deletion compliance
    fun purgeAccountData() {
        viewModelScope.launch {
            repository.wipeUserData()
            prefs.clearAllPreferences()
            performanceRepository.resetToAutomatic()
            refreshPerformanceState()
            _isLoggedIn.value = false
            _isOnboardingCompleted.value = false
            _isPremiumActive.value = false
            _preventiveAlerts.value = null
            _habitsAdvice.value = null
        }
    }

    // AI Wellness Advice (triggers Gemini API or fallback offline)
    fun fetchPreventiveAlerts() {
        viewModelScope.launch {
            val user = repository.getUserDirectly() ?: return@launch
            val recentLogs = repository.allHealthLogs.first().take(10)
            _alertsLoading.value = true
            _preventiveAlerts.value = null
            try {
                val advice = geminiService.getPreventiveAdvice(user, recentLogs, "alertas")
                _preventiveAlerts.value = advice
            } catch (e: Exception) {
                _preventiveAlerts.value = "Imposible conectar con el motor preventivo en este momento: ${e.localizedMessage}"
            } finally {
                _alertsLoading.value = false
            }
        }
    }

    fun fetchHealthyHabits() {
        viewModelScope.launch {
            val user = repository.getUserDirectly() ?: return@launch
            val recentLogs = repository.allHealthLogs.first().take(10)
            _habitsLoading.value = true
            _habitsAdvice.value = null
            try {
                val advice = geminiService.getPreventiveAdvice(user, recentLogs, "hábitos")
                _habitsAdvice.value = advice
            } catch (e: Exception) {
                _habitsAdvice.value = "Imposible conectar con el mentor de hábitos en este momento: ${e.localizedMessage}"
            } finally {
                _habitsLoading.value = false
            }
        }
    }

    fun fetchFitCoreAdvice(requestType: String) {
        viewModelScope.launch {
            val user = repository.getUserDirectly() ?: return@launch
            val recentLogs = repository.getRecentHealthLogs(10).first()
            _fitCoreLoading.value = true
            _fitCoreAdvice.value = null
            try {
                ProductAnalytics.track("coach_message_sent", mapOf("topic" to requestType))
                _fitCoreAdvice.value = geminiService.getPreventiveAdvice(user, recentLogs, requestType)
            } catch (e: Exception) {
                _fitCoreAdvice.value = "FitCore no pudo generar la recomendación ahora. Intenta de nuevo en unos segundos."
            } finally {
                _fitCoreLoading.value = false
            }
        }
    }

    fun setNutritionEnabled(enabled: Boolean) {
        prefs.setNutritionEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setWorkoutEnabled(enabled: Boolean) {
        prefs.setWorkoutEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setProgressEnabled(enabled: Boolean) {
        prefs.setProgressEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setHealthEnabled(enabled: Boolean) {
        prefs.setHealthEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setAIRecommendationsEnabled(enabled: Boolean) {
        prefs.setAIRecommendationsEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setPremiumEnabled(enabled: Boolean) {
        prefs.setPremiumEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setChartsEnabled(enabled: Boolean) {
        prefs.setChartsEnabled(enabled)
        performanceRepository.setChartsDisabled(!enabled)
        refreshPerformanceState()
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.setRemindersEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.setAnimationsEnabled(enabled)
        performanceRepository.setAnimationsDisabled(!enabled)
        refreshPerformanceState()
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setPerformanceModeEnabled(enabled: Boolean) {
        prefs.setPerformanceModeEnabled(enabled)
        performanceRepository.setSelectedMode(
            if (enabled) AppPerformanceMode.LIGHTWEIGHT else AppPerformanceMode.AUTO
        )
        syncVisualFeatureFlagsFromPerformanceProfile()
        refreshPerformanceState()
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setStreakEnabled(enabled: Boolean) {
        prefs.setStreakEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setShowStreakWidget(enabled: Boolean) {
        prefs.setShowStreakWidget(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setDailyMissionsEnabled(enabled: Boolean) {
        prefs.setDailyMissionsEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setAchievementsEnabled(enabled: Boolean) {
        prefs.setAchievementsEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setMotivationMessagesEnabled(enabled: Boolean) {
        prefs.setMotivationMessagesEnabled(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    fun setSoftMotivationMode(enabled: Boolean) {
        prefs.setSoftMotivationMode(enabled)
        _featureSettings.value = prefs.getFeatureSettings()
    }

    private fun refreshPerformanceState() {
        _performancePreferences.value = performanceRepository.getPreferences()
        _visualPerformanceProfile.value = performanceRepository.buildProfile()
    }

    private fun syncVisualFeatureFlagsFromPerformanceProfile() {
        val profile = performanceRepository.buildProfile()
        prefs.setPerformanceModeEnabled(profile.useLightweightCards)
        prefs.setAnimationsEnabled(profile.animationsEnabled)
        prefs.setChartsEnabled(profile.chartsEnabled)
    }
}

