import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.Test

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  // Core Gradle plugin: adds the JaCoCo coverage agent to every Test task.
  id("jacoco")
}

// A coverage run can be given its own build directory:
//   ./gradlew -Pconcordance.buildDir=build-coverage :app:testDebugUnitTest
// Two Gradle builds in one project directory serialise on the build lock, so
// the parallel coverage jobs (one per test area) each need their own output
// directory.
val buildDirOverride = providers.gradleProperty("concordance.buildDir").orNull
if (buildDirOverride != null) {
  layout.buildDirectory.set(File(rootDir, buildDirOverride))
}

android {
  namespace = "com.example"
  // 37 is required by the current Compose BOM / lifecycle libraries; targetSdk
  // below stays at 36, so this only widens what we compile against.
  compileSdk { version = release(37) }

  defaultConfig {
    // Distinct from the private edition so both can be installed side by side.
    applicationId = "com.aistudio.concordance.rkzv.oss"
    minSdk = 24
    targetSdk = 36
    // Must exceed the version already installed on the device (versionCode 3,
    // versionName 1.0.1) or adb refuses the install as a downgrade.
    versionCode = 4
    versionName = "1.0.2"

    // The embedded llama.cpp and sherpa-onnx runtimes are arm64-only.
    ndk { abiFilters += listOf("arm64-v8a") }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Native runtimes must be extracted from the APK: Android only grants execute permission
  // to binaries in nativeLibraryDir, never to files in app storage.
  packaging {
    jniLibs { useLegacyPackaging = true }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Robolectric's Android SDK 36 sandbox requires JDK 21 at test runtime
// ("Android SDK 36 requires Java 21"); a daemon running on JDK 17 fails every
// Robolectric test at class init. Pin the *test* JVM to a JDK 21 toolchain so
// the build no longer depends on a machine-specific `org.gradle.java.home`.
tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
    // Coverage instrumentation. `includeNoLocationClasses` keeps the classes
    // with no debug metadata (which would otherwise be reported as untested
    // noise) out of the report.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*", "kotlin.*", "androidx.*")
    }
}

/**
 * Line coverage of the app's own code, from the unit-test suite.
 *
 *   ./gradlew :app:jacocoTestReport
 *
 * The HTML report lands in `app/build/reports/jacoco/jacocoTestReport/html`
 * and the XML in `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`.
 * The class directories are collected from the build output rather than from a
 * hard-coded path, because the AGP output layout changes between versions.
 */
tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates the JaCoCo coverage report for the debug unit tests."
    dependsOn(tasks.withType<Test>())

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/java"))
    // The app's own compiled classes. The output layout differs between AGP
    // versions (AGP 8 used `tmp/kotlin-classes`, AGP 9 uses
    // `intermediates/built_in_kotlinc`), so the directories are globbed rather
    // than hard-coded. Only the debug *main* variant is included: the unit
    // test classes and the release variant are not app code.
    classDirectories.setFrom(
        files(layout.buildDirectory.dir("intermediates")).asFileTree.matching {
            include("**/compileDebugKotlin/classes/**/*.class")
            include("**/compileDebugJavaWithJavac/classes/**/*.class")
            include("**/kotlin-classes/debug/**/*.class")
        }
    )
    executionData.setFrom(
        tasks.withType<Test>().map { test ->
            test.extensions.getByType<JacocoTaskExtension>().destinationFile
        }
    )
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Room schema export (ConcordanceDatabase has exportSchema = true). Exporting the
// versioned schema JSONs into source control lets migration tests validate each
// manual migration against the real prior schema.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation("com.overlay:kotlin-core:1.0.0")
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
  // On-device speech: sherpa-onnx runs Kokoro with in-process espeak-ng G2P.
  implementation(files("libs/sherpa-onnx-1.13.8.aar"))
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.androidx.security.crypto)
  // ONNX Runtime is provided inside libs/sherpa-onnx-1.13.8.aar; adding the standalone
  // onnxruntime-android dependency would ship a second, conflicting libonnxruntime.so.
  // implementation(libs.onnxruntime.android)
  // implementation(libs.play.services.location)
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
  testImplementation(libs.roborazzi.compose.preview.scanner.support)
  testImplementation(libs.mockwebserver)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  coreLibraryDesugaring(libs.desugar.jdk.libs)
}
