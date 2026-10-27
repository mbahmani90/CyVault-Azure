import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    // Linked into the app's iOS framework; only :app builds a framework.
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.cypressit.cyvault.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Koin
            implementation(libs.koin.core)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // Ktor Android engine (OkHttp)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            // Ktor iOS engine (Darwin / NSURLSession)
            implementation(libs.ktor.client.darwin)
        }
    }
}
