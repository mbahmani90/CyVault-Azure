package com.cypressit.cyvault.feature.vault.device.di

import com.cypressit.cyvault.core.di.IO_DISPATCHER
import com.cypressit.cyvault.feature.vault.device.data.local.DataStoreDeviceIdStore
import com.cypressit.cyvault.feature.vault.device.data.local.DeviceIdStore
import com.cypressit.cyvault.feature.vault.device.data.remote.DeviceApiService
import com.cypressit.cyvault.feature.vault.device.data.repository.DeviceRepositoryImpl
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceRepository
import com.cypressit.cyvault.feature.vault.device.domain.usecase.EnrollDeviceUseCase
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

internal val deviceModule = module {
    includes(devicePlatformModule)

    // Local: the DataStore comes from devicePlatformModule.
    single<DeviceIdStore> { DataStoreDeviceIdStore(get()) }

    // Remote (AuthClient comes from :auth's authClientModule, DeviceKeyStore from :devicekey's
    // deviceKeyModule, PushTokens from :push's pushModule)
    single { DeviceApiService(get(), get(), apiConfig()) }

    // The keys belong to the device, one per account, so nothing here is kept per session.
    single<DeviceRepository> { DeviceRepositoryImpl(get(), get(), get(), get(), get(named(IO_DISPATCHER))) }
    factory { EnrollDeviceUseCase(get()) }
}

/** The app registers its build's config; without one, the production API. */
private fun Scope.apiConfig(): CyVaultApiConfig = getOrNull() ?: CyVaultApiConfig.Production
