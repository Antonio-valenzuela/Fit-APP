import java.util.zip.ZipFile
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.hilt.android)
}

val releaseKeystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
  ?.trim()
  ?.takeIf(String::isNotEmpty)
val releaseStorePassword = providers.environmentVariable("STORE_PASSWORD").orNull
  ?.takeIf(String::isNotBlank)
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
  ?.takeIf(String::isNotBlank)
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
  ?.takeIf(String::isNotBlank)
val configuredReleaseKeystore = releaseKeystorePath?.let(::File)
val releaseKeystoreFile = releaseKeystorePath?.let(::file)
val projectRootPath = rootDir.canonicalFile.toPath()
val releaseSigningProblems = buildList {
  when {
    releaseKeystorePath == null -> add("KEYSTORE_PATH is required")
    configuredReleaseKeystore?.isAbsolute != true -> add("KEYSTORE_PATH must be an absolute path")
    releaseKeystoreFile?.isFile != true -> add("KEYSTORE_PATH must reference an existing file")
    releaseKeystoreFile.canonicalFile.toPath().startsWith(projectRootPath) ->
      add("KEYSTORE_PATH must point outside the project directory")
  }
  if (releaseStorePassword == null) add("STORE_PASSWORD is required")
  if (releaseKeyAlias == null) add("KEY_ALIAS is required")
  if (releaseKeyPassword == null) add("KEY_PASSWORD is required")
}
val hasReleaseSigning = releaseSigningProblems.isEmpty()
val releaseArtifactRequested = gradle.startParameter.taskNames.any { requestedTask ->
  val taskName = requestedTask.substringAfterLast(':')
  taskName == "assembleRelease" ||
    taskName == "bundleRelease" ||
    taskName == "packageRelease" ||
    (taskName.startsWith("publish") && taskName.contains("Release"))
}

if (releaseArtifactRequested && !hasReleaseSigning) {
  throw GradleException(
    "Release signing is not configured securely: ${releaseSigningProblems.joinToString("; ")}. " +
      "See docs/security/release-signing.md."
  )
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.fitplanmx.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("boolean", "DEBUG_HITBOXES", "false")
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = requireNotNull(releaseKeystoreFile)
        storePassword = requireNotNull(releaseStorePassword)
        keyAlias = requireNotNull(releaseKeyAlias)
        keyPassword = requireNotNull(releaseKeyPassword)
      }
    }
  }

  buildTypes {
    release {
      buildConfigField("boolean", "PREMIUM_TEST_MODE", "false")
      buildConfigField("boolean", "EXERCISE_CATALOG_ENABLED", "false")
      isCrunchPngs = false
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
      buildConfigField("boolean", "DEBUG_HITBOXES", "false")
    }
    debug {
      buildConfigField("boolean", "PREMIUM_TEST_MODE", "true")
      buildConfigField("boolean", "EXERCISE_CATALOG_ENABLED", "true")
      buildConfigField("boolean", "DEBUG_HITBOXES", "true")
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  // MigrationTestHelper loads exported Room schemas as androidTest assets.
  // Keep the generated schema directory as the single source of truth.
  sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

val validateReleaseSigning by tasks.registering {
  group = "verification"
  description = "Fails closed unless release signing uses complete inputs and an external keystore."
  doLast {
    check(hasReleaseSigning) {
      "Release signing is not configured securely: ${releaseSigningProblems.joinToString("; ")}. " +
        "See docs/security/release-signing.md."
    }
  }
}

tasks.matching {
  it.name == "assembleRelease" ||
    it.name == "bundleRelease" ||
    it.name == "packageRelease" ||
    (it.name.startsWith("publish") && it.name.contains("Release"))
}.configureEach {
  dependsOn(validateReleaseSigning)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.incremental", "true")
  arg("room.expandProjection", "true")
}

// This root .env is Android-only. Backend settings live in the process environment;
// the exhaustive ignore list prevents accidental BuildConfig generation if a local
// developer places a server variable in the wrong file.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  // Server-to-server credentials must never be generated into Android BuildConfig.
  listOf(
    "BACKEND_API_KEY",
    "CORS_ALLOWED_ORIGINS",
    "DATABASE_URL",
    "DATABASE_TLS",
    "GEMINI_API_KEY",
    "GEMINI_MODEL",
    "GOOGLE_OAUTH_CLIENT_ID",
    "JWT_ALGORITHMS",
    "JWT_AUDIENCE",
    "JWT_ISSUER",
    "JWT_SECRET",
    "JWT_TTL_SECONDS",
    "NODE_ENV",
    "PORT",
    "PRE_AUTH_RATE_LIMIT_MAX",
    "RATE_LIMIT_WINDOW_MS",
    "RATE_LIMIT_STORE",
    "SHUTDOWN_TIMEOUT_MS",
    "TRUST_PROXY_HOPS",
    "USER_RATE_LIMIT_MAX",
    "GEMINI_USER_RATE_LIMIT_MAX"
  ).forEach(ignoreList::add)
}

val releaseApks = fileTree(layout.buildDirectory.dir("outputs/apk/release")) {
  include("*.apk")
}
val debugApks = fileTree(layout.buildDirectory.dir("outputs/apk/debug")) {
  include("*.apk")
}
val releaseBundle = layout.buildDirectory.file("outputs/bundle/release/app-release.aab")
val rootEnvironmentFile = rootProject.layout.projectDirectory.file(".env")

fun verifyAndroidArtifacts(artifacts: Collection<File>, environmentFile: File) {
    val sensitiveName = Regex(
      "(?:^|_)(?:API_?KEY|SECRET|TOKEN|PASSWORD|CREDENTIAL)(?:_|$)",
      RegexOption.IGNORE_CASE
    )
    fun usableSecret(value: String): String? = value
      .trim()
      .trim('"', '\'')
      .takeIf {
        it.length >= 8 &&
          !it.contains("YOUR_", ignoreCase = true) &&
          !it.contains("placeholder", ignoreCase = true) &&
          !it.contains("example", ignoreCase = true) &&
          !it.contains("changeme", ignoreCase = true)
      }

    val configuredSecrets = buildSet {
      environmentFile.takeIf { it.isFile }?.readLines()?.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith('#')) {
          val name = trimmed.substringBefore('=', "").trim()
          if (sensitiveName.containsMatchIn(name)) {
            usableSecret(trimmed.substringAfter('=', ""))?.let(::add)
          }
        }
      }
      System.getenv().forEach { (name, value) ->
        if (sensitiveName.containsMatchIn(name)) usableSecret(value)?.let(::add)
      }
    }

    val signatures = buildList<Pair<String, Regex>> {
      add("backend credential field" to Regex("BACKEND_API_KEY", RegexOption.IGNORE_CASE))
      add("Google-style API key" to Regex("AIza[0-9A-Za-z_-]{30,}"))
      add("provider-style secret" to Regex("sk-[0-9A-Za-z_-]{20,}"))
      add("Authorization Bearer credential" to Regex("Authorization\\s*:\\s*Bearer", RegexOption.IGNORE_CASE))
      add(
        "named API key, client secret, or token value" to
          Regex(
            "(?:api[_-]?key|client[_-]?secret|access[_-]?token|refresh[_-]?token|auth[_-]?token)" +
              "\\s*[\"']?\\s*[:=]\\s*[\"']?[0-9A-Za-z._~+/-]{8,}",
            RegexOption.IGNORE_CASE
          )
      )
      add("PEM private key" to Regex("-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----", RegexOption.IGNORE_CASE))
      add("JWT-shaped token" to Regex("eyJ[0-9A-Za-z_-]{8,}\\.[0-9A-Za-z_-]{8,}\\.[0-9A-Za-z_-]{8,}"))
      add("URL with embedded credentials" to Regex("https?://[^/\\s:@]+:[^/\\s@]+@"))
      configuredSecrets.forEach { credential ->
        add("configured backend credential" to Regex(Regex.escape(credential)))
        add(
          "base64-encoded backend credential" to
            Regex(Regex.escape(Base64.getEncoder().encodeToString(credential.toByteArray(Charsets.UTF_8))))
        )
      }
    }

    val findings = mutableListOf<String>()
    artifacts.forEach { artifact ->
      check(artifact.isFile) { "Missing Android artifact: ${artifact.name}" }
      ZipFile(artifact).use { archive ->
        archive.entries().asSequence()
          .filterNot { it.isDirectory }
          .forEach { entry ->
            val encodedViews = archive.getInputStream(entry).use { stream ->
              val bytes = stream.readBytes()
              listOf(
                String(bytes, Charsets.ISO_8859_1),
                String(bytes, Charsets.UTF_16LE),
                String(bytes, Charsets.UTF_16BE)
              )
            }
            signatures.forEach { (label, pattern) ->
              if (encodedViews.any(pattern::containsMatchIn)) {
                findings += "${artifact.name}:${entry.name}:$label"
              }
            }
          }
      }
    }

    check(findings.isEmpty()) {
      "Potential client secrets found (values suppressed): ${findings.distinct().joinToString()}"
    }
}

val scanDebugApkForSecrets by tasks.registering {
  group = "verification"
  description = "Rejects embedded credentials in the debug APK without printing their values."
  notCompatibleWithConfigurationCache("Scans generated ZIP entries and local sensitive environment values.")
  inputs.files(debugApks)
  inputs.file(rootEnvironmentFile).optional()
  doLast {
    check(debugApks.files.isNotEmpty()) { "Missing debug APK artifact" }
    verifyAndroidArtifacts(debugApks.files.sortedBy { it.name }, rootEnvironmentFile.asFile)
    logger.lifecycle("Debug APK secret scan passed; no secret values were printed.")
  }
}

val scanReleaseApkForSecrets by tasks.registering {
  group = "verification"
  description = "Rejects embedded credentials in the release APK without printing their values."
  notCompatibleWithConfigurationCache("Scans generated ZIP entries and a local optional environment file.")
  inputs.files(releaseApks)
  inputs.file(rootEnvironmentFile).optional()
  doLast {
    check(releaseApks.files.isNotEmpty()) { "Missing release APK artifact" }
    verifyAndroidArtifacts(releaseApks.files.sortedBy { it.name }, rootEnvironmentFile.asFile)
    logger.lifecycle("Release APK secret scan passed; no secret values were printed.")
  }
}

val scanReleaseBundleForSecrets by tasks.registering {
  group = "verification"
  description = "Rejects embedded credentials in the release AAB without printing their values."
  notCompatibleWithConfigurationCache("Scans generated ZIP entries and a local optional environment file.")
  inputs.file(releaseBundle)
  inputs.file(rootEnvironmentFile).optional()
  doLast {
    verifyAndroidArtifacts(listOf(releaseBundle.get().asFile), rootEnvironmentFile.asFile)
    logger.lifecycle("Release AAB secret scan passed; no secret values were printed.")
  }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
  finalizedBy(scanDebugApkForSecrets)
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
  finalizedBy(scanReleaseApkForSecrets)
}
tasks.matching { it.name == "bundleRelease" }.configureEach {
  finalizedBy(scanReleaseBundleForSecrets)
}

val verifyReleaseArtifactsHaveNoSecrets by tasks.registering {
  group = "verification"
  description = "Builds and scans both release APK and AAB for embedded client credentials."
  dependsOn("assembleRelease", "bundleRelease")
}

val verifyAndroidArtifactsHaveNoSecrets by tasks.registering {
  group = "verification"
  description = "Builds and scans the debug APK plus release APK and AAB for embedded credentials."
  dependsOn("assembleDebug", "assembleRelease", "bundleRelease")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.billing.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.googleid)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.maplibre.android)
  implementation(libs.moshi.kotlin)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.room.testing)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  "ksp"(libs.hilt.compiler)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  testImplementation(libs.turbine)
  implementation(libs.androidx.health.connect)
}
