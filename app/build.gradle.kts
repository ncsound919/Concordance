import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.concordance.rkzv"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.0.0"

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
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
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
      signingConfig = signingConfigs.getByName("debugConfig")
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

  // Robolectric's SDK 36 sandbox refuses to start on a JDK below 21 ("Android SDK 36 requires
  // Java 21"). Pin only the unit-test JVM so the suite behaves the same on a JDK 17 machine as
  // on a JDK 21 one, without forcing the app's compile target upward.
  tasks.withType<Test>().configureEach {
    javaLauncher.set(
      javaToolchains.launcherFor {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
      }
    )
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

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
  // On-device speech: sherpa-onnx runs Kokoro with in-process espeak-ng G2P.
  implementation(files("libs/sherpa-onnx-1.13.8.aar"))
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // OkHttp is used directly by every HTTP client in this app; it used to arrive transitively
  // through Retrofit, which has been removed.
  implementation(libs.okhttp)
  // PDF text extraction for the local corpus (Apache-2.0 port of PDFBox).
  implementation(libs.pdfbox.android)
  // Walking the SAF tree the user grants for the corpus.
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.security.crypto)
  // ONNX Runtime is provided inside libs/sherpa-onnx-1.13.8.aar; adding the standalone
  // onnxruntime-android dependency would ship a second, conflicting libonnxruntime.so.
  // implementation(libs.onnxruntime.android)
  // implementation(libs.play.services.location)
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
  coreLibraryDesugaring(libs.desugar.jdk.libs)
}

/**
 * Fail in seconds when the upload key is missing, instead of minutes into a
 * release build. The signing config reads the same three inputs, so this task
 * checks exactly what the build will use and reports every missing piece at
 * once rather than stopping at the first.
 *
 * The values are Providers, not captured strings: the configuration cache
 * forbids a task action from touching the Project, and it must not freeze an
 * environment that CI sets per job.
 *
 * See docs/RELEASE.md.
 */
val releaseKeystorePath = providers.environmentVariable("KEYSTORE_PATH").orElse("${rootDir}/my-upload-key.jks")
val releaseStorePassword = providers.environmentVariable("STORE_PASSWORD")
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD")

tasks.register("verifyReleaseSigning") {
  group = "verification"
  description = "Fails fast when the upload keystore or its passwords are missing."
  val keystore = releaseKeystorePath
  val storePassword = releaseStorePassword
  val keyPassword = releaseKeyPassword
  doLast {
    val path = keystore.get()
    val missing = buildList {
      if (!File(path).isFile) add("keystore file not found: $path")
      if (storePassword.orNull.isNullOrBlank()) add("STORE_PASSWORD is unset")
      if (keyPassword.orNull.isNullOrBlank()) add("KEY_PASSWORD is unset")
    }
    if (missing.isNotEmpty()) {
      throw GradleException(
        "Release signing is not configured:\n  " + missing.joinToString("\n  ") +
          "\nSee docs/RELEASE.md. CI reads these from repository secrets."
      )
    }
  }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
  dependsOn("verifyReleaseSigning")
}
