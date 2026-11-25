package com.cypressit.cyvault.navigation

import androidx.navigation.NavController

/** [route] becomes the only screen: Back can't return to what was there (sign-in ↔ home). */
fun NavController.navigateClearingBackStack(route: Any) =
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
