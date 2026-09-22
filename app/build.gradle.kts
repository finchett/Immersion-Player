import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. Point `immersionKeystore` (in ~/.gradle/gradle.properties or -P) at a
// properties file holding storeFile/storePassword/keyAlias/keyPassword. Without it, release
// builds fall back to the debug key so the project still builds for everyone else.
fun loadKeystoreProps(): Properties? {
    val path = project.findProperty("immersionKeystore") as String? ?: return null
    val propsFile = file(path)
    if (!propsFile.isFile) return null
    val loaded = Properties()
    propsFile.inputStream().use { stream -> loaded.load(stream) }
    return loaded
}

val keystoreProps: Properties? = loadKeystoreProps()

android {
    namespace = "io.github.immersionplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.immersionplayer"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.12.0-alpha03")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.12.0-alpha03")

    // shoulder triggers: shell-level access to the trigger sensors via Shizuku
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

}
