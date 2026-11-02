package com.cypressit.cyvault.feature.vault.device.di

import com.cypressit.cyvault.feature.vault.device.data.local.deviceIdDataStore
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual val devicePlatformModule: Module = module {
    single { deviceIdDataStore(documentsDirectory()) }
}

@OptIn(ExperimentalForeignApi::class) // NSFileManager's URLForDirectory
private fun documentsDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(url?.path) { "No documents directory" }
}
