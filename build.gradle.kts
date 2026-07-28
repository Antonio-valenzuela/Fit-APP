import java.security.MessageDigest
import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  id("com.google.devtools.ksp") version "2.3.5" apply false
  id("com.google.dagger.hilt.android") version "2.59.2" apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

allprojects {
  dependencyLocking {
    lockAllConfigurations()
  }
}

val expectedGradleVersion = "9.4.1"
val expectedGradleDistributionSha256 =
  "2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb"
val expectedGradleWrapperJarSha256 =
  "55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c"

fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
  .digest(readBytes())
  .joinToString("") { byte -> "%02x".format(byte) }

tasks.register("verifySupplyChainConfiguration") {
  group = "verification"
  description = "Verifies the pinned Gradle wrapper, centralized repositories, checksums, and dependency locks."

  doLast {
    val wrapperPropertiesFile = file("gradle/wrapper/gradle-wrapper.properties")
    val wrapperProperties = Properties().apply {
      wrapperPropertiesFile.inputStream().use(::load)
    }
    val distributionUrl = wrapperProperties.getProperty("distributionUrl").orEmpty()
    check(distributionUrl.endsWith("gradle-$expectedGradleVersion-bin.zip")) {
      "Gradle wrapper must use the approved $expectedGradleVersion binary distribution."
    }
    check(wrapperProperties.getProperty("distributionSha256Sum") == expectedGradleDistributionSha256) {
      "Gradle wrapper distribution SHA-256 is missing or does not match the official release checksum."
    }

    val wrapperJar = file("gradle/wrapper/gradle-wrapper.jar")
    check(wrapperJar.sha256() == expectedGradleWrapperJarSha256) {
      "Gradle wrapper JAR does not match the official $expectedGradleVersion checksum."
    }

    val settingsText = file("settings.gradle.kts").readText()
    check("RepositoriesMode.FAIL_ON_PROJECT_REPOS" in settingsText) {
      "Dependency repositories must remain centralized in settings.gradle.kts."
    }

    val verificationMetadata = file("gradle/verification-metadata.xml")
    check(verificationMetadata.isFile) {
      "Gradle dependency verification metadata is missing."
    }
    val verificationText = verificationMetadata.readText()
    check("<verify-metadata>true</verify-metadata>" in verificationText) {
      "Dependency metadata verification must remain enabled."
    }
    check("<sha256 value=" in verificationText) {
      "Dependency verification metadata must contain SHA-256 checksums."
    }

    val lockFiles = fileTree(rootDir) {
      include("**/gradle.lockfile")
      exclude(".gradle/**", "**/build/**")
    }.files
    check(lockFiles.isNotEmpty()) {
      "No Gradle dependency lockfile was found."
    }
  }
}
