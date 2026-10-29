// End-to-end tests: drive the installed app from outside through an Appium server, the way a
// user would. Black-box on purpose, so this module depends only on :testtags, not on app code.
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    testImplementation(projects.testtags)
    testImplementation(libs.appium.javaClient)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

// Not part of `gradlew test`: these need an emulator and a running Appium server.
tasks.test {
    enabled = false
}

tasks.register<Test>("e2eTest") {
    description = "Runs the end-to-end tests against the debug APK on a connected device."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    dependsOn(":androidApp:assembleDebug")

    // Override with -Pe2e.<name>=<value>
    systemProperty("e2e.platform", providers.gradleProperty("e2e.platform").getOrElse("android"))
    systemProperty("e2e.appiumUrl", providers.gradleProperty("e2e.appiumUrl").getOrElse("http://127.0.0.1:4723"))
    systemProperty(
        "e2e.app",
        providers.gradleProperty("e2e.app").getOrElse(
            rootProject.file("androidApp/build/outputs/apk/debug/androidApp-debug.apk").absolutePath,
        ),
    )
    // Always run: the app or backend may have changed even when the test code hasn't.
    outputs.upToDateWhen { false }
}
