package com.cypressit.cyvault

import com.cypressit.cyvault.feature.vault.debug.vaultDebugModule
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Debug builds add the developer tools, such as the vault's sample items, and use the debug
 * environment: its own tenant (src/debug/res/raw/auth_config_native_auth.json), API and storage.
 */
val buildTypeModules: List<Module> = listOf(
    vaultDebugModule,
    module {
        // azd environment "debug" (azure-backend: azd env get-values -e debug).
        single {
            CyVaultApiConfig(
                baseUrl = "https://func-cyvault-5zvntav5rsyeq.azurewebsites.net/api",
                appIdUri = "api://7b114a03-12d1-4c35-842f-861308111286",
            )
        }
    },
)
