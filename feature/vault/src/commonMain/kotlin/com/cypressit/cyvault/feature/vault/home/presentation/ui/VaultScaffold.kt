package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeIntent
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeViewModel
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.vault_home_back_description
import cyvault.vault.generated.resources.vault_home_open_menu_description
import cyvault.vault.generated.resources.vault_home_sign_out
import cyvault.vault.generated.resources.vault_home_sign_out_description
import cyvault.vault.generated.resources.vault_home_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The vault's shell (drawer, top bar, sign-out) around one vault screen. Each vault destination
 * in app's NavHost wraps its screen in it; navigation events go out as callbacks.
 *
 * @param showMenu true on the root vault screen (menu icon + drawer), false elsewhere (back arrow).
 * @param onSignedOut only reachable when [showMenu] is true, since sign-out lives in the drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultScaffold(
    showMenu: Boolean,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: VaultHomeViewModel = koinViewModel(),
    content: @Composable (Modifier) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackBarHostState = remember { SnackbarHostState() }

    VaultScaffoldEffect(
        effects = viewModel.effect,
        drawerState = drawerState,
        snackBarHostState = snackBarHostState,
        onSignedOut = onSignedOut,
        onBack = onBack,
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showMenu,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(Res.string.vault_home_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
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
                        IconButton(onClick = {
                            if (showMenu) viewModel.onIntent(VaultHomeIntent.OpenDrawer)
                            else viewModel.onIntent(VaultHomeIntent.NavigateBack)
                        }) {
                            Icon(
                                imageVector = if (showMenu) Icons.Default.Menu
                                    else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (showMenu) stringResource(Res.string.vault_home_open_menu_description)
                                    else stringResource(Res.string.vault_home_back_description),
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            content(Modifier.padding(paddingValues))
        }
    }
}
