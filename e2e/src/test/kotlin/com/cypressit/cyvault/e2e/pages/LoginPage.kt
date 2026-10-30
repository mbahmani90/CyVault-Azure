package com.cypressit.cyvault.e2e.pages

import com.cypressit.cyvault.testtags.LoginTags
import io.appium.java_client.AppiumDriver

class LoginPage(driver: AppiumDriver) : BasePage(driver) {
    fun enterEmail(email: String) = apply { find(LoginTags.EMAIL_FIELD).sendKeys(email) }

    fun enterPassword(password: String) = apply { find(LoginTags.PASSWORD_FIELD).sendKeys(password) }

    fun tapSignIn(): VaultHomePage {
        find(LoginTags.SIGN_IN_BUTTON).click()
        return VaultHomePage(driver)
    }

    fun signIn(email: String, password: String): VaultHomePage =
        enterEmail(email).enterPassword(password).tapSignIn()
}
