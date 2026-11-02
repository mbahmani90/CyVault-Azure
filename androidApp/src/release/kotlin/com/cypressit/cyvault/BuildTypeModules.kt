package com.cypressit.cyvault

import org.koin.core.module.Module

/**
 * Release builds add nothing: the developer tools' modules aren't even compiled in, and the app
 * uses production (CyVaultApiConfig.Production and src/main's MSAL config).
 */
val buildTypeModules: List<Module> = emptyList()

/**
 * A production API scope for email sign-in, so Conditional Access on the API (MFA) runs then.
 * The API's Application ID URI is CyVaultApiConfig.Production's, which this source set can't see.
 */
val signInScope = "api://61a46c01-e17b-43da-8630-17d521ef888b/Profile.ReadWrite"
