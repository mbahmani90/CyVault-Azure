package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.vault_home_add_item_cancel
import cyvault.vault.generated.resources.vault_home_add_item_currency_description
import cyvault.vault.generated.resources.vault_home_add_item_description
import cyvault.vault.generated.resources.vault_home_add_item_save
import cyvault.vault.generated.resources.vault_home_add_item_title
import cyvault.vault.generated.resources.vault_home_add_item_title_field
import cyvault.vault.generated.resources.vault_home_add_item_type_description
import cyvault.vault.generated.resources.vault_home_add_item_type_income
import cyvault.vault.generated.resources.vault_home_add_item_type_purchase
import cyvault.vault.generated.resources.vault_home_add_item_value
import cyvault.vault.generated.resources.vault_home_balance_delete_confirm
import cyvault.vault.generated.resources.vault_home_edit_item_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** The currencies the dialog offers, as ISO 4217 codes. */
internal val itemCurrencies = listOf("EUR", "GBP", "USD")

internal fun currencySymbol(code: String): String = when (code) {
    "EUR" -> "€"
    "GBP" -> "£"
    "USD" -> "$"
    else -> code
}

/**
 * Asks for an item's title, description, type and value with its currency, and saves it as a
 * balance: a purchase is saved as a negative amount, income as a positive one. Edits [item]
 * when it isn't null. Save needs a title and a numeric value.
 */
@Composable
internal fun AddItemDialog(
    item: Balance?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, value: Double, currency: String) -> Unit,
    /** Asks to delete [item]; the Delete button only shows when editing. */
    onDelete: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(item?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(item?.description.orEmpty()) }
    // The field always shows a positive number; the type decides the sign that's saved.
    var value by rememberSaveable { mutableStateOf(item?.amount?.let { abs(it).toString() }.orEmpty()) }
    var isIncome by rememberSaveable { mutableStateOf(item != null && item.amount >= 0) }
    var isTypeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var currency by rememberSaveable { mutableStateOf(item?.currency ?: itemCurrencies.first()) }
    var isCurrencyMenuOpen by rememberSaveable { mutableStateOf(false) }

    // Accept a decimal comma too, as many keyboards offer only that.
    val parsedValue = value.trim().replace(',', '.').toDoubleOrNull()?.let { abs(it) }
    val typeLabel = stringResource(
        if (isIncome) Res.string.vault_home_add_item_type_income else Res.string.vault_home_add_item_type_purchase
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (item == null) Res.string.vault_home_add_item_title else Res.string.vault_home_edit_item_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.vault_home_add_item_title_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.vault_home_add_item_description)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(
                        onClick = { isTypeMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(typeLabel, modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(Res.string.vault_home_add_item_type_description),
                        )
                    }
                    DropdownMenu(
                        expanded = isTypeMenuOpen,
                        onDismissRequest = { isTypeMenuOpen = false },
                    ) {
                        listOf(false, true).forEach { income ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (income) Res.string.vault_home_add_item_type_income
                                            else Res.string.vault_home_add_item_type_purchase
                                        )
                                    )
                                },
                                onClick = {
                                    isIncome = income
                                    isTypeMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(stringResource(Res.string.vault_home_add_item_value)) },
                        singleLine = true,
                        isError = value.isNotBlank() && parsedValue == null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    // OutlinedTextField reserves 8 dp above its box for the floating label, so
                    // the same top padding centers the button on the box, not the whole field.
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        val currencyLabel = stringResource(Res.string.vault_home_add_item_currency_description)
                        OutlinedButton(onClick = { isCurrencyMenuOpen = true }) {
                            Text(currencySymbol(currency))
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = currencyLabel)
                        }
                        DropdownMenu(
                            expanded = isCurrencyMenuOpen,
                            onDismissRequest = { isCurrencyMenuOpen = false },
                        ) {
                            itemCurrencies.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text("${currencySymbol(code)}  $code") },
                                    onClick = {
                                        currency = code
                                        isCurrencyMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        // One row instead of confirm/dismiss slots, so Delete can sit on the left when editing.
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (item != null) {
                    TextButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(Res.string.vault_home_balance_delete_confirm))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.vault_home_add_item_cancel))
                }
                TextButton(
                    onClick = {
                        val signed = if (isIncome) parsedValue!! else -parsedValue!!
                        onSave(title.trim(), description.trim(), signed, currency)
                    },
                    enabled = title.isNotBlank() && parsedValue != null && !isSaving,
                ) {
                    Text(stringResource(Res.string.vault_home_add_item_save))
                }
            }
        },
    )
}
