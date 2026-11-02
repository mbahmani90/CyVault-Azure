package com.cypressit.cyvault.feature.vault.addcard.di

import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateCardUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.DeleteCardsUseCase
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardViewModel
import com.cypressit.cyvault.feature.vault.di.UserSession
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val addCardModule = module {
    // In the user's session, like the card repository they use.
    scope(UserSession.qualifier) {
        // Use cases
        factory { CreateCardUseCase(get()) }
        factory { DeleteCardsUseCase(get()) }

        // ViewModel
        viewModelOf(::AddCardViewModel)
    }
}
