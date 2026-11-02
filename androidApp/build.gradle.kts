import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// The upload key's keystore and passwords live in the git-ignored local.properties
val localProperties =
    Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
    }

// CD (.github/workflows/cd.yml) passes the upload key in environment variables instead
fun signingValue(
    env: String,
    property: String,
): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProperties.getProperty(property)

val releaseStoreFile = signingValue("CYVAULT_KEYSTORE_FILE", "release.storeFile")

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Firebase project config (push notifications) from google-services.json
    alias(libs.plugins.googleServices)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    implementation(projects.app)
    // For MsalConfig: the shell hands its MSAL config file to :auth
    implementation(projects.auth)
    // Developer tools, such as the vault's sample items: never in a release build
    debugImplementation(projects.feature.vaultDebug)
    // For CyVaultApiConfig: debug builds point the vault at the debug environment's API
    debugImplementation(projects.feature.vault)
    // For ForgotPasswordPreview: debug builds show the unfinished Forgot Password
    debugImplementation(projects.feature.authentication)

    implementation(libs.koin.android)
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.cypressit.cyvault"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.cypressit.cyvault"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0"
        // The MSAL redirect URI's signing key hash, not URL-encoded. Release: the Play app signing
        // key's hash (Play Console → Test and release → App integrity → App signing).
        // Must match redirect_uri in src/main/res/raw/auth_config_native_auth.json.
        manifestPlaceholders["msalSignatureHash"] = "vEllEfJ8DIztUlq9ZEKO6kmPMDs="
    }
    signingConfigs {
        create("release") {
            releaseStoreFile?.let { storeFile = file(it) }
            storePassword = signingValue("CYVAULT_KEYSTORE_PASSWORD", "release.storePassword")
            keyAlias = signingValue("CYVAULT_KEY_ALIAS", "release.keyAlias")
            keyPassword = signingValue("CYVAULT_KEY_PASSWORD", "release.keyPassword")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            // The debug keystore's hash; matches src/debug/res/raw/auth_config_native_auth.json
            manifestPlaceholders["msalSignatureHash"] = "+6PpzKBqQ/0GponbMHq4NRPWiMc="
        }
        getByName("release") {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    lint {
        // The library modules have no lint tasks of their own, so lint them from the app
        checkDependencies = true
        xmlReport = true
        htmlReport = true
    }
}

// A release's version comes from its tag (cd.yml: v1.2.3 → -Pcyvault.versionName=1.2.3
// -Pcyvault.versionCode=10203). The variant API runs after android {}, so it wins over defaultConfig;
// without the properties, local builds keep defaultConfig's version.
androidComponents {
    val versionName = providers.gradleProperty("cyvault.versionName").orNull
    val versionCode = providers.gradleProperty("cyvault.versionCode").orNull?.toInt()
    onVariants { variant ->
        variant.outputs.forEach { output ->
            versionName?.let { output.versionName.set(it) }
            versionCode?.let { output.versionCode.set(it) }
        }
    }
}
