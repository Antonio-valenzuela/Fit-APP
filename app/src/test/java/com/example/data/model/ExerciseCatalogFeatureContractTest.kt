package com.example.data.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogFeatureContractTest {
    @Test
    fun `debug enables the catalog while release forces it off`() {
        val gradleFile = projectFile("build.gradle.kts").readText()
        val releaseBlock = gradleFile.substringAfter("release {").substringBefore("debug {")
        val debugBlock = gradleFile.substringAfter("debug {")

        assertTrue(
            releaseBlock.contains(
                "buildConfigField(\"boolean\", \"EXERCISE_CATALOG_ENABLED\", \"false\")"
            )
        )
        assertTrue(
            debugBlock.contains(
                "buildConfigField(\"boolean\", \"EXERCISE_CATALOG_ENABLED\", \"true\")"
            )
        )
    }

    @Test
    fun `preferences resolves both normal and corrupted fallbacks through the build gate`() {
        val preferencesSource = projectFile(
            "src/main/java/com/example/data/local/PreferencesManager.kt"
        ).readText()

        assertTrue(preferencesSource.contains("import com.example.BuildConfig"))
        assertTrue(preferencesSource.contains("feature_exercise_catalog_enabled"))
        assertEquals(
            2,
            Regex("""ExerciseCatalogFeaturePolicy\.resolve\(""")
                .findAll(preferencesSource)
                .count()
        )
        assertTrue(preferencesSource.contains("BuildConfig.EXERCISE_CATALOG_ENABLED"))
        assertFalse(
            Regex("""exerciseCatalogEnabled\s*=\s*prefs\.getBoolean""")
                .containsMatchIn(preferencesSource)
        )
    }

    @Test
    fun `catalog screen requests the complete current catalog instead of one repository page`() {
        val viewModelSource = projectFile(
            "src/main/java/com/example/ui/catalog/ExerciseCatalogViewModel.kt"
        ).readText()

        assertTrue(viewModelSource.contains("limit = EXERCISE_CATALOG_UI_LIMIT"))
        assertTrue(viewModelSource.contains("const val EXERCISE_CATALOG_UI_LIMIT = 2_000"))
    }

    private fun projectFile(relativePath: String): File {
        val moduleRelative = File(relativePath)
        if (File("src/main").isDirectory && moduleRelative.exists()) return moduleRelative
        return File("app", relativePath).also { file ->
            require(file.exists()) { "Project file not found: $relativePath" }
        }
    }
}
