package com.example.data.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumBuildTypeContractTest {
    @Test
    fun `debug enables premium test mode and release forces it off`() {
        val gradleFile = projectFile("build.gradle.kts").readText()
        val policySource = projectFile(
            "src/main/java/com/example/data/model/PremiumAccessPolicy.kt"
        ).readText()

        assertTrue(
            Regex(
                """release\s*\{\s*buildConfigField\("boolean",\s*"PREMIUM_TEST_MODE",\s*"false"\)"""
            ).containsMatchIn(gradleFile)
        )
        assertTrue(
            Regex(
                """debug\s*\{\s*buildConfigField\("boolean",\s*"PREMIUM_TEST_MODE",\s*"true"\)"""
            ).containsMatchIn(gradleFile)
        )
        assertTrue(
            policySource.contains("BuildConfig.DEBUG && BuildConfig.PREMIUM_TEST_MODE")
        )
    }

    @Test
    fun `legacy global force unlock flag cannot return`() {
        val sourceRoot = projectFile("src/main")
        val containsLegacyFlag = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "kts", "java") }
            .any { it.readText().contains("FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING") }

        assertFalse(containsLegacyFlag)
    }

    @Test
    fun `QA bypass cannot persist a fake purchase or subscription`() {
        val viewModelSource = projectFile(
            "src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt"
        ).readText()

        assertFalse(viewModelSource.contains("patchPremiumAccess"))
        assertEquals(1, Regex("""prefs\.setPremiumUnlocked\(""").findAll(viewModelSource).count())
        assertTrue(viewModelSource.contains("prefs.setPremiumUnlocked(enabled)"))
        assertTrue(viewModelSource.contains("current.copy(isPremium = enabled)"))
    }

    private fun projectFile(relativePath: String): File {
        val moduleRelative = File(relativePath)
        if (File("src/main").isDirectory && moduleRelative.exists()) return moduleRelative
        return File("app", relativePath).also { file ->
            require(file.exists()) { "Project file not found: $relativePath" }
        }
    }
}
