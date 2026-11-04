// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.myxomatom.lutrin.core.domain.dispatchers.DefaultDispatcher
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The only place where the platform dispatchers are named (`AGENTS.md` §3). */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    /** Dispatcher for CPU-bound work. */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** Dispatcher for blocking I/O. */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
