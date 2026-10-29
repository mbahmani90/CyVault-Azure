package com.cypressit.cyvault

import com.cypressit.cyvault.feature.vault.debug.vaultDebugModule
import org.koin.core.module.Module

/** Debug builds add the developer tools, such as the vault's sample items. */
val buildTypeModules: List<Module> = listOf(vaultDebugModule)
