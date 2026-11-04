// Kotlin plugins are declared once here so :core and :app share one Kotlin plugin classloader.
// The Android Gradle Plugin and google-services are declared in :app only, which lets
// `./gradlew :core:test -PcoreOnly` run on machines without Google's Maven repo or the Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
