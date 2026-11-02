package com.cypressit.cyvault.feature.vault.device.di

import org.koin.core.module.Module

/** Binds the device-ID store's DataStore, in the platform's app-private files directory. */
internal expect val devicePlatformModule: Module
