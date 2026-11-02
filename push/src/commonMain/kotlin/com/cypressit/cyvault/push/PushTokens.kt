package com.cypressit.cyvault.push

/**
 * The phone's push token: the address the server sends pushes to. The app sends it to the server
 * after sign-in, and drops it on sign-out so the account's pushes stop arriving on this phone.
 */
interface PushTokens {
    /** The current token, or null when this phone can't get pushes (no Google Play services, iOS for now). */
    suspend fun current(): String?

    /** Drops the token: pushes sent to it no longer arrive. The next [current] makes a new one. */
    suspend fun delete()
}
