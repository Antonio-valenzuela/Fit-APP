package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidSecurityConfigurationTest {
    @Test
    fun mainAndReleaseDisallowCleartextWhileDebugScopesItToLoopbackOnly() {
        val mainManifest = source("src/main/AndroidManifest.xml")
        val debugManifest = source("src/debug/AndroidManifest.xml")

        assertTrue(mainManifest.contains("android:usesCleartextTraffic=\"false\""))
        assertFalse(mainManifest.contains("android:usesCleartextTraffic=\"true\""))
        assertTrue(debugManifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertFalse(debugManifest.contains("android:usesCleartextTraffic=\"true\""))
        assertFalse(debugManifest.contains("tools:replace=\"android:usesCleartextTraffic\""))

        val configFile = File("src/debug/res/xml/network_security_config.xml")
        assertTrue("Debug network security config is missing", configFile.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(configFile)
        val baseConfigs = document.getElementsByTagName("base-config")
        assertEquals(1, baseConfigs.length)
        assertEquals(
            "false",
            baseConfigs.item(0).attributes.getNamedItem("cleartextTrafficPermitted").nodeValue
        )

        val domainConfigs = document.getElementsByTagName("domain-config")
        assertEquals(1, domainConfigs.length)
        assertEquals(
            "true",
            domainConfigs.item(0).attributes.getNamedItem("cleartextTrafficPermitted").nodeValue
        )
        val domains = document.getElementsByTagName("domain")
        val allowed = (0 until domains.length).map { index -> domains.item(index).textContent.trim() }.toSet()
        assertEquals(setOf("localhost", "127.0.0.1", "10.0.2.2"), allowed)
    }

    @Test
    fun backupsAndNonLauncherComponentsAreFailClosed() {
        val mainManifest = source("src/main/AndroidManifest.xml")

        assertTrue(mainManifest.contains("android:allowBackup=\"false\""))
        assertTrue(mainManifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(mainManifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(
            Regex("<provider[\\s\\S]*?android:exported=\"false\"").containsMatchIn(mainManifest)
        )
    }

    @Test
    fun releaseSourcesDoNotLogHealthActionsExceptionsOrFoodPhotoMetadata() {
        val viewModel = source("src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt")
        val nutrition = source("src/main/java/com/example/ui/screens/NutritionScreen.kt")

        assertFalse(viewModel.contains("Gamification hook error for \$actionKey: \${e.message}"))
        assertFalse(viewModel.contains("Daily check-in hook error: \${e.message}"))
        assertFalse(nutrition.contains("Food photo prepared:"))
    }

    private fun source(path: String): String {
        val file = File(path)
        assertTrue("Missing source file: ${file.absolutePath}", file.isFile)
        return file.readText()
    }
}
