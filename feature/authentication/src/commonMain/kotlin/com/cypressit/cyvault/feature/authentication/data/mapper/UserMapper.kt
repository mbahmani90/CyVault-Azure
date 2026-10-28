package com.cypressit.cyvault.feature.authentication.data.mapper

import com.cypressit.cyvault.auth.AuthUser
import com.cypressit.cyvault.feature.authentication.domain.model.User

/** The auth library's user as this feature's domain model. */
fun AuthUser.toDomain(): User = User(id = id, name = name, email = email)
