plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

fun String.asBuildConfigString(): String = buildString {
  append('"')
  this@asBuildConfigString.forEach { char ->
    when (char) {
      '\\' -> append("\\\\")
      '"' -> append("\\\"")
      else -> append(char)
    }
  }
  append('"')
}

val deepSeekApiKey = providers.gradleProperty("DEEPSEEK_API_KEY").orElse(providers.environmentVariable("DEEPSEEK_API_KEY")).getOrElse("")
val deepSeekBaseUrl = providers.gradleProperty("DEEPSEEK_BASE_URL").orElse(providers.environmentVariable("DEEPSEEK_BASE_URL")).getOrElse("https://api.deepseek.com")
val deepSeekModel = providers.gradleProperty("DEEPSEEK_MODEL").orElse(providers.environmentVariable("DEEPSEEK_MODEL")).getOrElse("deepseek-chat")
val defaultWorkflowPack = providers.gradleProperty("OPENWHALE_WORKFLOW_PACK").orElse(providers.environmentVariable("OPENWHALE_WORKFLOW_PACK")).getOrElse("navigation_hotel")

android {
    namespace = "com.example.openwhale"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.openwhale"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DEEPSEEK_API_KEY", deepSeekApiKey.asBuildConfigString())
        buildConfigField("String", "DEEPSEEK_BASE_URL", deepSeekBaseUrl.asBuildConfigString())
        buildConfigField("String", "DEEPSEEK_MODEL", deepSeekModel.asBuildConfigString())
        buildConfigField("String", "DEFAULT_WORKFLOW_PACK", defaultWorkflowPack.asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Networking and JSON
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
}
