import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            // Named CyVaultApp to avoid conflicting with Swift's built-in App protocol
            baseName = "CyVaultApp"
            isStatic = true
            // Swift implements IosAuthBridge from :feature:authentication
            export(projects.feature.authentication)
        }
    }

    android {
        namespace = "com.cypressit.cyvault.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Core infrastructure
            implementation(projects.core)

            // Feature modules — add every new feature here
            api(projects.feature.authentication)
            implementation(projects.feature.vault)

            // Compose — used directly in App.kt and MainViewController.kt
            implementation(libs.compose.runtime)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(compose.materialIconsExtended)

            // Koin — used directly in AppModule.kt and MainViewController.kt
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Navigation
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
