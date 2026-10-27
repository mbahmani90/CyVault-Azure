package com.cypressit.cyvault.feature.authentication.data.remote

import android.content.Context
import com.cypressit.cyvault.feature.authentication.R
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

/**
 * Holds the single MSAL native auth client. MSAL's factory blocks while it reads the config,
 * so the client is created lazily on the IO dispatcher instead of in Application.onCreate.
 * The context comes from Koin (androidContext), so the app shell doesn't have to pass it in.
 */
internal object MsalClientProvider {
    private var client: INativeAuthPublicClientApplication? = null
    private val mutex = Mutex()

    suspend fun get(): INativeAuthPublicClientApplication = mutex.withLock {
        client ?: withContext(Dispatchers.IO) {
            PublicClientApplication.createNativeAuthPublicClientApplication(
                KoinPlatform.getKoin().get<Context>(),
                R.raw.auth_config_native_auth,
            )
        }.also { client = it }
    }
}
