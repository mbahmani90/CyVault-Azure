package com.cypressit.cyvault.e2e.config

import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import java.net.URI

object DriverFactory {
    fun create(): AppiumDriver = when (E2eConfig.platform) {
        Platform.ANDROID -> AndroidDriver(
            URI(E2eConfig.appiumUrl).toURL(),
            UiAutomator2Options()
                .setApp(E2eConfig.app)
                // Debug builds keep the same version code, so without this a device that already has
                // the app keeps the old build and the tests run against stale code.
                .setEnforceAppInstall(true)
                // Clears app data before each session, so every test starts signed out.
                .setNoReset(false)
                .setAutoGrantPermissions(true),
        )
        Platform.IOS -> TODO("Add an IOSDriver with XCUITestOptions once the iOS app is ready")
    }
}
