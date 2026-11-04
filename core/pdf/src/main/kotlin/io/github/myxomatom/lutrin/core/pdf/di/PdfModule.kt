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
import io.github.myxomatom.lutrin.core.pdf.mupdf.MuPdfEngine
import io.github.myxomatom.lutrin.core.pdf.mupdf.SingleThreadDocumentDispatcherFactory
import java.io.FileNotFoundException
import javax.inject.Singleton

/** Provides the PDF engine to the rest of the app. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PdfModule {
    @Binds
    @Singleton
    abstract fun bindEngine(engine: MuPdfEngine): PdfEngine

    @Binds
    abstract fun bindDispatcherFactory(factory: SingleThreadDocumentDispatcherFactory): DocumentDispatcherFactory

    companion object {
        @Provides
        fun provideContentOpener(@ApplicationContext context: Context): ContentOpener = ContentOpener { uri ->
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("No content provider for the document")
        }
    }
}
