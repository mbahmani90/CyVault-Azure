package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeIntent
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeState
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeViewModel
import com.cypressit.cyvault.systemdesign.components.CyAvatar
import com.cypressit.cyvault.systemdesign.components.StatusBarIconsFor
import com.cypressit.cyvault.systemdesign.theme.CyTheme
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.cyvault_logo
import cyvault.vault.generated.resources.vault_home_add_card
import cyvault.vault.generated.resources.vault_home_add_item_cancel
import cyvault.vault.generated.resources.vault_home_add_item_description_fab
import cyvault.vault.generated.resources.vault_home_balance_delete_confirm
import cyvault.vault.generated.resources.vault_home_balance_delete_message
import cyvault.vault.generated.resources.vault_home_balance_delete_title
import cyvault.vault.generated.resources.vault_home_balances_empty
import cyvault.vault.generated.resources.vault_home_change_photo_description
import cyvault.vault.generated.resources.vault_home_delete_balance_description
import cyvault.vault.generated.resources.vault_home_edit_item_title
import cyvault.vault.generated.resources.vault_home_logo_description
import cyvault.vault.generated.resources.vault_home_open_menu_description
import cyvault.vault.generated.resources.vault_home_sign_out
import cyvault.vault.generated.resources.vault_home_sign_out_description
import cyvault.vault.generated.resources.vault_home_title
import cyvault.vault.generated.resources.vault_home_total_since
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Readable on both the light and the dark surface.
private val IncomeGreen = Color(0xFF2E9E5B)

/**
 * `−€ 25.69`, or `+€ 25.69` / `€ 25.69` for a positive amount depending on [plusSign].
 * With [symbolAfter], the symbol follows the number: `−25.69 €`.
 */
internal fun formatAmount(
    amount: Double,
    currency: String,
    plusSign: Boolean = false,
    symbolAfter: Boolean = false,
): String {
    val sign = when {
        amount < 0 -> "−"
        plusSign -> "+"
        else -> ""
    }
    val number = kotlin.math.abs(amount).fmt2dp()
    val symbol = currencySymbol(currency)
    return if (symbolAfter) "$sign$number $symbol" else "$sign$symbol $number"
}

private fun Double.fmt2dp(): String {
    // Round, not truncate: 25.69 + 5.98 is 31.669999… as a Double and must show as 31.67.
    val scaled = kotlin.math.round(this * 100).toLong()
    val intPart = scaled / 100
    val decPart = kotlin.math.abs(scaled % 100)
    return "$intPart.${decPart.toString().padStart(2, '0')}"
}

/**
 * The vault's home: total assets, the user's balances and cards, a drawer with add-card and
 * sign-out, and an add button that opens the add-item dialog.
 */
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
        onAddCard = onAddCardClick,
    )

    // Deleting money data takes a confirmation, from a long press or the edit dialog's Delete.
    // The confirmation is pure UI state.
    var pendingDelete by remember { mutableStateOf<Balance?>(null) }

    if (state.isAddItemDialogVisible) {
        AddItemDialog(
            item = state.editingBalance,
            isSaving = state.isSavingBalance,
            onDismiss = { viewModel.onIntent(VaultHomeIntent.DismissAddItemDialog) },
            onSave = { title, description, value, currency, date ->
                viewModel.onIntent(VaultHomeIntent.SaveItem(title, description, value, currency, date))
            },
            onDelete = {
                pendingDelete = state.editingBalance
                viewModel.onIntent(VaultHomeIntent.DismissAddItemDialog)
            },
        )
    }

    pendingDelete?.let { balance ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.vault_home_balance_delete_title)) },
            text = { Text(stringResource(Res.string.vault_home_balance_delete_message, balance.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.onIntent(VaultHomeIntent.DeleteBalance(balance.id))
                }) {
                    Text(stringResource(Res.string.vault_home_balance_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(Res.string.vault_home_add_item_cancel))
                }
            },
        )
    }

    // Platform actions are created here, next to the ViewModel, and passed down as lambdas.
    val pickProfilePhoto = rememberProfilePhotoPicker { photo ->
        viewModel.onIntent(VaultHomeIntent.ChangeProfilePhoto(photo))
    }

    // The top bar is drawn behind the status bar, so its icons must contrast with the bar.
    StatusBarIconsFor(barColor = CyTheme.brandColors.topBar)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                DrawerHeader(
                    name = state.userName,
                    email = state.userEmail,
                    photo = state.profilePhoto,
                    isUpdatingPhoto = state.isUpdatingPhoto,
                    onChangePhotoClick = pickProfilePhoto,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Default.AddCard, contentDescription = null) },
                    label = { Text(stringResource(Res.string.vault_home_add_card)) },
                    selected = false,
                    onClick = { viewModel.onIntent(VaultHomeIntent.AddCardFromDrawer) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
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
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackBarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.vault_home_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CyTheme.brandColors.topBar,
                        titleContentColor = CyTheme.brandColors.onTopBar,
                        navigationIconContentColor = CyTheme.brandColors.onTopBar,
                        actionIconContentColor = CyTheme.brandColors.onTopBar,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onIntent(VaultHomeIntent.OpenDrawer) }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(Res.string.vault_home_open_menu_description),
                            )
                        }
                    },
                    actions = {
                        Image(
                            painter = painterResource(Res.drawable.cyvault_logo),
                            contentDescription = stringResource(Res.string.vault_home_logo_description),
                            modifier = Modifier
                                .size(42.dp),
                        )
                    },
                )
            },
        ) { paddingValues ->
            HomeContent(
                state = state,
                onAddItem = { viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog) },
                onEditBalance = { viewModel.onIntent(VaultHomeIntent.EditBalance(it)) },
                onLongPressBalance = { pendingDelete = it },
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

/**
 * The signed-in user at the top of the drawer: avatar, name and email. Tapping the avatar
 * calls [onChangePhotoClick] to change the profile photo.
 */
@Composable
private fun DrawerHeader(
    name: String,
    email: String,
    photo: ByteArray?,
    isUpdatingPhoto: Boolean,
    onChangePhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoBitmap = remember(photo) { photo?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            // Only the circle is clipped and clickable, so the badge can overlap its edge.
            CyAvatar(
                name = name,
                fallback = email,
                photo = photoBitmap,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = !isUpdatingPhoto,
                        onClickLabel = stringResource(Res.string.vault_home_change_photo_description),
                        onClick = onChangePhotoClick,
                    ),
            )
            if (isUpdatingPhoto) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(14.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = stringResource(Res.string.vault_home_change_photo_description),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp),
                )
            }
        }
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
private fun BalanceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Total assets, then the user's balances and cards in one scrolling list. */
@Composable
private fun HomeContent(
    state: VaultHomeState,
    onAddItem: () -> Unit,
    onEditBalance: (Balance) -> Unit,
    onLongPressBalance: (Balance) -> Unit,
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
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Total Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // One line per currency; € 0.00 until there are balances.
                        state.balanceTotals.ifEmpty { listOf("EUR" to 0.0) }.forEach { (currency, total) ->
                            Text(
                                text = formatAmount(total, currency, symbolAfter = true),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        oldestBalanceDate(state.balances.map { it.date })?.let { since ->
                            Text(
                                text = stringResource(Res.string.vault_home_total_since, since),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SmallFloatingActionButton(onClick = onAddItem) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.vault_home_add_item_description_fab),
                        )
                    }
                }
            }

            LazyColumn {
                if (state.isLoadingBalances && state.balances.isEmpty()) {
                    item(key = "balances-loading") {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            // Gray instead of the theme's accent: loading shouldn't draw the eye.
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                } else if (state.balances.isEmpty()) {
                    item(key = "balances-empty") {
                        Text(
                            text = stringResource(Res.string.vault_home_balances_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                // Prefixed keys: a balance ID must never collide with a card ID in this list.
                itemsIndexed(state.balances, key = { _, balance -> "balance-${balance.id}" }) { index, balance ->
                    // The same line between rows as after the last one.
                    if (index > 0) BalanceDivider()
                    // Purchases are negative and income positive; the sign leads the symbol.
                    val isIncome = balance.amount >= 0
                    ListItem(
                        headlineContent = { Text(balance.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        // Every row gets a second line, so rows without a description line up too.
                        supportingContent = {
                            Text(
                                text = balance.description.ifBlank { "-" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatAmount(
                                        balance.amount,
                                        balance.currency,
                                        plusSign = true,
                                        symbolAfter = true,
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isIncome) IncomeGreen else MaterialTheme.colorScheme.error,
                                )
                                formatBalanceDate(balance.date)?.let { date ->
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        // Tap edits; a long press asks to delete.
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClickLabel = stringResource(Res.string.vault_home_edit_item_title),
                                onLongClickLabel = stringResource(Res.string.vault_home_delete_balance_description),
                                onLongClick = { onLongPressBalance(balance) },
                                onClick = { onEditBalance(balance) },
                            ),
                    )
                }
                // Only closes a list of rows; under the progress bar or empty text it's just noise.
                if (state.balances.isNotEmpty()) {
                    item(key = "balances-divider") { BalanceDivider() }
                }

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
