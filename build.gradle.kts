// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application").version("9.4.1").apply(false)
    // AGP 9 compiles Kotlin itself (built-in Kotlin): no kotlin-android plugin any more. Its
    // Kotlin Gradle plugin is pinned here so the compiler version is ours, not AGP's default.
    id("org.jetbrains.kotlin.plugin.compose").version("2.4.21").apply(false)
}
