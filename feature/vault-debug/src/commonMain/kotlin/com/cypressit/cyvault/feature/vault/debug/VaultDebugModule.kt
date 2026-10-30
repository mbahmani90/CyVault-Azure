package com.cypressit.cyvault.feature.vault.debug

import com.cypressit.cyvault.feature.vault.home.domain.debug.SampleBalancesSeeder
import org.koin.core.module.Module
import org.koin.dsl.module

/** Loaded in debug builds only; with it, the vault drawer shows "Add 50 sample items". */
val vaultDebugModule: Module = module {
    single<SampleBalancesSeeder> { RandomSampleBalancesSeeder(get()) }
}
