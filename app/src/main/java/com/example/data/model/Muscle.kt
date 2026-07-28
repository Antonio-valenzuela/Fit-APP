package com.example.data.model

import java.text.Normalizer
import java.util.Locale

enum class Muscle(val displayName: String, val legacyName: String) {
    CHEST("Pecho", "pecho"),
    BACK("Espalda", "espalda"),
    SHOULDERS("Hombros", "hombros"),
    BICEPS("Bíceps", "biceps"),
    TRICEPS("Tríceps", "triceps"),
    ABS("Abdomen", "abdomen"),
    GLUTES("Glúteos", "gluteos"),
    QUADS("Cuádriceps", "cuadriceps"),
    HAMSTRINGS("Femorales", "femorales"),
    CALVES("Pantorrillas", "pantorrillas"),
    FULL_BODY("Cuerpo completo", "full_body"),
    CARDIO("Cardio", "cardio"),
    FOREARMS("Antebrazos", "antebrazos"),
    TRAPS("Trapecio", "trapecio"),
    ADDUCTORS("Aductores", "aductores"),
    ABDUCTORS("Abductores", "abductores");

    companion object {
        fun fromLegacyName(name: String?): Muscle? {
            if (name == null) return null
            
            val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
                .trim()

            return when (normalized) {
                "pecho", "chest" -> CHEST
                "espalda", "back" -> BACK
                "hombros", "hombro", "shoulders", "shoulder" -> SHOULDERS
                "biceps" -> BICEPS
                "triceps" -> TRICEPS
                "abdomen", "abs", "core" -> ABS
                "gluteos", "gluteo", "glutes" -> GLUTES
                "cuadriceps", "quads" -> QUADS
                "femorales", "femoral", "hamstrings", "isquiotibiales" -> HAMSTRINGS
                "pantorrillas", "pantorrilla", "calves", "gemelos" -> CALVES
                "full_body", "cuerpo_completo", "full body" -> FULL_BODY
                "cardio" -> CARDIO
                "antebrazos", "antebrazo", "forearms" -> FOREARMS
                "trapecio", "traps" -> TRAPS
                "aductores", "aductor" -> ADDUCTORS
                "abductores", "abductor" -> ABDUCTORS
                else -> null
            }
        }
    }
}
