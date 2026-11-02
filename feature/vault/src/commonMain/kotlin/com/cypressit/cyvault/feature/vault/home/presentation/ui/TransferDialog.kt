package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.TransferError
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeIntent
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeState
import com.cypressit.cyvault.systemdesign.components.exposeTestTags
import com.cypressit.cyvault.testtags.TransferDialogTags
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.transfer_amount
import cyvault.vault.generated.resources.transfer_cancel
import cyvault.vault.generated.resources.transfer_card
import cyvault.vault.generated.resources.transfer_card_option
import cyvault.vault.generated.resources.transfer_currency_description
import cyvault.vault.generated.resources.transfer_error_challenge_expired
import cyvault.vault.generated.resources.transfer_error_failed
import cyvault.vault.generated.resources.transfer_error_invalid
import cyvault.vault.generated.resources.transfer_error_no_card
import cyvault.vault.generated.resources.transfer_error_signing_locked
import cyvault.vault.generated.resources.transfer_error_signing_unavailable
import cyvault.vault.generated.resources.transfer_loading_cards
import cyvault.vault.generated.resources.transfer_no_cards
import cyvault.vault.generated.resources.transfer_phone_number
import cyvault.vault.generated.resources.transfer_phone_number_placeholder
import cyvault.vault.generated.resources.transfer_send
import cyvault.vault.generated.resources.transfer_sending
import cyvault.vault.generated.resources.transfer_title
import org.jetbrains.compose.resources.stringResource

/**
 * Sends money to a phone number: the receiver's phone number, the amount with its currency, and
 * the card to send from. Every field and menu lives in [VaultHomeState]; this only renders it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransferDialog(
    state: VaultHomeState,
    onIntent: (VaultHomeIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onIntent(VaultHomeIntent.TransferDismissed) },
        // A dialog is its own window, so it needs exposeTestTags as well as the app root.
        modifier = Modifier.exposeTestTags().testTag(TransferDialogTags.DIALOG),
        title = { Text(stringResource(Res.string.transfer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.transferPhoneNumber,
                    onValueChange = { onIntent(VaultHomeIntent.TransferPhoneNumberChanged(it)) },
                    label = { Text(stringResource(Res.string.transfer_phone_number)) },
                    placeholder = { Text(stringResource(Res.string.transfer_phone_number_placeholder)) },
                    singleLine = true,
                    isError = state.isTransferPhoneNumberInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag(TransferDialogTags.PHONE_NUMBER_FIELD),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.transferAmount,
                        onValueChange = { onIntent(VaultHomeIntent.TransferAmountChanged(it)) },
                        label = { Text(stringResource(Res.string.transfer_amount)) },
                        singleLine = true,
                        isError = state.isTransferAmountInvalid,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f).testTag(TransferDialogTags.AMOUNT_FIELD),
                    )
                    // OutlinedTextField reserves 8 dp above its box for the floating label, so
                    // the same top padding centers the button on the box, not the whole field.
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(
                            onClick = { onIntent(VaultHomeIntent.TransferCurrencyMenuOpenChanged(true)) },
                            modifier = Modifier.testTag(TransferDialogTags.CURRENCY_BUTTON),
                        ) {
                            Text(currencySymbol(state.transferCurrency))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(Res.string.transfer_currency_description),
                            )
                        }
                        DropdownMenu(
                            expanded = state.isTransferCurrencyMenuOpen,
                            onDismissRequest = { onIntent(VaultHomeIntent.TransferCurrencyMenuOpenChanged(false)) },
                        ) {
                            itemCurrencies.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text("${currencySymbol(code)}  $code") },
                                    onClick = { onIntent(VaultHomeIntent.TransferCurrencySelected(code)) },
                                )
                            }
                        }
                    }
                }

                // The card to send from: users can have more than one.
                ExposedDropdownMenuBox(
                    expanded = state.isTransferCardMenuOpen,
                    onExpandedChange = { onIntent(VaultHomeIntent.TransferCardMenuOpenChanged(it)) },
                ) {
                    OutlinedTextField(
                        value = when {
                            state.transferCard != null -> cardLabel(state.transferCard!!)
                            state.isLoadingTransferCards -> stringResource(Res.string.transfer_loading_cards)
                            else -> stringResource(Res.string.transfer_no_cards)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.transfer_card)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = state.isTransferCardMenuOpen)
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag(TransferDialogTags.CARD_FIELD),
                    )
                    ExposedDropdownMenu(
                        expanded = state.isTransferCardMenuOpen,
                        onDismissRequest = { onIntent(VaultHomeIntent.TransferCardMenuOpenChanged(false)) },
                    ) {
                        state.transferCards.forEach { card ->
                            DropdownMenuItem(
                                text = { Text(cardLabel(card)) },
                                onClick = { onIntent(VaultHomeIntent.TransferCardSelected(card.id)) },
                            )
                        }
                    }
                }

                // Why the last Send failed; the dialog stays open so the user can fix it.
                state.transferError?.let { error ->
                    Text(
                        text = stringResource(
                            when (error) {
                                TransferError.NoCardForPhoneNumber -> Res.string.transfer_error_no_card
                                TransferError.Invalid -> Res.string.transfer_error_invalid
                                TransferError.SigningUnavailable -> Res.string.transfer_error_signing_unavailable
                                TransferError.ChallengeExpired -> Res.string.transfer_error_challenge_expired
                                TransferError.SigningLocked -> Res.string.transfer_error_signing_locked
                                TransferError.Failed -> Res.string.transfer_error_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(VaultHomeIntent.TransferSubmitted) },
                enabled = state.isTransferEnabled,
                modifier = Modifier.testTag(TransferDialogTags.SEND_BUTTON),
            ) {
                Text(
                    stringResource(
                        if (state.isSendingTransfer) Res.string.transfer_sending else Res.string.transfer_send,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onIntent(VaultHomeIntent.TransferDismissed) },
                enabled = !state.isSendingTransfer,
                modifier = Modifier.testTag(TransferDialogTags.CANCEL_BUTTON),
            ) {
                Text(stringResource(Res.string.transfer_cancel))
            }
        },
    )
}

/** "CGD · Visa **** 1111": the bank and brand when the card has them, then its last four digits. */
@Composable
internal fun cardLabel(card: Card): String {
    val name = listOf(card.bankName, card.brand).filter { it.isNotBlank() }.joinToString(" · ")
    return stringResource(Res.string.transfer_card_option, name, card.last4).trim()
}
