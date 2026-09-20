plugins {
    // AGP 9.1.1 is compatible with the pinned Gradle 9.3.1 wrapper and
    // supports compileSdk 37. Keep plugin versions explicit so CI can resolve
    // the Android application/test and Compose compiler plugins.
    id("com.android.application") version "9.1.1" apply false
    id("com.android.test") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
