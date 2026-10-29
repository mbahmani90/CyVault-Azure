package com.cypressit.cyvault.e2e.fixtures

import com.cypressit.cyvault.e2e.config.E2eConfig
import com.cypressit.cyvault.e2e.pages.LoginPage
import com.cypressit.cyvault.e2e.pages.VaultHomePage

/** Fixture: signs in with the test account and returns the home screen. */
fun AppiumSession.signedIn(): VaultHomePage =
    LoginPage(driver).signIn(E2eConfig.email, E2eConfig.password)
