plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.xth.dualapp"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.xth.dualapp"
    // installExistingPackage 需要 API 28
    minSdk = 28
    targetSdk = 36
    versionCode = 5
    versionName = "1.0.4"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      // 自用：release 也用 debug 签名，方便直接装
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
  implementation(composeBom)
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
  implementation("androidx.core:core-ktx:1.16.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
