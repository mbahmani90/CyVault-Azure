package com.cypressit.cyvault.feature.vault.addcard.presentation.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardSaveError
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardState
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardViewModel
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.systemdesign.components.CyButton
import com.cypressit.cyvault.systemdesign.components.CyOutlinedTextField
import com.cypressit.cyvault.systemdesign.components.CyTopAppBar
import com.cypressit.cyvault.systemdesign.components.exposeTestTags
import com.cypressit.cyvault.systemdesign.theme.CyTheme
import com.cypressit.cyvault.testtags.CardsTags
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.add_card_bank_name
import cyvault.vault.generated.resources.add_card_cancel
import cyvault.vault.generated.resources.add_card_card_number
import cyvault.vault.generated.resources.add_card_invalid
import cyvault.vault.generated.resources.add_card_phone_already_linked
import cyvault.vault.generated.resources.add_card_phone_number
import cyvault.vault.generated.resources.add_card_phone_number_placeholder
import cyvault.vault.generated.resources.add_card_save_failed
import cyvault.vault.generated.resources.add_card_submit
import cyvault.vault.generated.resources.add_card_title
import cyvault.vault.generated.resources.card_delete_message
import cyvault.vault.generated.resources.card_delete_title
import cyvault.vault.generated.resources.card_details_bank
import cyvault.vault.generated.resources.card_details_brand
import cyvault.vault.generated.resources.card_details_close
import cyvault.vault.generated.resources.card_details_number
import cyvault.vault.generated.resources.card_details_phone
import cyvault.vault.generated.resources.card_details_title
import cyvault.vault.generated.resources.cards_add_description
import cyvault.vault.generated.resources.cards_added
import cyvault.vault.generated.resources.cards_delete_confirm
import cyvault.vault.generated.resources.cards_delete_failed
import cyvault.vault.generated.resources.cards_delete_message
import cyvault.vault.generated.resources.cards_delete_title
import cyvault.vault.generated.resources.cards_deleted
import cyvault.vault.generated.resources.cards_empty
import cyvault.vault.generated.resources.cards_load_failed
import cyvault.vault.generated.resources.cards_masked_number
import cyvault.vault.generated.resources.cards_select_card_description
import cyvault.vault.generated.resources.cards_selection_close_description
import cyvault.vault.generated.resources.cards_selection_count
import cyvault.vault.generated.resources.cards_selection_delete_description
import cyvault.vault.generated.resources.cards_selection_select_all_description
import cyvault.vault.generated.resources.cards_title
import cyvault.vault.generated.resources.cards_view_card_description
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
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
                AddCardEffect.CardAdded ->
                    snackbarHostState.showSnackbar(getString(Res.string.cards_added))
                is AddCardEffect.CardsDeleted -> snackbarHostState.showSnackbar(
                    getPluralString(Res.plurals.cards_deleted, effect.count, effect.count),
                )
                AddCardEffect.CardsDeleteFailed ->
                    snackbarHostState.showSnackbar(getString(Res.string.cards_delete_failed))
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
        topBar = topBar@{
            if (state.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedCardCount,
                    areAllSelected = state.areAllCardsSelected,
                    isDeleting = state.isDeletingCards,
                    onClose = { onIntent(AddCardIntent.ClearCardSelection) },
                    onSelectAll = { onIntent(AddCardIntent.SelectAllCards) },
                    onDelete = { onIntent(AddCardIntent.RequestDeleteSelectedCards) },
                )
                return@topBar
            }
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
            onIntent = onIntent,
            modifier = Modifier.padding(paddingValues),
        )
    }

    if (state.isAddCardDialogVisible) {
        AddCardDialog(
            cardNumber = state.cardNumber,
            phoneNumber = state.phoneNumber,
            bankName = state.bankName,
            isAddEnabled = state.isAddEnabled,
            isSaving = state.isSavingCard,
            saveError = state.saveError,
            onCardNumberChange = { onIntent(AddCardIntent.CardNumberChanged(it)) },
            onPhoneNumberChange = { onIntent(AddCardIntent.PhoneNumberChanged(it)) },
            onBankNameChange = { onIntent(AddCardIntent.BankNameChanged(it)) },
            onAddClick = { onIntent(AddCardIntent.AddCardSubmitted) },
            onDismiss = { onIntent(AddCardIntent.AddCardDialogDismissed) },
        )
    }

    state.viewingCard?.let { card ->
        CardDetailsDialog(
            card = card,
            onDelete = { onIntent(AddCardIntent.RequestDeleteViewingCard) },
            onDismiss = { onIntent(AddCardIntent.CardDetailsDismissed) },
        )
    }

    state.pendingDeleteCard?.let { card ->
        DeleteCardsDialog(
            title = stringResource(Res.string.card_delete_title),
            message = stringResource(Res.string.card_delete_message, card.last4),
            onConfirm = { onIntent(AddCardIntent.ConfirmDeleteCard) },
            onDismiss = { onIntent(AddCardIntent.DismissDeleteCard) },
        )
    }

    if (state.isDeleteSelectedConfirmVisible) {
        DeleteCardsDialog(
            title = pluralStringResource(
                Res.plurals.cards_delete_title,
                state.selectedCardCount,
                state.selectedCardCount,
            ),
            message = stringResource(Res.string.cards_delete_message),
            onConfirm = { onIntent(AddCardIntent.ConfirmDeleteSelectedCards) },
            onDismiss = { onIntent(AddCardIntent.DismissDeleteSelectedCards) },
        )
    }
}

/** Replaces the brand top bar while cards are selected: close, count, select all, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    areAllSelected: Boolean,
    isDeleting: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(Res.string.cards_selection_count, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(CardsTags.SELECTION_COUNT_TEXT),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CyTheme.brandColors.topBar,
            titleContentColor = CyTheme.brandColors.onTopBar,
            navigationIconContentColor = CyTheme.brandColors.onTopBar,
            actionIconContentColor = CyTheme.brandColors.onTopBar,
        ),
        navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.testTag(CardsTags.SELECTION_CLOSE_BUTTON)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.cards_selection_close_description),
                )
            }
        },
        actions = {
            IconButton(
                onClick = onSelectAll,
                enabled = !areAllSelected && !isDeleting,
                modifier = Modifier.testTag(CardsTags.SELECTION_SELECT_ALL_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = stringResource(Res.string.cards_selection_select_all_description),
                )
            }
            if (isDeleting) {
                // Same size as the icon button it replaces, so the bar doesn't shift.
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = CyTheme.brandColors.onTopBar,
                    )
                }
            } else {
                IconButton(onClick = onDelete, modifier = Modifier.testTag(CardsTags.SELECTION_DELETE_BUTTON)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.cards_selection_delete_description),
                    )
                }
            }
        },
    )
}

@Composable
private fun CardList(
    state: AddCardState,
    onIntent: (AddCardIntent) -> Unit,
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
                    CardRow(
                        card = card,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = state.isCardSelected(card.id),
                        onClick = { onIntent(AddCardIntent.ClickCard(card)) },
                        onLongClick = { onIntent(AddCardIntent.LongClickCard(card)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CardRow(
    card: Card,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Brand and bank name are optional: cards added before they existed have neither.
    ListItem(
        // A check box only in selection mode, so normal rows keep their full width.
        leadingContent = if (isSelectionMode) {
            { Checkbox(checked = isSelected, onCheckedChange = null) }
        } else {
            null
        },
        // Every row has the same two lines on the left, so rows line up with or without a brand.
        headlineContent = { Text(stringResource(Res.string.cards_masked_number, card.last4)) },
        supportingContent = { Text(card.phoneNumber) },
        trailingContent = { CardLabels(bankName = card.bankName, brand = card.brand) },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        // Tap shows the card, or selects in selection mode; a long press starts selection mode.
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CardsTags.CARD_ROW)
            .combinedClickable(
                onClickLabel = stringResource(
                    if (isSelectionMode) {
                        Res.string.cards_select_card_description
                    } else {
                        Res.string.cards_view_card_description
                    },
                ),
                onLongClickLabel = stringResource(Res.string.cards_select_card_description),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    )
}

/** The bank name with the brand under it, right-aligned; either may be empty on older cards. */
@Composable
private fun CardLabels(bankName: String, brand: String) {
    Column(horizontalAlignment = Alignment.End) {
        if (bankName.isNotBlank()) {
            Text(text = bankName, style = MaterialTheme.typography.labelLarge)
        }
        if (brand.isNotBlank()) {
            Text(
                text = brand,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A card's details, with Delete to remove it. */
@Composable
private fun CardDetailsDialog(
    card: Card,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // A dialog is its own window, so it needs exposeTestTags as well as the app root.
        modifier = Modifier.exposeTestTags().testTag(CardsTags.DETAILS_DIALOG),
        title = { Text(stringResource(Res.string.card_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow(
                    label = stringResource(Res.string.card_details_number),
                    value = stringResource(Res.string.cards_masked_number, card.last4),
                )
                if (card.brand.isNotBlank()) {
                    DetailRow(label = stringResource(Res.string.card_details_brand), value = card.brand)
                }
                if (card.bankName.isNotBlank()) {
                    DetailRow(label = stringResource(Res.string.card_details_bank), value = card.bankName)
                }
                DetailRow(label = stringResource(Res.string.card_details_phone), value = card.phoneNumber)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(CardsTags.DETAILS_CLOSE_BUTTON)) {
                Text(stringResource(Res.string.card_details_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete, modifier = Modifier.testTag(CardsTags.DETAILS_DELETE_BUTTON)) {
                Text(stringResource(Res.string.cards_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Asks before deleting one card or the selected ones. */
@Composable
private fun DeleteCardsDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // A dialog is its own window, so it needs exposeTestTags as well as the app root.
        modifier = Modifier.exposeTestTags().testTag(CardsTags.DELETE_DIALOG),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(CardsTags.DELETE_CONFIRM_BUTTON)) {
                Text(stringResource(Res.string.cards_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(CardsTags.DELETE_CANCEL_BUTTON)) {
                Text(stringResource(Res.string.add_card_cancel))
            }
        },
    )
}

@Composable
private fun AddCardDialog(
    cardNumber: String,
    phoneNumber: String,
    bankName: String,
    isAddEnabled: Boolean,
    isSaving: Boolean,
    saveError: AddCardSaveError?,
    onCardNumberChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onBankNameChange: (String) -> Unit,
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

                CyOutlinedTextField(
                    value = bankName,
                    onValueChange = onBankNameChange,
                    label = stringResource(Res.string.add_card_bank_name),
                    modifier = Modifier.fillMaxWidth().testTag(CardsTags.BANK_NAME_FIELD),
                )

                // In the dialog, not a snackbar: the dialog would cover one.
                if (saveError != null) {
                    Text(
                        text = stringResource(
                            when (saveError) {
                                AddCardSaveError.PhoneNumberAlreadyLinked -> Res.string.add_card_phone_already_linked
                                AddCardSaveError.InvalidCard -> Res.string.add_card_invalid
                                AddCardSaveError.Failed -> Res.string.add_card_save_failed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(CardsTags.SAVE_ERROR_TEXT),
                    )
                }
            }
        },
        confirmButton = {
            CyButton(
                text = stringResource(Res.string.add_card_submit),
                onClick = onAddClick,
                enabled = isAddEnabled,
                isLoading = isSaving,
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
