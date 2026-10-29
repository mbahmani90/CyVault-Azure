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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.systemdesign.components.exposeTestTags
import com.cypressit.cyvault.testtags.AddItemDialogTags
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.vault_home_add_item_cancel
import cyvault.vault.generated.resources.vault_home_add_item_currency_description
import cyvault.vault.generated.resources.vault_home_add_item_date_description
import cyvault.vault.generated.resources.vault_home_add_item_date_done
import cyvault.vault.generated.resources.vault_home_add_item_date_next
import cyvault.vault.generated.resources.vault_home_add_item_description
import cyvault.vault.generated.resources.vault_home_add_item_save
import cyvault.vault.generated.resources.vault_home_add_item_title_field
import cyvault.vault.generated.resources.vault_home_add_item_type_description
import cyvault.vault.generated.resources.vault_home_add_item_type_income
import cyvault.vault.generated.resources.vault_home_add_item_type_purchase
import cyvault.vault.generated.resources.vault_home_add_item_value
import cyvault.vault.generated.resources.vault_home_balance_delete_confirm
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Instant

/** The currencies the dialog offers, as ISO 4217 codes. */
internal val itemCurrencies = listOf("EUR", "GBP", "USD")

internal fun currencySymbol(code: String): String = when (code) {
    "EUR" -> "€"
    "GBP" -> "£"
    "USD" -> "$"
    else -> code
}

/**
 * Asks for an item's title, description, type, value with its currency, and date and time, and
 * saves it as a balance: a purchase is saved as a negative amount, income as a positive one.
 * The date starts at now, or at the stored date when editing [item]. Save needs a title and a
 * numeric value.
 */
@OptIn(ExperimentalMaterial3Api::class) // DatePicker, TimePicker
@Composable
internal fun AddItemDialog(
    item: Balance?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    /** [date] is ISO 8601. */
    onSave: (title: String, description: String, value: Double, currency: String, date: String) -> Unit,
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
        if (isIncome) Res.string.vault_home_add_item_type_income else Res.string.vault_home_add_item_type_purchase,
    )

    // When it happened: now for a new item, the stored date when editing. Epoch millis so it
    // survives rotation.
    var dateMillis by rememberSaveable {
        mutableStateOf((parseBalanceDate(item?.date) ?: Clock.System.now()).toEpochMilliseconds())
    }
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }
    // The day picked in the date picker, waiting for the time picker; null when not picking.
    var pickedDayMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val date = Instant.fromEpochMilliseconds(dateMillis)

    if (isDatePickerOpen) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = toDatePickerMillis(date))
        DatePickerDialog(
            onDismissRequest = { isDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDayMillis = datePickerState.selectedDateMillis
                        isDatePickerOpen = false
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                ) { Text(stringResource(Res.string.vault_home_add_item_date_next)) }
            },
            dismissButton = {
                TextButton(onClick = { isDatePickerOpen = false }) {
                    Text(stringResource(Res.string.vault_home_add_item_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    pickedDayMillis?.let { dayMillis ->
        val local = date.toLocalDateTime(TimeZone.currentSystemDefault())
        val timePickerState = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)
        AlertDialog(
            onDismissRequest = { pickedDayMillis = null },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    dateMillis = fromPickers(dayMillis, timePickerState.hour, timePickerState.minute)
                        .toEpochMilliseconds()
                    pickedDayMillis = null
                }) { Text(stringResource(Res.string.vault_home_add_item_date_done)) }
            },
            dismissButton = {
                TextButton(onClick = { pickedDayMillis = null }) {
                    Text(stringResource(Res.string.vault_home_add_item_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // A dialog is its own window, so it needs exposeTestTags as well as the app root.
        modifier = Modifier.exposeTestTags().testTag(AddItemDialogTags.DIALOG),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.vault_home_add_item_title_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag(AddItemDialogTags.TITLE_FIELD),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.vault_home_add_item_description)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag(AddItemDialogTags.DESCRIPTION_FIELD),
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
                                            if (income) {
                                                Res.string.vault_home_add_item_type_income
                                            } else {
                                                Res.string.vault_home_add_item_type_purchase
                                            },
                                        ),
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
                        modifier = Modifier.weight(1f).testTag(AddItemDialogTags.VALUE_FIELD),
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
                // Date and time, picked with the date picker and then the time picker.
                OutlinedButton(
                    onClick = { isDatePickerOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(formatBalanceDate(date), modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = stringResource(Res.string.vault_home_add_item_date_description),
                    )
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
                TextButton(onClick = onDismiss, modifier = Modifier.testTag(AddItemDialogTags.CANCEL_BUTTON)) {
                    Text(stringResource(Res.string.vault_home_add_item_cancel))
                }
                TextButton(
                    onClick = {
                        val signed = if (isIncome) parsedValue!! else -parsedValue!!
                        onSave(title.trim(), description.trim(), signed, currency, date.toString())
                    },
                    enabled = title.isNotBlank() && parsedValue != null && !isSaving,
                    modifier = Modifier.testTag(AddItemDialogTags.SAVE_BUTTON),
                ) {
                    Text(stringResource(Res.string.vault_home_add_item_save))
                }
            }
        },
    )
}
