// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.di

import android.content.Context
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.myxomatom.lutrin.R
import io.github.myxomatom.lutrin.feature.about.AppVersionProvider
import io.github.myxomatom.lutrin.feature.about.LicensesSource

/**
 * Gives `feature:about` what only the app module owns: its version, and the third-party license
 * list that the AboutLibraries plugin generates for the app as `res/raw/aboutlibraries.json`
 * (ADR 0030). The feature never sees a `Context` in its ViewModel.
 */
@Module
@InstallIn(SingletonComponent::class)
object AboutModule {
    /** Version name of the installed app, from its `PackageInfo`. */
    @Provides
    fun provideAppVersion(@ApplicationContext context: Context): AppVersionProvider = AppVersionProvider {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (expected: PackageManager.NameNotFoundException) {
            // The app's own package is always installed; shown as no version if it is not.
            null
        }
    }

    /** The generated license data, read from the raw resource of the app. */
    @Provides
    fun provideLicensesSource(@ApplicationContext context: Context): LicensesSource = LicensesSource {
        context.resources.openRawResource(R.raw.aboutlibraries).use { it.readBytes().decodeToString() }
    }
}
