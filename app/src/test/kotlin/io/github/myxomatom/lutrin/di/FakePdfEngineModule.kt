// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import io.github.myxomatom.lutrin.core.pdf.di.PdfEngineModule
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import javax.inject.Singleton

/**
 * Replaces the MuPDF engine, whose native library Robolectric cannot load, in every
 * `@HiltAndroidTest` of this module. Tests inject [FakePdfEngine] to drive and inspect it.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PdfEngineModule::class])
object FakePdfEngineModule {
    /** One fake per test application. */
    @Provides
    @Singleton
    fun provideFakeEngine(): FakePdfEngine = FakePdfEngine()

    /** The fake, as the engine of the app. */
    @Provides
    fun provideEngine(fake: FakePdfEngine): PdfEngine = fake
}
