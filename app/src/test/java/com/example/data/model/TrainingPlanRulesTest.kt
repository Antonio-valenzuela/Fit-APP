package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanRulesTest {
    @Test
    fun `level place and days recalculate the visible plan`() {
        val beginnerHome = TrainingPlanRules.defaults(
            level = "Principiante",
            trainingDays = "2",
            trainingPlace = "Casa"
        )
        val advancedGym = TrainingPlanRules.defaults(
            level = "Avanzado",
            trainingDays = "5",
            trainingPlace = "Gimnasio"
        )

        assertEquals("Ligera", beginnerHome.intensity)
        assertEquals("Peso corporal y equipo básico", beginnerHome.equipment)
        assertEquals("Completar 2 entrenamientos", beginnerHome.weeklyGoal)
        assertEquals("Alta", advancedGym.intensity)
        assertEquals("Máquinas, barras y mancuernas", advancedGym.equipment)
        assertEquals("Completar 5 entrenamientos", advancedGym.weeklyGoal)
    }

    @Test
    fun `stored plan values remain readable without a room migration`() {
        val user = UserEntity(
            goal = "Ganar músculo | Nivel: Intermedio | Días: 4 | Lugar: Ambos | " +
                "Equipo: Peso corporal y equipo de gimnasio | Intensidad: Moderada | " +
                "Meta: Completar 4 entrenamientos | Restricciones: Rodilla sensible"
        )

        assertEquals("Intermedio", user.getTrainingLevel())
        assertEquals("4", user.getTrainingDays())
        assertEquals("Ambos", user.getTrainingPlace())
        assertEquals("Rodilla sensible", user.getTrainingLimitations())
    }

    @Test
    fun `legacy or missing training place is treated as any location`() {
        assertEquals("Casa", UserEntity(goal = "Mantener peso | Lugar: Casa").getTrainingPlace())
        assertEquals("Gimnasio", UserEntity(goal = "Mantener peso | Lugar: Gimnasio").getTrainingPlace())
        assertEquals("Ambos", UserEntity(goal = "Mantener peso | Lugar: Ambos").getTrainingPlace())
        assertEquals("Ambos", UserEntity(goal = "Mantener peso").getTrainingPlace())
        assertEquals("Ambos", UserEntity(goal = "Mantener peso | Lugar: valor antiguo").getTrainingPlace())
    }

    @Test
    fun `training place writer is canonical and preserves other plan segments`() {
        val goal = "Ganar músculo | Nivel: Intermedio | Días: 4 | Restricciones: Rodilla sensible"

        assertEquals(
            "Ganar músculo | Nivel: Intermedio | Días: 4 | Restricciones: Rodilla sensible | Lugar: Casa",
            goal.withCanonicalTrainingPlace("casa")
        )
        assertEquals("Casa", UserEntity(goal = goal.withCanonicalTrainingPlace("casa")).getTrainingPlace())
    }

    @Test
    fun `training place writer removes duplicates and normalizes unknown values to both`() {
        val duplicated = "Mantener peso | Lugar: Casa | Nivel: Avanzado | lugar: Gimnasio | Días: 5"

        assertEquals(
            "Mantener peso | Nivel: Avanzado | Días: 5 | Lugar: Ambos",
            duplicated.withCanonicalTrainingPlace("sitio antiguo")
        )
        assertEquals("Lugar: Ambos", "".withCanonicalTrainingPlace("  "))
        assertEquals("Ambos", UserEntity(goal = duplicated.withCanonicalTrainingPlace("?")).getTrainingPlace())
    }

    @Test
    fun `training place can switch home gym and home without losing plan data`() {
        val original = "Ganar músculo | Nivel: Avanzado | Días: 5 | Lugar: Casa | Restricciones: Ninguna"
        val gym = original.withTrainingPlace("Gimnasio")
        val homeAgain = gym.withTrainingPlace("Casa")

        assertEquals("Gimnasio", UserEntity(goal = gym).getTrainingPlace())
        assertEquals("Casa", UserEntity(goal = homeAgain).getTrainingPlace())
        assertTrue("Nivel: Avanzado" in homeAgain)
        assertTrue("Días: 5" in homeAgain)
        assertTrue("Restricciones: Ninguna" in homeAgain)
    }
}
