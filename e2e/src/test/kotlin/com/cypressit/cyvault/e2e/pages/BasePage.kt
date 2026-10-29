package com.cypressit.cyvault.e2e.pages

import com.cypressit.cyvault.e2e.config.E2eConfig
import com.cypressit.cyvault.e2e.config.Platform
import io.appium.java_client.AppiumBy
import io.appium.java_client.AppiumDriver
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

abstract class BasePage(protected val driver: AppiumDriver) {
    // Sign-in and saving go over the network to Azure, so allow for a slow backend.
    private val wait = WebDriverWait(driver, Duration.ofSeconds(20))

    /** A Modifier.testTag: a resource ID on Android, an accessibility ID on iOS. */
    protected fun byTag(tag: String): By = when (E2eConfig.platform) {
        // Compose exposes the tag without the usual "<package>:id/" prefix, so match it exactly.
        Platform.ANDROID -> AppiumBy.androidUIAutomator("new UiSelector().resourceId(\"$tag\")")
        Platform.IOS -> AppiumBy.accessibilityId(tag)
    }

    /** An element showing exactly [text]. */
    protected fun byText(text: String): By = when (E2eConfig.platform) {
        Platform.ANDROID -> AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")")
        Platform.IOS -> AppiumBy.iOSNsPredicateString("label == '$text'")
    }

    protected fun find(tag: String): WebElement =
        wait.until(ExpectedConditions.visibilityOfElementLocated(byTag(tag)))

    protected fun findAll(tag: String): List<WebElement> = driver.findElements(byTag(tag))

    protected fun isShown(tag: String): Boolean = findAll(tag).any { it.isDisplayed }

    protected fun longPress(element: WebElement) {
        val id = (element as RemoteWebElement).id
        when (E2eConfig.platform) {
            // Well past Android's long-press timeout; the default is too close to it to count reliably.
            Platform.ANDROID -> driver.executeScript(
                "mobile: longClickGesture",
                mapOf("elementId" to id, "duration" to 1000),
            )
            Platform.IOS -> driver.executeScript("mobile: touchAndHold", mapOf("elementId" to id, "duration" to 1.0))
        }
    }

    protected fun waitUntil(condition: () -> Boolean): Boolean =
        runCatching { wait.until { condition() } }.isSuccess
}
