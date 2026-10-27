package com.cypressit.cyvault.feature.vault.vaultlist.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.vault.vaultlist.presentation.viewmodel.VaultListEffect
import com.cypressit.cyvault.feature.vault.vaultlist.presentation.viewmodel.VaultListViewModel
import org.koin.compose.viewmodel.koinViewModel

private fun Double.fmt2dp(): String {
    val scaled = (this * 100).toLong()
    val intPart = scaled / 100
    val decPart = kotlin.math.abs(scaled % 100)
    return "$intPart.${decPart.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultListScreen(
    onAddCard: () -> Unit,
    viewModel: VaultListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VaultListEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                VaultListEffect.CardDeleted -> snackbarHostState.showSnackbar("Card deleted")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCard) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Card")
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Total Assets",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "€ ${state.totalBalance.fmt2dp()}",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }

                LazyColumn {
                    items(state.cards, key = { it.id }) { card ->
                        ListItem(
                            headlineContent = { Text(card.bankName) },
                            supportingContent = {
                                Text("**** **** **** ${card.cardNumber.takeLast(4)}")
                            },
                            trailingContent = {
                                Text(
                                    text = "€ ${card.balance.fmt2dp()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            overlineContent = { Text(card.cardType.name) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
