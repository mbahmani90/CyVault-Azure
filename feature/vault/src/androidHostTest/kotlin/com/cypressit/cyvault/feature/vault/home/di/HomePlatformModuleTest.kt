package com.cypressit.cyvault.feature.vault.home.di

import com.cypressit.cyvault.core.di.dispatcherModule
import com.cypressit.cyvault.feature.vault.home.data.local.BalanceCacheDatabase
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Koin only finds a missing definition when it's asked for one, which crashes the app at runtime. */
@RunWith(RobolectricTestRunner::class)
class HomePlatformModuleTest {

    private val koin = koinApplication {
        androidContext(RuntimeEnvironment.getApplication())
        modules(dispatcherModule, homePlatformModule)
    }

    @After fun tearDown() = koin.close()

    @Test
    fun balanceCacheDatabase_canBeCreated() {
        koin.koin.get<BalanceCacheDatabase>().close()
    }
}
