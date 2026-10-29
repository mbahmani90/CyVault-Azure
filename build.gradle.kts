plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.ktlint) apply false
    // Applied here too so the root project can merge every module's coverage into one report
    alias(libs.plugins.kover)
}

val codeModules = listOf(
    ":androidApp",
    ":app",
    ":core",
    ":auth",
    ":systemdesign",
    ":feature:authentication",
    ":feature:vault",
    ":testtags",
)

dependencies {
    codeModules.forEach { kover(project(it)) }
}

subprojects {
    if (path in codeModules) {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "org.jetbrains.kotlinx.kover")

        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            // Plain-text reports for CI logs; checkstyle XML so the pipeline can show results
            coloredOutput.set(false)
            reporters {
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
            }
            filter {
                // Compose resource accessors and other generated sources
                exclude { it.file.path.contains("${File.separator}build${File.separator}") }
            }
        }
    }
}