// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  id("com.google.devtools.ksp") version "2.3.5" apply false
  id("com.google.dagger.hilt.android") version "2.59.2" apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}