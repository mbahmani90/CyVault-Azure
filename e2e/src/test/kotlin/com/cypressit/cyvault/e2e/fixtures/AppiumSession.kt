package com.cypressit.cyvault.e2e.fixtures

import com.cypressit.cyvault.e2e.config.DriverFactory
import io.appium.java_client.AppiumDriver
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.openqa.selenium.OutputType
import java.io.File

/**
 * Fixture: starts the app in a fresh Appium session before each test and closes it after.
 * Cleanups registered with [cleanup] run first, newest first, even when the test failed. A failed
 * test leaves the screen it ended on in build/reports/e2e-failures, so CI shows where it stopped.
 */
class AppiumSession : TestRule {
    lateinit var driver: AppiumDriver
        private set

    private val cleanups = ArrayDeque<() -> Unit>()

    fun cleanup(action: () -> Unit) {
        cleanups.addFirst(action)
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            driver = DriverFactory.create()
            try {
                base.evaluate()
            } catch (failure: Throwable) {
                saveScreen(description)
                throw failure
            } finally {
                // A failed cleanup only leaves test data behind; it must not hide the test's own result.
                cleanups.forEach { runCatching(it).onFailure { e -> System.err.println("E2E cleanup failed: $e") } }
                driver.quit()
            }
        }
    }

    // Before the cleanups run, which leave the home screen.
    private fun saveScreen(description: Description) {
        val dir = File("build/reports/e2e-failures").apply { mkdirs() }
        val name = "${description.testClass.simpleName}.${description.methodName}"
        runCatching { File(dir, "$name.xml").writeText(driver.pageSource.orEmpty()) }
            .onFailure { System.err.println("E2E couldn't save the page source: $it") }
        runCatching { driver.getScreenshotAs(OutputType.FILE).copyTo(File(dir, "$name.png"), overwrite = true) }
            .onFailure { System.err.println("E2E couldn't save a screenshot: $it") }
    }
}
