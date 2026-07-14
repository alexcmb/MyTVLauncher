plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "crazyboyfeng.justTvLauncher"
    compileSdk = 35
    defaultConfig {
        applicationId = "crazyboyfeng.justTvLauncher"
        minSdk = 21
        targetSdk = 35
        // Overridable from CI so each tag produces an increasing version
        // (e.g. -PappVersionName=2026.7.14 -PappVersionCode=20260714).
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 20210913
        versionName = (project.findProperty("appVersionName") as String?) ?: "2021.9.13"
    }
    signingConfigs {
        // A fixed debug keystore committed to the repo so every build
        // (local and CI) shares one signature, allowing in-app updates.
        getByName("debug") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildFeatures {
        viewBinding = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.leanback:leanback:1.0.0")
}
