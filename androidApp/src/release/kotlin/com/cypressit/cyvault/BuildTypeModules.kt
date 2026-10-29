package com.cypressit.cyvault

import org.koin.core.module.Module

/** Release builds add nothing: the developer tools' modules aren't even compiled in. */
val buildTypeModules: List<Module> = emptyList()
