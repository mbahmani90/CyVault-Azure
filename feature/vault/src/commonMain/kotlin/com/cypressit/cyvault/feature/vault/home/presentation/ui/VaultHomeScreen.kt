package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeIntent
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeState
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeViewModel
import com.cypressit.cyvault.systemdesign.components.CyAvatar
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.vault_home_open_menu_description
import cyvault.vault.generated.resources.vault_home_sign_out
import cyvault.vault.generated.resources.vault_home_sign_out_description
import cyvault.vault.generated.resources.vault_home_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private fun Double.fmt2dp(): String {
    val scaled = (this * 100).toLong()
    val intPart = scaled / 100
    val decPart = kotlin.math.abs(scaled % 100)
    return "$intPart.${decPart.toString().padStart(2, '0')}"
}

/** The vault's home: the card list with total assets, a drawer with sign-out, and an add-card button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultHomeScreen(
    onAddCardClick: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: VaultHomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackBarHostState = remember { SnackbarHostState() }

    VaultHomeScreenEffect(
        effects = viewModel.effect,
        drawerState = drawerState,
        snackBarHostState = snackBarHostState,
        onSignedOut = onSignedOut,
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                DrawerHeader(
                    name = state.userName,
                    email = state.userEmail,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(Res.string.vault_home_sign_out_description),
                        )
                    },
                    label = { Text(stringResource(Res.string.vault_home_sign_out)) },
                    selected = false,
                    onClick = { viewModel.onIntent(VaultHomeIntent.SignOut) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackBarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.vault_home_title)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onIntent(VaultHomeIntent.OpenDrawer) }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(Res.string.vault_home_open_menu_description),
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddCardClick) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Card")
                }
            },
        ) { paddingValues ->
            CardList(
                state = state,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

/** The signed-in user at the top of the drawer: initials avatar, name and email. */
@Composable
private fun DrawerHeader(
    name: String,
    email: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CyAvatar(name = name, fallback = email)
        Spacer(Modifier.height(8.dp))
        if (name.isNotBlank()) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (email.isNotBlank()) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardList(
    state: VaultHomeState,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        else -> Column(
            modifier = modifier.fillMaxSize(),
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
