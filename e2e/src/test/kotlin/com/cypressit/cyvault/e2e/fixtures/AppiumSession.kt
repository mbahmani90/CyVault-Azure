package com.cypressit.cyvault.e2e.fixtures

import com.cypressit.cyvault.e2e.config.DriverFactory
import io.appium.java_client.AppiumDriver
import org.junit.rules.ExternalResource

/**
 * Fixture: starts the app in a fresh Appium session before each test and closes it after.
 * Cleanups registered with [cleanup] run first, newest first, even when the test failed.
 */
class AppiumSession : ExternalResource() {
    lateinit var driver: AppiumDriver
        private set

    private val cleanups = ArrayDeque<() -> Unit>()

    fun cleanup(action: () -> Unit) {
        cleanups.addFirst(action)
    }

    override fun before() {
        driver = DriverFactory.create()
    }

    override fun after() {
        if (!::driver.isInitialized) return
        // A failed cleanup only leaves test data behind; it must not hide the test's own result.
        cleanups.forEach { runCatching(it).onFailure { e -> System.err.println("E2E cleanup failed: $e") } }
        driver.quit()
    }
}
