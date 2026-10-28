package com.cypressit.cyvault.feature.vault.home.domain.repository

import com.cypressit.cyvault.feature.vault.home.domain.model.Account

interface AccountRepository {
    /** The signed-in user, or null when nobody is signed in. */
    suspend fun currentAccount(): Account?

    suspend fun signOut(): Result<Unit>
}
