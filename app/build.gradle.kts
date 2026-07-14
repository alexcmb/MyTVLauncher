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
        versionCode = 20210913
        versionName = "2021.9.13"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.leanback:leanback:1.0.0")
}
