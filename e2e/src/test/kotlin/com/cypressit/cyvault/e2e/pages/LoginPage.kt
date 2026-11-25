package com.cypressit.cyvault.e2e.pages

import com.cypressit.cyvault.testtags.LoginTags
import com.cypressit.cyvault.testtags.MfaTags
import com.cypressit.cyvault.testtags.VaultHomeTags
import io.appium.java_client.AppiumBy
import io.appium.java_client.AppiumDriver

class LoginPage(driver: AppiumDriver) : BasePage(driver) {
    fun enterEmail(email: String) = apply { find(LoginTags.EMAIL_FIELD).sendKeys(email) }

    fun enterPassword(password: String) = apply { find(LoginTags.PASSWORD_FIELD).sendKeys(password) }

    fun tapSignIn(): VaultHomePage {
        find(LoginTags.SIGN_IN_BUTTON).click()
        return VaultHomePage(driver)
    }

    /**
     * Signs in and waits for the home screen. When sign-in fails, it fails with the reason the app
     * shows: the error snackbar's text, or the MFA screen Entra asked for. The snackbar only stays a
     * few seconds, so it has to be read while waiting, not from the screenshot taken at the end.
     */
    fun signIn(email: String, password: String): VaultHomePage {
        enterEmail(email).enterPassword(password).tapSignIn()
        var failure: String? = null
        waitUntil {
            when {
                isShown(VaultHomeTags.ADD_ITEM_BUTTON) -> true
                isShown(MfaTags.PHONE_NUMBER_FIELD) || isShown(MfaTags.CODE_FIELD) -> {
                    failure = "Sign-in stopped at the MFA screen: Entra asked for a second factor"
                    true
                }
                isShown(LoginTags.SNACKBAR) -> {
                    failure = "Sign-in failed: ${snackbarText()}"
                    true
                }
                else -> false
            }
        }
        failure?.let { error(it) }
        return VaultHomePage(driver)
    }

    // Compose shows the message as a text element inside the snackbar host.
    private fun snackbarText(): String {
        val host = find(LoginTags.SNACKBAR)
        val texts = host.findElements(AppiumBy.className("android.widget.TextView")).map { it.text }
        return texts.filter { it.isNotBlank() }.joinToString(" | ").ifEmpty { host.text }
    }
}
