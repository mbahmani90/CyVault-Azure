package com.cypressit.cyvault.auth

import android.content.Context
import androidx.annotation.RawRes
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The app's MSAL configuration: a raw resource with the client ID, tenant authority and
 * redirect URI. It lives in the app, so this module has no app-specific IDs.
 */
data class MsalConfig(@param:RawRes val configResId: Int)

/**
 * Holds the single MSAL native auth client. MSAL's factory blocks while it reads the config,
 * so the client is created lazily on the IO dispatcher instead of in Application.onCreate.
 */
internal class MsalClientProvider(
    private val context: Context,
    private val config: MsalConfig,
) {
    private var client: INativeAuthPublicClientApplication? = null
    private val mutex = Mutex()

    suspend fun get(): INativeAuthPublicClientApplication = mutex.withLock {
        client ?: withContext(Dispatchers.IO) {
            PublicClientApplication.createNativeAuthPublicClientApplication(context, config.configResId)
        }.also { client = it }
    }
}
