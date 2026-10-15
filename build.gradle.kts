import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("multiplatform") version "2.2.21" apply false
    kotlin("android") version "2.2.21"
    kotlin("plugin.compose") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("com.android.library") version "8.13.2"
    id("com.android.application") version "8.13.2" apply false
    `maven-publish`
}

group = "ru.raiffeisen"
version = "1.0-SNAPSHOT"

android {
    namespace = "ru.raiffeisen.mobilecashbox"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    publishing {
        multipleVariants("all") {
            includeBuildTypeValues("debug", "release")
        }
    }
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-logging:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("androidx.activity:activity:1.13.0")

    // Compose is optional for consumers that only use MobileCashboxAndroidBridge.
    compileOnly("androidx.activity:activity-compose:1.13.0")

    testImplementation(kotlin("test"))
    testImplementation("androidx.compose.runtime:runtime:1.10.4")
    testImplementation("io.ktor:ktor-client-mock:3.5.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("all") {
                from(components["all"])
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/raiffeisen-dgtl/mobile-cashbox-sdk")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
