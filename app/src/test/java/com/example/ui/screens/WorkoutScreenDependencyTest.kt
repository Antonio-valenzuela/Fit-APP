package com.example.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutScreenDependencyTest {
    @Test
    fun muscleMapViewModelUsesHiltFactoryInWorkoutScreen() {
        val source = workoutScreenSource()

        assertTrue(source.contains("import androidx.hilt.navigation.compose.hiltViewModel"))
        assertTrue(source.contains("val muscleViewModel: MuscleMapViewModel = hiltViewModel()"))
        assertFalse(source.contains("val muscleViewModel: MuscleMapViewModel = composeViewModel()"))
    }

    private fun workoutScreenSource(): String {
        val appRelative = File("src/main/java/com/example/ui/screens/WorkoutScreen.kt")
        val rootRelative = File("app/src/main/java/com/example/ui/screens/WorkoutScreen.kt")
        return when {
            appRelative.exists() -> appRelative.readText()
            rootRelative.exists() -> rootRelative.readText()
            else -> error("WorkoutScreen.kt source file was not found")
        }
    }
}
