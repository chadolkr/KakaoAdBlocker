plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

// Function to calculate a hash of the src/main directory to detect changes
fun calculateSrcHash(dir: File): String {
    if (!dir.exists()) return ""
    val digest = MessageDigest.getInstance("SHA-256")
    dir.walkTopDown().sortedBy { it.absolutePath }.forEach { file ->
        if (file.isFile) {
            digest.update(file.absolutePath.toByteArray())
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val versionPropsFile = file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    // Initial configuration targets 1.1.0 (code 2) on first run
    versionProps["MAJOR"] = "1"
    versionProps["MINOR"] = "1"
    versionProps["PATCH"] = "-1"
    versionProps["BUILD_CODE"] = "1"
    versionProps["LAST_HASH"] = ""
}

val srcDir = file("src/main")
val currentHash = calculateSrcHash(srcDir)
val savedHash = versionProps.getProperty("LAST_HASH") ?: ""

var major = (versionProps.getProperty("MAJOR") ?: "1").toInt()
var minor = (versionProps.getProperty("MINOR") ?: "1").toInt()
var patch = (versionProps.getProperty("PATCH") ?: "-1").toInt()
var buildCode = (versionProps.getProperty("BUILD_CODE") ?: "1").toInt()

if (currentHash.isNotEmpty() && currentHash != savedHash) {
    patch += 1
    buildCode += 1
    versionProps["PATCH"] = patch.toString()
    versionProps["BUILD_CODE"] = buildCode.toString()
    versionProps["LAST_HASH"] = currentHash
    versionPropsFile.createNewFile()
    versionProps.store(FileOutputStream(versionPropsFile), "Auto-generated Version Properties")
}

val computedVersionName = "$major.$minor.$patch"
val computedVersionCode = buildCode

base {
  archivesName.set("KakaoAdBlocker_v$computedVersionName")
}

android {
    namespace = "com.example.kakaoadblocker"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.chadolkr.kakaoadblocker"
        minSdk = 24
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = computedVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
            storePassword = "kakaoadblocker123"
            keyAlias = "kakaokey"
            keyPassword = "kakaoadblocker123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
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
  implementation("androidx.compose.material:material-icons-core")
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
}
