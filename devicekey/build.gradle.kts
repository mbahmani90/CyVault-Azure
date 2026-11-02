import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// The device's biometric-protected signing keys (Android Keystore; the iOS Secure Enclave later).
// No app or feature types: features enroll and use the keys through DeviceKeyStore.
kotlin {
    // Linked into the app's iOS framework; only :app builds a framework.
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.cypressit.cyvault.devicekey"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}
