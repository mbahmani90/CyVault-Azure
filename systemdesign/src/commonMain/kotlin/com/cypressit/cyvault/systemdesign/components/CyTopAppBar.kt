package com.cypressit.cyvault.systemdesign.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.cypressit.cyvault.systemdesign.theme.CyTheme

/** The brand top bar, drawn behind the status bar, with status bar icons that contrast with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyTopAppBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
) {
    StatusBarIconsFor(barColor = CyTheme.brandColors.topBar)

    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CyTheme.brandColors.topBar,
            titleContentColor = CyTheme.brandColors.onTopBar,
            navigationIconContentColor = CyTheme.brandColors.onTopBar,
            actionIconContentColor = CyTheme.brandColors.onTopBar,
        ),
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
    )
}
