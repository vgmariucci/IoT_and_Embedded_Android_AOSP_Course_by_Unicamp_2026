// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // ✅ No Kotlin plugin here — AGP 9.x handles it
}