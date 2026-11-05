// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import io.github.myxomatom.lutrin.core.pdf.mupdf.ContentOpener
import io.github.myxomatom.lutrin.core.pdf.mupdf.DocumentDispatcherFactory
import io.github.myxomatom.lutrin.core.pdf.mupdf.LogReleaseFailureReporter
import io.github.myxomatom.lutrin.core.pdf.mupdf.MuPdfEngine
import io.github.myxomatom.lutrin.core.pdf.mupdf.ReleaseFailureReporter
import io.github.myxomatom.lutrin.core.pdf.mupdf.SingleThreadDocumentDispatcherFactory
import java.io.FileNotFoundException
import javax.inject.Singleton

/** Provides the PDF engine to the rest of the app. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PdfModule {
    /** The MuPDF engine, shared by the whole app (it holds no per-document state). */
    @Binds
    @Singleton
    abstract fun bindEngine(engine: MuPdfEngine): PdfEngine

    /** One dedicated thread per open document (ADR 0007, point 6). */
    @Binds
    abstract fun bindDispatcherFactory(factory: SingleThreadDocumentDispatcherFactory): DocumentDispatcherFactory

    /** Failures of closing a document are logged (type only), never thrown on its thread. */
    @Binds
    abstract fun bindReleaseFailureReporter(reporter: LogReleaseFailureReporter): ReleaseFailureReporter

    companion object {
        /**
         * Opens content URIs read-only through the [android.content.ContentResolver]
         * (SAF only, `AGENTS.md` §9).
         *
         * @throws FileNotFoundException if no provider serves the URI any more.
         * @throws SecurityException if the read permission was revoked.
         */
        @Provides
        internal fun provideContentOpener(@ApplicationContext context: Context): ContentOpener = ContentOpener { uri ->
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("No content provider for the document")
        }
    }
}
