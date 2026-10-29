package com.cypressit.cyvault

import org.koin.core.module.Module

/**
 * Release builds add nothing: the developer tools' modules aren't even compiled in, and the app
 * uses production (CyVaultApiConfig.Production and src/main's MSAL config).
 */
val buildTypeModules: List<Module> = emptyList()
