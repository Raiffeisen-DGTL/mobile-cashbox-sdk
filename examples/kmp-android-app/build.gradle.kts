// KMP consumer example. Mobile Cashbox is an Android AAR, so both the dependency and integration
// code intentionally live in androidMain rather than commonMain.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.application")
}

android {
    namespace = "ru.raiffeisen.mobilecashbox.example.kmpandroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.raiffeisen.mockuplib.example.kmpandroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation(project(":"))
            implementation("androidx.activity:activity-compose:1.13.0")
            implementation("androidx.compose.ui:ui:1.10.4")
            implementation("androidx.compose.foundation:foundation:1.10.4")
            implementation("androidx.compose.material3:material3:1.4.0")
        }
    }
}
