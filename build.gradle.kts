// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  // The core `jacoco` plugin (no version) adds the coverage agent to every
  // Test task, which is how the app's unit-test coverage is measured. It is a
  // core Gradle plugin, so it needs no extra resolution from the plugin portal.
  jacoco
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}
