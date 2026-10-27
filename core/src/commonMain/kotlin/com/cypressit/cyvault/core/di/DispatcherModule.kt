package com.cypressit.cyvault.core.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Koin qualifier of the dispatcher for blocking I/O (files, blocking SDKs). */
const val IO_DISPATCHER = "ioDispatcher"

/**
 * Dispatchers are injected rather than hardcoded, so tests can pass a test dispatcher.
 * Suspend Ktor calls are already main-safe and don't need one.
 */
val dispatcherModule = module {
    single<CoroutineDispatcher>(named(IO_DISPATCHER)) { Dispatchers.IO }
}
