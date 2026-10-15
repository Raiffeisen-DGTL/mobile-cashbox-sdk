// Plain Android application consuming Mobile Cashbox as a project dependency. A production app
// would normally use the published Maven coordinate instead.
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "ru.raiffeisen.mobilecashbox.example.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.raiffeisen.mockuplib.example.android"
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
}

dependencies {
    implementation(project(":"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.10.4")
    implementation("androidx.compose.foundation:foundation:1.10.4")
    implementation("androidx.compose.material3:material3:1.4.0")
}
