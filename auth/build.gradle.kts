import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// Sign-in and session for Microsoft Entra External ID (MSAL native auth on Android, a Swift
// bridge on iOS). No app or feature types and no CyVault IDs: the app passes its MSAL config,
// so the module can be reused in another project.
kotlin {
    // Linked into the app's iOS framework; only :app builds a framework.
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.cypressit.cyvault.auth"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.msal)
        }
    }
}
