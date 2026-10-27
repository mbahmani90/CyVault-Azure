package com.cypressit.cyvault.core.di

import org.koin.core.module.Module

/**
 * Platform-specific Koin module.
 * Each platform provides the HttpClientEngine and any other OS-level dependencies.
 */
expect val platformModule: Module
