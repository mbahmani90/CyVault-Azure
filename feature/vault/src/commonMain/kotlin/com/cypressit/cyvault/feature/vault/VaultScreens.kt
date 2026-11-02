package com.cypressit.cyvault.feature.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cypressit.cyvault.feature.vault.addcard.presentation.ui.AddCardRoute
import com.cypressit.cyvault.feature.vault.di.UserSession
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import com.cypressit.cyvault.feature.vault.home.presentation.ui.VaultHomeScreen as VaultHomeContent

/*
 * The vault feature's public screens. They only report events; the app module owns routes
 * and navigation, so this feature doesn't depend on Navigation Compose. Their ViewModels come
 * from the user's session scope, which sign-out closes.
 */

/** The vault's home: card list, drawer with sign-out, and an add-card button. */
@Composable
fun VaultHomeScreen(
    onAddCardClick: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val koin = getKoin()
    val session = remember(koin) { UserSession.scope(koin) }
    VaultHomeContent(
        onAddCardClick = onAddCardClick,
        onSignedOut = {
            // Leave the vault first, then drop the user's data with their session.
            onSignedOut()
            UserSession.close(koin)
        },
        viewModel = koinViewModel(scope = session),
    )
}

/** The user's cards, with a top-bar button that adds one; it has its own back arrow. */
@Composable
fun AddCardScreen(
    onBack: () -> Unit,
    onCardSaved: () -> Unit,
) {
    val koin = getKoin()
    val session = remember(koin) { UserSession.scope(koin) }
    AddCardRoute(
        onNavigateBack = onBack,
        onCardSaved = onCardSaved,
        viewModel = koinViewModel(scope = session),
    )
}
