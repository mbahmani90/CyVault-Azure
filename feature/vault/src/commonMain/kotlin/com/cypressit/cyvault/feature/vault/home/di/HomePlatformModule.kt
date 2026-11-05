package com.cypressit.cyvault.feature.vault.home.di

import org.koin.core.module.Module

/** Binds the home screen's platform pieces: the avatar cache, picked-photo reader and balance cache. */
internal expect val homePlatformModule: Module
