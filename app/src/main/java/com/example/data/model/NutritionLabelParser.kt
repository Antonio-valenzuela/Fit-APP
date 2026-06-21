package com.example.data.model

import java.text.Normalizer
import kotlin.math.round

data class ParsedNutritionLabel(
    val servingSizeLabel: String? = null,
    val calories: Double? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val saturatedFatGrams: Double? = null,
    val sugarGrams: Double? = null,
    val fiberGrams: Double? = null,
    val sodiumMg: Double? = null,
    val confidence: Float = 0f,
    val warnings: List<String> = emptyList()
)

object NutritionLabelParser {
    fun parse(rawText: String): ParsedNutritionLabel {
        val normalized = normalize(rawText)
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
        val warnings = mutableListOf<String>()
        val hasPer100 = normalized.contains("por 100 g") || normalized.contains("por 100g") ||
            normalized.contains("por 100 ml") || normalized.contains("por 100ml")
        val hasPerServing = normalized.contains("por porcion") || normalized.contains("cantidad por porcion") ||
            normalized.contains("tamano de porcion")
        if (hasPer100 && hasPerServing) {
            warnings.add("Se detectaron datos por 100 g/ml y por porcion. Revisa cual quieres guardar.")
        }

        val kcal = findCalories(lines)
        val protein = findGrams(lines, listOf("proteina", "proteinas"))
        val carbs = findGrams(lines, listOf("carbohidratos totales", "hidratos de carbono", "carbohidratos"))
        val saturatedFat = findGrams(lines, listOf("grasas saturadas", "grasa saturada"))
        val fat = findGrams(lines.filterNot { it.contains("saturad") }, listOf("grasas totales", "lipidos", "grasas", "grasa"))
        val sugar = findGrams(lines, listOf("azucares anadidos", "azucares añadidos", "azucares", "azucar"))
        val fiber = findGrams(lines, listOf("fibra dietetica", "fibra"))
        val sodium = findSodiumMg(lines)
        val serving = findServing(lines)

        val foundCount = listOf(kcal, protein, carbs, fat, saturatedFat, sugar, fiber, sodium).count { it != null }
        if (foundCount < 3) warnings.add("No se detectaron suficientes valores nutrimentales.")

        return ParsedNutritionLabel(
            servingSizeLabel = serving,
            calories = kcal,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            saturatedFatGrams = saturatedFat,
            sugarGrams = sugar,
            fiberGrams = fiber,
            sodiumMg = sodium,
            confidence = (foundCount / 6f).coerceIn(0f, 1f),
            warnings = warnings
        )
    }

    private fun normalize(value: String): String {
        val noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return noAccents.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
    }

    private fun findCalories(lines: List<String>): Double? {
        val calorieLines = lines.filter { line ->
            listOf("calorias", "kcal", "energia", "contenido energetico").any { line.contains(it) }
        }
        calorieLines.forEach { line ->
            numberBeforeUnit(line, "kcal")?.let { return it }
        }
        calorieLines.forEach { line ->
            numberBeforeUnit(line, "kj")?.let { kj -> return roundOne(kj / 4.184) }
        }
        return calorieLines.firstNotNullOfOrNull { firstNumber(it) }
    }

    private fun findGrams(lines: List<String>, labels: List<String>): Double? {
        return lines.firstNotNullOfOrNull { line ->
            if (labels.any { line.contains(it) }) {
                numberBeforeUnit(line, "g") ?: firstNumber(line)
            } else {
                null
            }
        }
    }

    private fun findSodiumMg(lines: List<String>): Double? {
        return lines.firstNotNullOfOrNull { line ->
            if (!line.contains("sodio") && !line.contains("sal")) return@firstNotNullOfOrNull null
            numberBeforeUnit(line, "mg") ?: numberBeforeUnit(line, "g")?.let { roundOne(it * 1000.0) } ?: firstNumber(line)
        }
    }

    private fun findServing(lines: List<String>): String? {
        return lines.firstOrNull { line ->
            listOf("porcion", "tamano de porcion", "porciones por envase", "cantidad por porcion", "por 100 g", "por 100 ml").any { line.contains(it) }
        }?.take(80)
    }

    private fun numberBeforeUnit(line: String, unit: String): Double? {
        val pattern = Regex("""(\d+(?:[.,]\d+)?)\s*$unit\b""")
        return pattern.find(line)?.groupValues?.getOrNull(1)?.toNumber()
    }

    private fun firstNumber(line: String): Double? {
        return Regex("""\d+(?:[.,]\d+)?""").find(line)?.value.toNumber()
    }

    private fun String?.toNumber(): Double? {
        return this?.replace(",", ".")?.toDoubleOrNull()
    }

    private fun roundOne(value: Double): Double = round(value * 10.0) / 10.0
}
