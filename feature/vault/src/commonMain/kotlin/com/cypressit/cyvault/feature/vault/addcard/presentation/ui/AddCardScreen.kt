package com.cypressit.cyvault.feature.vault.addcard.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.systemdesign.components.CyButton
import com.cypressit.cyvault.systemdesign.components.CyOutlinedTextField
import com.cypressit.cyvault.systemdesign.components.CyTopAppBar
import com.cypressit.cyvault.feature.vault.addcard.presentation.BankAppLauncher
import com.cypressit.cyvault.feature.vault.addcard.domain.model.BankAccount
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardEffect
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardIntent
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardState
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardStep
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardViewModel
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    onNavigateBack: () -> Unit,
    onCardSaved: () -> Unit,
    viewModel: AddCardViewModel = koinViewModel(),
    bankAppLauncher: BankAppLauncher = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AddCardEffect.LaunchBankApp -> bankAppLauncher.openBankUrl(effect.bankUrl)
                AddCardEffect.CardSaved -> onCardSaved()
                is AddCardEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            CyTopAppBar(
                title = when (state.step) {
                    AddCardStep.SelectBank -> "Select Bank"
                    AddCardStep.Authenticating -> "Authenticating"
                    AddCardStep.SelectAccount -> "Select Account"
                    AddCardStep.EnterCardDetails -> "Card Details"
                },
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (state.step) {
            AddCardStep.SelectBank -> SelectBankStep(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = Modifier.padding(paddingValues),
            )
            AddCardStep.Authenticating -> AuthenticatingStep(
                isLoading = state.isLoading,
                onAuthCompleted = { viewModel.onIntent(AddCardIntent.AuthenticationCompleted(consentToken = null)) },
                onAuthFailed = { viewModel.onIntent(AddCardIntent.AuthenticationFailed("Authentication cancelled.")) },
                modifier = Modifier.padding(paddingValues),
            )
            AddCardStep.SelectAccount -> SelectAccountStep(
                accounts = state.accounts,
                onAccountSelected = { viewModel.onIntent(AddCardIntent.AccountSelected(it)) },
                modifier = Modifier.padding(paddingValues),
            )
            AddCardStep.EnterCardDetails -> EnterCardDetailsStep(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun SelectBankStep(
    state: AddCardState,
    onIntent: (AddCardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CyOutlinedTextField(
            value = state.country,
            onValueChange = { onIntent(AddCardIntent.CountryChanged(it.uppercase())) },
            label = "Country Code (e.g. GB, DE, FR)",
            modifier = Modifier.fillMaxWidth(),
        )

        CyOutlinedTextField(
            value = state.bankSearchQuery,
            onValueChange = { onIntent(AddCardIntent.BankSearchQueryChanged(it)) },
            label = "Search bank name",
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = if (state.isInstitutionsLoading) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else null,
        )

        if (state.institutions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.institutions, key = { it.id }) { institution ->
                    ListItem(
                        headlineContent = { Text(institution.name) },
                        supportingContent = institution.bic?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIntent(AddCardIntent.InstitutionSelected(institution)) },
                    )
                    HorizontalDivider()
                }
            }
        } else if (state.bankSearchQuery.length >= 2 && !state.isInstitutionsLoading) {
            Text(
                text = "No banks found. Try a different search or country code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AuthenticatingStep(
    isLoading: Boolean,
    onAuthCompleted: () -> Unit,
    onAuthFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))

            Text(
                text = "Complete authentication in your bank's page,\nthen return here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            CyButton(
                text = "I've Authenticated",
                onClick = onAuthCompleted,
                isLoading = isLoading,
            )

            TextButton(onClick = onAuthFailed) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SelectAccountStep(
    accounts: List<BankAccount>,
    onAccountSelected: (BankAccount) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Select the account to link",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(accounts, key = { it.id }) { account ->
                ListItem(
                    headlineContent = { Text(account.iban ?: account.name) },
                    supportingContent = { Text("${account.balance} ${account.currency}") },
                    trailingContent = account.ownerName?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAccountSelected(account) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterCardDetailsStep(
    state: AddCardState,
    onIntent: (AddCardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cardTypeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CyOutlinedTextField(
            value = state.bankName,
            onValueChange = {},
            label = "Bank",
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CyOutlinedTextField(
                value = state.balance,
                onValueChange = {},
                label = "Balance",
                readOnly = true,
                modifier = Modifier.weight(1f),
            )
            CyOutlinedTextField(
                value = state.currency,
                onValueChange = {},
                label = "Currency",
                readOnly = true,
                modifier = Modifier.weight(0.4f),
            )
        }

        CyOutlinedTextField(
            value = state.cardHolderName,
            onValueChange = { onIntent(AddCardIntent.CardHolderNameChanged(it)) },
            label = "Card Holder Name",
            modifier = Modifier.fillMaxWidth(),
        )

        CyOutlinedTextField(
            value = state.cardNumber,
            onValueChange = { onIntent(AddCardIntent.CardNumberChanged(it)) },
            label = "Card Number",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        CyOutlinedTextField(
            value = state.cvv2,
            onValueChange = { onIntent(AddCardIntent.Cvv2Changed(it)) },
            label = "CVV2",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        CyOutlinedTextField(
            value = state.expiryDate,
            onValueChange = { onIntent(AddCardIntent.ExpiryDateChanged(it)) },
            label = "Expiry Date (MM/YY)",
            placeholder = "MM/YY",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        ExposedDropdownMenuBox(
            expanded = cardTypeExpanded,
            onExpandedChange = { cardTypeExpanded = it },
        ) {
            CyOutlinedTextField(
                value = state.cardType.name,
                onValueChange = {},
                label = "Card Type",
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cardTypeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = cardTypeExpanded,
                onDismissRequest = { cardTypeExpanded = false },
            ) {
                CardType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.name) },
                        onClick = {
                            onIntent(AddCardIntent.CardTypeChanged(type))
                            cardTypeExpanded = false
                        }
                    )
                }
            }
        }

        CyButton(
            text = "Save Card",
            onClick = { onIntent(AddCardIntent.Submit) },
            isLoading = state.isLoading,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
