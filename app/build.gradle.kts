plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing material never lives in the repo: CI decodes the keystore from repository
// secrets and passes these in the environment, and a local release build can export the same.
// When they're absent — any debug build — the release type is simply left unsigned rather
// than failing the build.
val signingStore: String? = System.getenv("SIGNING_STORE_FILE")
val signingStorePass: String? = System.getenv("SIGNING_STORE_PASSWORD")
val signingAlias: String? = System.getenv("SIGNING_KEY_ALIAS")
val signingKeyPass: String? = System.getenv("SIGNING_KEY_PASSWORD")

android {
    namespace = "alexcmb.mytvlauncher"
    compileSdk = 35
    defaultConfig {
        applicationId = "alexcmb.mytvlauncher"
        minSdk = 21
        targetSdk = 35
        // Overridable from CI so each tag produces an increasing version
        // (e.g. -PappVersionName=2026.7.14 -PappVersionCode=20260714).
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 20210913
        versionName = (project.findProperty("appVersionName") as String?) ?: "2021.9.13"
    }
    signingConfigs {
        // Debug builds use the SDK's own per-machine debug key; nothing to configure.
        if (signingStore != null) {
            create("release") {
                storeFile = file(signingStore)
                storePassword = signingStorePass
                keyAlias = signingAlias
                keyPassword = signingKeyPass
            }
        }
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Null when no signing material was supplied: the APK comes out unsigned rather
            // than the build failing.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        // The pure logic under test never calls into the framework; this keeps any
        // incidental android.jar reference from throwing "not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Extracts the accent colour from a focused app's banner (the "auto" accent).
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Compose for TV — the whole UI.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.tv:tv-material:1.0.0")

    testImplementation("junit:junit:4.13.2")
}
