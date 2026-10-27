package com.cypressit.cyvault.feature.vault.di

import com.cypressit.cyvault.feature.vault.addcard.di.addCardModule
import com.cypressit.cyvault.feature.vault.card.di.cardModule
import com.cypressit.cyvault.feature.vault.home.di.homeModule
import org.koin.core.module.Module
import org.koin.dsl.module

/** The vault feature's only public Koin module; it loads every package's module. */
val vaultFeatureModule: Module = module {
    includes(cardModule, homeModule, addCardModule)
}
