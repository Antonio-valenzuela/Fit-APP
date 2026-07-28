package com.example.ui.workout

data class ExerciseCatalogItem(
    val id: String,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val level: String,
    val instructions: String,
    val suggestedSets: Int,
    val suggestedReps: String,
    val restSeconds: Int
)

data class SuggestedRoutine(
    val id: String,
    val name: String,
    val objective: String,
    val level: String,
    val estimatedMinutes: Int,
    val primaryMuscles: List<String>,
    val exerciseIds: List<String>,
    val equipment: String = "Gym",
    val focus: String = "Fuerza"
)

object TrainingCatalog {
    val exercises = listOf(
        ExerciseCatalogItem("squat", "Sentadilla", "cuadriceps", listOf("gluteos", "femorales"), "Peso corporal o barra", "Principiante", "Baja con control, rodillas alineadas y pecho estable.", 4, "8-12", 90),
        ExerciseCatalogItem("bulgarian_squat", "Sentadilla bulgara", "gluteos", listOf("cuadriceps", "femorales"), "Banco y mancuernas", "Intermedio", "Apoya el pie trasero y baja vertical sin perder estabilidad.", 3, "8-10 por pierna", 90),
        ExerciseCatalogItem("lunges", "Zancadas", "cuadriceps", listOf("gluteos"), "Peso corporal o mancuernas", "Principiante", "Da un paso largo, baja controlado y empuja con la pierna frontal.", 3, "10-12 por pierna", 75),
        ExerciseCatalogItem("hip_thrust", "Hip thrust", "gluteos", listOf("femorales"), "Banco y barra", "Principiante", "Empuja la cadera arriba y pausa un segundo en contraccion.", 4, "10-12", 90),
        ExerciseCatalogItem("romanian_deadlift", "Peso muerto rumano", "femorales", listOf("gluteos", "espalda"), "Barra o mancuernas", "Intermedio", "Lleva la cadera atras, espalda neutra y siente tension posterior.", 4, "8-10", 120),
        ExerciseCatalogItem("leg_curl", "Curl femoral", "femorales", emptyList(), "Maquina", "Principiante", "Flexiona rodillas sin despegar la cadera del apoyo.", 3, "10-15", 75),
        ExerciseCatalogItem("leg_extension", "Extension de cuadriceps", "cuadriceps", emptyList(), "Maquina", "Principiante", "Extiende con control y evita bloquear agresivamente la rodilla.", 3, "12-15", 60),
        ExerciseCatalogItem("calf_raise", "Elevacion de talones", "pantorrillas", emptyList(), "Maquina o escalon", "Principiante", "Sube completo, pausa arriba y baja lento.", 4, "12-20", 60),
        ExerciseCatalogItem("bench_press", "Press banca", "pecho", listOf("triceps", "hombros"), "Barra o mancuernas", "Intermedio", "Baja al pecho con control y empuja sin perder escápulas.", 4, "6-10", 120),
        ExerciseCatalogItem("incline_press", "Press inclinado", "pecho", listOf("hombros", "triceps"), "Banco inclinado", "Intermedio", "Empuja en angulo inclinado manteniendo muñecas firmes.", 3, "8-12", 90),
        ExerciseCatalogItem("flyes", "Aperturas", "pecho", listOf("hombros"), "Mancuernas o polea", "Principiante", "Abre con codos suaves y junta sin golpear las manos.", 3, "10-15", 60),
        ExerciseCatalogItem("push_up", "Push up", "pecho", listOf("triceps", "abdomen"), "Peso corporal", "Principiante", "Cuerpo recto, baja el pecho y empuja el suelo.", 3, "8-15", 60),
        ExerciseCatalogItem("barbell_row", "Remo con barra", "espalda", listOf("biceps"), "Barra", "Intermedio", "Inclina torso estable y lleva la barra hacia el abdomen.", 4, "8-10", 120),
        ExerciseCatalogItem("dumbbell_row", "Remo con mancuerna", "espalda", listOf("biceps"), "Mancuerna", "Principiante", "Tira el codo hacia la cadera sin girar el tronco.", 3, "10-12", 75),
        ExerciseCatalogItem("lat_pulldown", "Jalon al pecho", "espalda", listOf("biceps"), "Polea", "Principiante", "Lleva la barra al pecho y evita balancearte.", 4, "8-12", 90),
        ExerciseCatalogItem("pull_up", "Dominadas", "espalda", listOf("biceps"), "Barra", "Avanzado", "Sube con control y baja completo sin colgarte de golpe.", 3, "4-8", 120),
        ExerciseCatalogItem("military_press", "Press militar", "hombros", listOf("triceps"), "Barra o mancuernas", "Intermedio", "Empuja arriba sin arquear demasiado la espalda.", 4, "6-10", 120),
        ExerciseCatalogItem("lateral_raise", "Elevaciones laterales", "hombros", emptyList(), "Mancuernas", "Principiante", "Eleva hasta linea de hombros con control.", 3, "12-15", 60),
        ExerciseCatalogItem("face_pull", "Face pull", "hombros", listOf("espalda"), "Polea", "Principiante", "Tira hacia el rostro separando manos y cuidando postura.", 3, "12-15", 60),
        ExerciseCatalogItem("biceps_curl", "Curl biceps", "biceps", listOf("antebrazos"), "Barra o mancuernas", "Principiante", "Sube sin balancear el cuerpo y baja lento.", 3, "10-12", 60),
        ExerciseCatalogItem("hammer_curl", "Curl martillo", "biceps", listOf("antebrazos"), "Mancuernas", "Principiante", "Mantén agarre neutro y codos cerca del cuerpo.", 3, "10-12", 60),
        ExerciseCatalogItem("triceps_extension", "Extension triceps", "triceps", emptyList(), "Polea o mancuerna", "Principiante", "Extiende codos sin mover hombros.", 3, "10-15", 60),
        ExerciseCatalogItem("french_press", "Press frances", "triceps", emptyList(), "Barra Z o mancuerna", "Intermedio", "Baja controlado hacia la frente y extiende con triceps.", 3, "8-12", 75),
        ExerciseCatalogItem("plank", "Plancha", "abdomen", listOf("hombros"), "Peso corporal", "Principiante", "Mantén linea recta y respira sin hundir la cadera.", 3, "30-45 seg", 45),
        ExerciseCatalogItem("crunch", "Crunch", "abdomen", emptyList(), "Peso corporal", "Principiante", "Eleva hombros sin jalar el cuello.", 3, "12-20", 45),
        ExerciseCatalogItem("leg_raise", "Elevacion de piernas", "abdomen", listOf("femorales"), "Peso corporal", "Intermedio", "Sube piernas con abdomen activo y baja lento.", 3, "8-12", 60),
        ExerciseCatalogItem("russian_twist", "Russian twist", "abdomen", emptyList(), "Peso corporal o disco", "Principiante", "Gira controlado manteniendo torso firme.", 3, "16-24", 45)
    )

    val routines = listOf(
        SuggestedRoutine("express_15", "Express sin equipo", "Circuito corto para moverte y sudar sin bascula ni gimnasio", "Principiante", 15, listOf("cuadriceps", "pecho", "abdomen"), listOf("squat", "push_up", "plank"), "Sin equipo", "Bajar grasa"),
        SuggestedRoutine("legs_glutes", "Pierna y gluteo", "Base completa para tren inferior", "Principiante", 42, listOf("cuadriceps", "gluteos"), listOf("squat", "hip_thrust", "lunges", "calf_raise"), "Gym", "Hipertrofia"),
        SuggestedRoutine("hamstrings_glutes", "Femorales y gluteo", "Cadena posterior y estabilidad de cadera", "Intermedio", 38, listOf("femorales", "gluteos"), listOf("romanian_deadlift", "hip_thrust", "leg_curl", "bulgarian_squat"), "Gym", "Fuerza"),
        SuggestedRoutine("push_upper", "Tren superior empuje", "Pecho, hombros y triceps", "Intermedio", 40, listOf("pecho", "hombros", "triceps"), listOf("bench_press", "incline_press", "military_press", "triceps_extension"), "Gym", "Hipertrofia"),
        SuggestedRoutine("pull_upper", "Tren superior tiron", "Espalda y biceps", "Intermedio", 40, listOf("espalda", "biceps"), listOf("lat_pulldown", "barbell_row", "dumbbell_row", "biceps_curl"), "Gym", "Hipertrofia"),
        SuggestedRoutine("beginner_full_body", "Full body principiante", "Entrenamiento simple para empezar", "Principiante", 32, listOf("cuadriceps", "pecho", "espalda", "abdomen"), listOf("squat", "push_up", "dumbbell_row", "plank"), "Casa", "Fuerza"),
        SuggestedRoutine("quick_core", "Core rapido", "Abdomen y control corporal", "Principiante", 18, listOf("abdomen"), listOf("plank", "crunch", "russian_twist"), "Sin equipo", "Movilidad"),
        SuggestedRoutine("functional_20", "Fuerza funcional 20 minutos", "Moverte mejor con poco tiempo", "Principiante", 20, listOf("cuadriceps", "gluteos", "espalda", "abdomen"), listOf("squat", "push_up", "lunges", "plank"), "Sin equipo", "Resistencia"),
        SuggestedRoutine("mobility_recovery", "Movilidad y recuperacion", "Sesion suave para soltar el cuerpo", "Principiante", 20, listOf("espalda", "hombros", "abdomen", "femorales"), listOf("face_pull", "plank", "leg_raise"), "Casa", "Movilidad")
    )

    fun exerciseById(id: String): ExerciseCatalogItem? = exercises.firstOrNull { it.id == id }

    fun exercisesForMuscle(muscle: String): List<ExerciseCatalogItem> {
        return exercises.filter { it.primaryMuscle == muscle || it.secondaryMuscles.contains(muscle) }
    }

    fun routineForMuscle(
        muscle: String,
        preferredEquipment: String = "Gym",
        level: String? = null
    ): SuggestedRoutine {
        val normalized = preferredEquipment.lowercase()
        val matches = routines.filter {
            it.primaryMuscles.contains(muscle) && matchesLevel(it, level)
        }
        val preferred = when (normalized) {
            "casa", "sin equipo" -> matches.firstOrNull {
                it.equipment.lowercase() == "casa" || it.equipment.lowercase() == "sin equipo"
            }
            "ambos", "casa y gimnasio" -> matches.firstOrNull()
            else -> matches.firstOrNull { it.equipment.lowercase() == "gym" }
        }
        return preferred
            ?: matches.firstOrNull()
            ?: routines.firstOrNull { it.id == "beginner_full_body" && matchesLevel(it, level) }
            ?: routines.first { it.id == "beginner_full_body" }
    }

    fun filteredRoutines(
        maxMinutes: Int,
        equipment: String,
        objective: String,
        level: String? = null
    ): List<SuggestedRoutine> {
        val normalizedEquipment = equipment.lowercase()
        val normalizedObjective = objective.lowercase()
        fun matchesEquipment(routine: SuggestedRoutine): Boolean {
            val routineEquipment = routine.equipment.lowercase()
            if (normalizedEquipment == "ambos" || normalizedEquipment == "casa y gimnasio") return true
            return routineEquipment.contains(normalizedEquipment) ||
                (normalizedEquipment == "casa" && routineEquipment == "sin equipo")
        }
        val exact = routines.filter { routine ->
            routine.estimatedMinutes <= maxMinutes &&
                matchesEquipment(routine) &&
                matchesLevel(routine, level) &&
                routine.focus.lowercase().contains(normalizedObjective)
        }
        if (exact.isNotEmpty()) return exact

        val relaxed = routines.filter { routine ->
            routine.estimatedMinutes <= maxMinutes &&
                matchesEquipment(routine) &&
                matchesLevel(routine, level)
        }
        if (relaxed.isNotEmpty()) return relaxed

        val equipmentFallback = routines
            .filter { matchesEquipment(it) && matchesLevel(it, level) }
            .sortedBy { it.estimatedMinutes }
            .take(3)
        if (equipmentFallback.isNotEmpty()) return equipmentFallback

        return routines.filter {
            it.estimatedMinutes <= maxMinutes && matchesLevel(it, level)
        }.ifEmpty {
            routines.filter { matchesLevel(it, level) }.sortedBy { it.estimatedMinutes }.take(3)
        }
    }

    private fun matchesLevel(routine: SuggestedRoutine, selectedLevel: String?): Boolean {
        if (selectedLevel.isNullOrBlank()) return true
        val selectedRank = levelRank(selectedLevel) ?: return true
        val routineRank = levelRank(routine.level) ?: return true
        return routineRank <= selectedRank
    }

    private fun levelRank(level: String): Int? = when (level.trim().lowercase()) {
        "principiante" -> 1
        "intermedio" -> 2
        "avanzado" -> 3
        else -> null
    }
}

