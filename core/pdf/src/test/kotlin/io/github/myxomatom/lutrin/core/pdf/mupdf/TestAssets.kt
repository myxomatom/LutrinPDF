// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExecutorCoroutineDispatcher

/** Test file set of core:testing, read from its source folder (path given by Gradle). */
internal object TestAssets {
    private val root = File(checkNotNull(System.getProperty("lutrin.testAssets")) { "lutrin.testAssets is not set" })

    fun file(path: String): File = File(root, path).also { check(it.isFile) { "Missing test file $path" } }

    fun source(path: String): DocumentSource = DocumentSource.FromFile(file(path))
}

/** Dispatcher factory that remembers what it created, to check that nothing leaks. */
internal class RecordingDispatcherFactory : DocumentDispatcherFactory {
    val created = CopyOnWriteArrayList<ExecutorCoroutineDispatcher>()
    private val delegate = SingleThreadDocumentDispatcherFactory()

    override fun create(): ExecutorCoroutineDispatcher = delegate.create().also(created::add)

    /** Waits until every created thread has run its queued work and stopped. */
    fun awaitTermination() {
        created.forEach { dispatcher ->
            val executor = dispatcher.executor as ExecutorService
            check(executor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Document thread still running"
            }
        }
    }
}

private const val TERMINATION_TIMEOUT_SECONDS = 10L

/** Number of descriptors this process holds open on [file] (Linux `/proc`, like the JNI tests). */
internal fun openDescriptorsTo(file: File): Int {
    val target = file.toPath().toRealPath()
    return File("/proc/self/fd").listFiles().orEmpty().count { descriptor ->
        runCatching { Files.readSymbolicLink(descriptor.toPath()) }.getOrNull() == target
    }
}

internal fun engine(dispatchers: DocumentDispatcherFactory = SingleThreadDocumentDispatcherFactory()) =
    MuPdfEngine(contentOpener = { error("Content URIs are covered by instrumented tests") }, dispatchers = dispatchers)

internal suspend fun openTestPdf(path: String, password: String? = null): MuPdfDocument =
    engine().open(TestAssets.source(path), password).getOrThrow() as MuPdfDocument
