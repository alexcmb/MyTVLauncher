// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application").version("8.7.3").apply(false)
    id("org.jetbrains.kotlin.android").version("2.1.0").apply(false)
    // Must track the Kotlin version: since Kotlin 2.0 the Compose compiler ships with it.
    id("org.jetbrains.kotlin.plugin.compose").version("2.1.0").apply(false)
}
