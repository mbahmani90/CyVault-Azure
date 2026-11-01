package com.cypressit.cyvault.feature.vault.addcard.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardEffect
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardIntent
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardState
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardViewModel
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.systemdesign.components.CyButton
import com.cypressit.cyvault.systemdesign.components.CyOutlinedTextField
import com.cypressit.cyvault.systemdesign.components.CyTopAppBar
import com.cypressit.cyvault.systemdesign.components.exposeTestTags
import com.cypressit.cyvault.testtags.CardsTags
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.add_card_cancel
import cyvault.vault.generated.resources.add_card_card_number
import cyvault.vault.generated.resources.add_card_phone_number
import cyvault.vault.generated.resources.add_card_phone_number_placeholder
import cyvault.vault.generated.resources.add_card_submit
import cyvault.vault.generated.resources.add_card_title
import cyvault.vault.generated.resources.cards_add_description
import cyvault.vault.generated.resources.cards_empty
import cyvault.vault.generated.resources.cards_load_failed
import cyvault.vault.generated.resources.cards_masked_number
import cyvault.vault.generated.resources.cards_title
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The only composable that knows the ViewModel: it collects state and effects for the screen. */
@Composable
internal fun AddCardRoute(
    onNavigateBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onCardSaved: () -> Unit,
    viewModel: AddCardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AddCardEffect.CardsLoadFailed ->
                    snackbarHostState.showSnackbar(getString(Res.string.cards_load_failed))
            }
        }
    }

    AddCardScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    )
}

/** Stateless: renders [state] and reports every user action as an [AddCardIntent]. */
@Composable
internal fun AddCardScreen(
    state: AddCardState,
    onIntent: (AddCardIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            CyTopAppBar(
                title = stringResource(Res.string.cards_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { onIntent(AddCardIntent.AddCardClicked) },
                        modifier = Modifier.testTag(CardsTags.ADD_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.cards_add_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        CardList(
            state = state,
            modifier = Modifier.padding(paddingValues),
        )
    }

    if (state.isAddCardDialogVisible) {
        // The Add button isn't wired to the ViewModel yet.
        AddCardDialog(
            cardNumber = state.cardNumber,
            phoneNumber = state.phoneNumber,
            isAddEnabled = state.isAddEnabled,
            onCardNumberChange = { onIntent(AddCardIntent.CardNumberChanged(it)) },
            onPhoneNumberChange = { onIntent(AddCardIntent.PhoneNumberChanged(it)) },
            onAddClick = {},
            onDismiss = { onIntent(AddCardIntent.AddCardDialogDismissed) },
        )
    }
}

@Composable
private fun CardList(
    state: AddCardState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isCardsLoading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag(CardsTags.LOADING),
            )

            state.isEmpty -> Text(
                text = stringResource(Res.string.cards_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .testTag(CardsTags.EMPTY_TEXT),
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag(CardsTags.LIST)) {
                items(state.cards, key = { it.id }) { card ->
                    CardRow(card)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CardRow(card: Card) {
    ListItem(
        overlineContent = { Text(card.cardType.name) },
        headlineContent = { Text(card.bankName) },
        supportingContent = {
            Text(stringResource(Res.string.cards_masked_number, card.cardNumber.takeLast(4)))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddCardDialog(
    cardNumber: String,
    phoneNumber: String,
    isAddEnabled: Boolean,
    onCardNumberChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // A dialog is its own window, so it needs exposeTestTags as well as the app root.
        modifier = Modifier.exposeTestTags().testTag(CardsTags.ADD_CARD_DIALOG),
        title = { Text(stringResource(Res.string.add_card_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CyOutlinedTextField(
                    value = cardNumber,
                    onValueChange = onCardNumberChange,
                    label = stringResource(Res.string.add_card_card_number),
                    modifier = Modifier.fillMaxWidth().testTag(CardsTags.CARD_NUMBER_FIELD),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                CyOutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    label = stringResource(Res.string.add_card_phone_number),
                    placeholder = stringResource(Res.string.add_card_phone_number_placeholder),
                    modifier = Modifier.fillMaxWidth().testTag(CardsTags.PHONE_NUMBER_FIELD),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            CyButton(
                text = stringResource(Res.string.add_card_submit),
                onClick = onAddClick,
                enabled = isAddEnabled,
                modifier = Modifier.testTag(CardsTags.SUBMIT_BUTTON),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(CardsTags.CANCEL_BUTTON)) {
                Text(stringResource(Res.string.add_card_cancel))
            }
        },
    )
}
