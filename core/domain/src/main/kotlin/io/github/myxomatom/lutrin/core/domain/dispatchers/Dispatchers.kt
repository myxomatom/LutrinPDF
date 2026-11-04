// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.dispatchers

import javax.inject.Qualifier

/**
 * Marks the injected `CoroutineDispatcher` for CPU-bound work (bitmap allocation, layout
 * computations). Injected rather than hard-coded so that tests use a test dispatcher (`AGENTS.md` §3).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Marks the injected `CoroutineDispatcher` for blocking I/O (files, databases). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
