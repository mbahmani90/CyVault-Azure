package com.cypressit.cyvault.e2e.config

enum class Platform { ANDROID, IOS }

/** Settings for a test run; the e2eTest Gradle task passes them as system properties. */
object E2eConfig {
    val platform: Platform = Platform.valueOf(System.getProperty("e2e.platform", "android").uppercase())
    val appiumUrl: String = System.getProperty("e2e.appiumUrl", "http://127.0.0.1:4723")
    val app: String = System.getProperty("e2e.app") ?: error("Set the e2e.app system property to the APK path")

    // A test account in the debug Entra tenant. Environment variables, so they never land in git.
    val email: String get() = env("E2E_EMAIL")
    val password: String get() = env("E2E_PASSWORD")

    private fun env(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("Set the $name environment variable")
}
