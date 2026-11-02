package com.cypressit.cyvault.feature.vault.di

import org.koin.core.Koin
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

/**
 * The signed-in user's Koin scope. What holds their data (the card, balance and profile photo
 * repositories, and the use cases and ViewModels built on them) is scoped here instead of being a
 * singleton. The vault screens open the scope, which also covers a session restored on start, and
 * sign-out closes it: Koin drops those instances, so the next user starts with empty ones.
 */
object UserSession {
    /** Declares a definition in this scope: `scope(UserSession.qualifier) { scoped { ... } }`. */
    val qualifier: Qualifier = named("UserSession")

    private const val SCOPE_ID = "user-session"

    internal fun scope(koin: Koin): Scope = koin.getOrCreateScope(SCOPE_ID, qualifier)

    /** Only once the vault screens are gone: an instance resolved after this belongs to a new session. */
    internal fun close(koin: Koin) {
        koin.getScopeOrNull(SCOPE_ID)?.close()
    }
}
