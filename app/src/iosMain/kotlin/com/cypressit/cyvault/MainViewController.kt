package com.cypressit.cyvault

import androidx.compose.ui.window.ComposeUIViewController
import com.cypressit.cyvault.di.appModules
import org.koin.core.context.startKoin

// PascalCase because Swift calls it like a type: MainViewControllerKt.MainViewController()
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController {
    startKoin { modules(appModules) }
    App()
}
