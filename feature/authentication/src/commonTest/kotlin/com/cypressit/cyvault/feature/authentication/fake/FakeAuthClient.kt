package com.cypressit.cyvault.feature.authentication.fake

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.auth.AuthUser
import com.cypressit.cyvault.auth.SignInStep

/** Nobody is signed in; the flows the tests don't use aren't faked. */
class FakeAuthClient : AuthClient {

    val resetPasswordEmails = mutableListOf<String>()

    /** What signIn() returns. */
    var signInStep: SignInStep = SignInStep.SignedIn(USER)

    /** What submitMfaPhoneNumber() returns. */
    var phoneNumberStep: SignInStep = SignInStep.CodeRequired(sentTo = "+351*****678", codeLength = 6)

    /** The code submitMfaCode() accepts; any other fails like Entra's wrong-code error. */
    var validMfaCode = "123456"

    val submittedPhoneNumbers = mutableListOf<String>()
    val submittedMfaCodes = mutableListOf<String>()

    override suspend fun currentUser(): AuthUser? = null

    override suspend fun resetPassword(email: String) {
        resetPasswordEmails += email
    }

    override suspend fun signIn(email: String, password: String): SignInStep = signInStep

    override suspend fun submitMfaPhoneNumber(phoneNumber: String): SignInStep {
        submittedPhoneNumbers += phoneNumber
        return phoneNumberStep
    }

    override suspend fun submitMfaCode(code: String): AuthUser {
        submittedMfaCodes += code
        if (code != validMfaCode) throw Exception(WRONG_CODE_MESSAGE)
        return USER
    }

    override suspend fun signUp(name: String, email: String, password: String) = throw NotImplementedError()

    override suspend fun confirmSignUp(email: String, code: String) = throw NotImplementedError()

    override suspend fun signInWithGoogle(): AuthUser = throw NotImplementedError()

    override suspend fun accessToken(scopes: List<String>): String = throw NotImplementedError()

    override suspend fun signOut() = throw NotImplementedError()

    companion object {
        val USER = AuthUser(id = "2ff862e2-8362-40b4-8070-7f9778f660cf", name = "Ada", email = "ada@example.com")
        const val WRONG_CODE_MESSAGE = "The code is incorrect"
    }
}
