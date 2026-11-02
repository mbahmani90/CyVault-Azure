package com.cypressit.cyvault.feature.authentication.data.mapper

import com.cypressit.cyvault.auth.AuthUser
import com.cypressit.cyvault.auth.SignInStep
import com.cypressit.cyvault.feature.authentication.domain.model.LoginResult
import com.cypressit.cyvault.feature.authentication.domain.model.User

/** The auth library's user as this feature's domain model. */
fun AuthUser.toDomain(): User = User(id = id, name = name, email = email)

fun SignInStep.toDomain(): LoginResult = when (this) {
    is SignInStep.SignedIn -> LoginResult.SignedIn(user.toDomain())
    SignInStep.PhoneNumberRequired -> LoginResult.PhoneNumberRequired
    is SignInStep.CodeRequired -> LoginResult.CodeRequired(sentTo = sentTo, codeLength = codeLength)
}
