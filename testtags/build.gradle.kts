import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Test tag names shared by the UI (Modifier.testTag) and the :e2e tests (Appium locators),
// so both sides use the same constants. Plain Kotlin only: :e2e consumes the jvm target.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    // Linked into the app's iOS framework; only :app builds a framework.
    iosArm64()
    iosSimulatorArm64()

    // For :e2e, which runs on the JVM
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    android {
        namespace = "com.cypressit.cyvault.testtags"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
}
