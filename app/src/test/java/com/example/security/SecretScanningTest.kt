package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class SecretScanningTest {

    private val apiPrefixPattern = Regex("AIzaSy[A-Za-z0-9_\\-]{33}")

    @Test
    fun scanBuildConfigForSecrets() {
        try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val fields = buildConfigClass.declaredFields
            for (field in fields) {
                if (Modifier.isStatic(field.modifiers) && field.type == String::class.java) {
                    val value = field.get(null) as? String ?: continue

                    // Verify that no field value looks like a Google API Key
                    assertFalse(
                        "Alerta de Seguridad: Se encontro una API Key de Google (AIzaSy...) hardcodeada en el campo BuildConfig.${field.name}!",
                        value.contains(apiPrefixPattern) ||
                        (field.name.contains("GEMINI_API_KEY", ignoreCase = true) &&
                         !value.contains("MY_GEMINI") &&
                         !value.contains("placeholder") &&
                         value.trim().length > 25)
                    )
                }
            }
        } catch (e: ClassNotFoundException) {
            // BuildConfig might not be generated yet in some build environments, which is fine
            println("BuildConfig class not found, skipping reflection secret check.")
        }
    }

    @Test
    fun scanSourceFilesForSecrets() {
        val srcDir = File("src/main/java")
        if (!srcDir.exists()) {
            println("Source directory not found: ${srcDir.absolutePath}")
            return
        }

        val kotlinFiles = srcDir.walkBottomUp().filter { it.extension == "kt" }
        for (file in kotlinFiles) {
            val content = file.readText()

            // Check for Google API key prefix pattern
            val match = apiPrefixPattern.find(content)
            if (match != null) {
                assertFalse(
                    "Alerta de Seguridad: Se detecto una API Key de Google hardcodeada en el archivo: ${file.name}! Encontrado: ${match.value}",
                    true
                )
            }
        }
    }
}
