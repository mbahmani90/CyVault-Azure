package com.cypressit.cyvault.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.auth.AuthUser
import com.cypressit.cyvault.auth.SignInStep
import com.cypressit.cyvault.feature.authentication.di.authModule
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthNavEvent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real auth graph with the real screens: each [AuthNavEvent] lands on its route with the right back stack. */
@OptIn(ExperimentalTestApi::class) // runComposeUiTest
@RunWith(RobolectricTestRunner::class)
class AuthNavigationTest {

    @Before
    fun startDi() {
        startKoin { modules(authModule, module { single<AuthClient> { SignedOutAuthClient } }) }
    }

    @After
    fun stopDi() = stopKoin()

    private lateinit var navController: NavHostController

    private fun ComposeUiTest.showAuthGraph() {
        setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = AuthGraphRoute) {
                authGraph(navController)
                composable<VaultHomeRoute> { Text(HOME) }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.navigate(event: AuthNavEvent) {
        runOnIdle { navController.navigateAuth(event) }
        waitForIdle()
    }

    private fun ComposeUiTest.assertBackStack(vararg routes: String) = runOnIdle {
        val stack = navController.currentBackStack.value
            .mapNotNull { it.destination.route?.substringAfterLast('.')?.substringBefore('/')?.substringBefore('?') }
            .filter { it != AuthGraphRoute::class.simpleName }
        assertEquals(routes.toList(), stack)
    }

    @Test
    fun startsAtSignIn() = runComposeUiTest {
        showAuthGraph()
        assertBackStack("LoginRoute")
    }

    @Test
    fun registerThenVerification_replacesRegister() = runComposeUiTest {
        showAuthGraph()

        navigate(AuthNavEvent.Register)
        navigate(AuthNavEvent.Register)
        assertBackStack("LoginRoute", "RegisterRoute")

        navigate(AuthNavEvent.Verification("ada@example.com"))
        assertBackStack("LoginRoute", "VerificationRoute")
        runOnIdle {
            assertEquals("ada@example.com", navController.currentBackStackEntry!!.arguments!!.getString("email"))
        }
    }

    @Test
    fun mfaPhoneThenCode_replacesThePhoneStep() = runComposeUiTest {
        showAuthGraph()

        navigate(AuthNavEvent.MfaPhoneNumber)
        assertBackStack("LoginRoute", "MfaPhoneNumberRoute")

        navigate(AuthNavEvent.MfaCode(sentTo = "+351*****678", codeLength = 6))
        assertBackStack("LoginRoute", "MfaCodeRoute")
    }

    @Test
    fun forgotPassword_backToLoginWithResetSent_saysTheEmailWasSent() = runComposeUiTest {
        showAuthGraph()

        navigate(AuthNavEvent.ForgotPassword("ada@example.com"))
        assertBackStack("LoginRoute", "ForgotPasswordRoute")

        navigate(AuthNavEvent.BackToLogin(resetEmailSent = true))
        assertBackStack("LoginRoute")
        onNodeWithText("Password reset email sent", substring = true).assertIsDisplayed()
    }

    @Test
    fun backToLogin_withoutReset_justPops() = runComposeUiTest {
        showAuthGraph()

        navigate(AuthNavEvent.Register)
        navigate(AuthNavEvent.BackToLogin(resetEmailSent = false))
        assertBackStack("LoginRoute")
    }

    @Test
    fun home_clearsTheWholeAuthFlow() = runComposeUiTest {
        showAuthGraph()

        navigate(AuthNavEvent.Register)
        navigate(AuthNavEvent.Home)
        assertBackStack("VaultHomeRoute")
        onNodeWithText(HOME).assertIsDisplayed()
        runOnIdle { assertTrue(navController.previousBackStackEntry == null) }
    }

    /** Nobody is signed in; the screens only open, so nothing else is called. */
    private object SignedOutAuthClient : AuthClient {
        override suspend fun currentUser(): AuthUser? = null
        override suspend fun signIn(email: String, password: String): SignInStep = throw NotImplementedError()
        override suspend fun submitMfaPhoneNumber(phoneNumber: String): SignInStep = throw NotImplementedError()
        override suspend fun submitMfaCode(code: String): AuthUser = throw NotImplementedError()
        override suspend fun signUp(name: String, email: String, password: String) = throw NotImplementedError()
        override suspend fun confirmSignUp(email: String, code: String) = throw NotImplementedError()
        override suspend fun resetPassword(email: String) = throw NotImplementedError()
        override suspend fun signInWithGoogle(): AuthUser = throw NotImplementedError()
        override suspend fun accessToken(scopes: List<String>): String = throw NotImplementedError()
        override suspend fun signOut() = throw NotImplementedError()
    }

    private companion object {
        const val HOME = "home"
    }
}
