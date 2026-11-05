// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Creates the single-thread dispatcher that owns one document. MuPDF objects are not
 * thread-safe (ADR 0001): every call on a document runs on its own thread.
 */
internal fun interface DocumentDispatcherFactory {
    /** Returns a new dispatcher backed by exactly one thread; the caller closes it. */
    fun create(): ExecutorCoroutineDispatcher
}

/** Production factory: one background daemon thread per open document. */
internal class SingleThreadDocumentDispatcherFactory
@Inject
constructor() : DocumentDispatcherFactory {
    private val counter = AtomicInteger()

    override fun create(): ExecutorCoroutineDispatcher = Executors
        .newSingleThreadExecutor { task ->
            Thread(task, "lutrin-pdf-${counter.incrementAndGet()}").apply { isDaemon = true }
        }.asCoroutineDispatcher()
}
