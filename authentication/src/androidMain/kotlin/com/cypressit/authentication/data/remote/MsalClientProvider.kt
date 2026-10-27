package com.cypressit.authentication.data.remote

import android.content.Context
import com.cypressit.authentication.R
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Holds the single MSAL native auth client. MSAL's factory blocks while it reads the config,
 * so the client is created lazily on the IO dispatcher instead of in Application.onCreate.
 */
object MsalClientProvider {
    private lateinit var appContext: Context
    private var client: INativeAuthPublicClientApplication? = null
    private val mutex = Mutex()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun get(): INativeAuthPublicClientApplication = mutex.withLock {
        client ?: withContext(Dispatchers.IO) {
            PublicClientApplication.createNativeAuthPublicClientApplication(
                appContext,
                R.raw.auth_config_native_auth,
            )
        }.also { client = it }
    }
}
