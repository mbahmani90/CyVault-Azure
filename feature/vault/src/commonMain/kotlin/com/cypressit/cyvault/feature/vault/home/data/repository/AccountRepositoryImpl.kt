package com.cypressit.cyvault.feature.vault.home.data.repository

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.core.coroutines.runSuspendCatching
import com.cypressit.cyvault.feature.vault.home.data.remote.SessionApiService
import com.cypressit.cyvault.feature.vault.home.domain.model.Account
import com.cypressit.cyvault.feature.vault.home.domain.repository.AccountRepository

class AccountRepositoryImpl(
    private val authClient: AuthClient,
    private val sessionApi: SessionApiService,
) : AccountRepository {

    override suspend fun currentAccount(): Account? =
        authClient.currentUser()?.let { Account(id = it.id, name = it.name, email = it.email) }

    override suspend fun signOut(): Result<Unit> = runSuspendCatching {
        // Best effort: offline, or on iOS where the app has no API token yet, the user still
        // signs out on this device. The local sign-out clears the tokens either way.
        runSuspendCatching { sessionApi.revokeSessions() }
        authClient.signOut()
    }
}
