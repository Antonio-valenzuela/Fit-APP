package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLabelParserTest {
    @Test
    fun `parses kcal protein carbs and fat`() {
        val parsed = NutritionLabelParser.parse(
            """
            Informacion nutrimental
            Energia 120 kcal
            Proteina 8 g
            Carbohidratos 15 g
            Grasas totales 3 g
            """.trimIndent()
        )

        assertEquals(120.0, parsed.calories!!, 0.1)
        assertEquals(8.0, parsed.proteinGrams!!, 0.1)
        assertEquals(15.0, parsed.carbsGrams!!, 0.1)
        assertEquals(3.0, parsed.fatGrams!!, 0.1)
    }

    @Test
    fun `parses hidratos de carbono`() {
        val parsed = NutritionLabelParser.parse("Hidratos de carbono 22 g")

        assertEquals(22.0, parsed.carbsGrams!!, 0.1)
    }

    @Test
    fun `parses sodium in mg`() {
        val parsed = NutritionLabelParser.parse("Sodio 340 mg")

        assertEquals(340.0, parsed.sodiumMg!!, 0.1)
    }

    @Test
    fun `converts sodium grams to mg`() {
        val parsed = NutritionLabelParser.parse("Sodio 0.48 g")

        assertEquals(480.0, parsed.sodiumMg!!, 0.1)
    }

    @Test
    fun `converts kJ to kcal when kcal is absent`() {
        val parsed = NutritionLabelParser.parse("Contenido energetico 418 kJ")

        assertEquals(99.9, parsed.calories!!, 0.5)
    }

    @Test
    fun `accepts comma decimal`() {
        val parsed = NutritionLabelParser.parse("Proteinas 7,5 g\nAzucares 12,3 g")

        assertEquals(7.5, parsed.proteinGrams!!, 0.1)
        assertEquals(12.3, parsed.sugarGrams!!, 0.1)
    }

    @Test
    fun `incomplete text returns warnings and no automatic save confidence`() {
        val parsed = NutritionLabelParser.parse("Porcion 30 g\nIngredientes: maiz")

        assertNull(parsed.calories)
        assertTrue(parsed.confidence < 0.5f)
        assertTrue(parsed.warnings.isNotEmpty())
    }

    @Test
    fun `warns when text has per 100 g and per serving`() {
        val parsed = NutritionLabelParser.parse(
            """
            Por 100 g Energia 400 kcal
            Cantidad por porcion Energia 120 kcal
            Proteina 6 g
            """.trimIndent()
        )

        assertTrue(parsed.warnings.any { it.contains("100 g") })
    }

    @Test
    fun `parses sugar and fiber`() {
        val parsed = NutritionLabelParser.parse("Azucares añadidos 5 g\nFibra dietetica 4 g")

        assertEquals(5.0, parsed.sugarGrams!!, 0.1)
        assertEquals(4.0, parsed.fiberGrams!!, 0.1)
    }

    @Test
    fun `garbage text does not crash`() {
        val parsed = NutritionLabelParser.parse("@@@ etiqueta borrosa ###")

        assertNotNull(parsed)
        assertNull(parsed.calories)
        assertTrue(parsed.warnings.isNotEmpty())
    }
}
