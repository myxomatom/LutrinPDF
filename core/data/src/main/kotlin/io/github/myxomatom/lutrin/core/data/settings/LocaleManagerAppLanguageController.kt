// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguageController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [AppLanguageController] on the per-app language of the system (`LocaleManager`, Android 13 and
 * later, ADR 0029): the system keeps the choice, applies it to every screen of the app, and shows
 * it in its own settings (`locales_config.xml`). No dependency, no permission, nothing stored by
 * the app. Before Android 13 the app follows the language of the system and offers no choice.
 *
 * @param context the application, whose [LocaleManager] is used.
 * @param ioDispatcher where the system is called: a call to another process.
 * @param sdkInt the version of Android the app runs on; a parameter for the tests, which can run
 *   on one level only.
 */
internal class LocaleManagerAppLanguageController(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    sdkInt: Int,
) : AppLanguageController {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context, ioDispatcher, Build.VERSION.SDK_INT)

    override val isSupported: Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

    override suspend fun current(): AppLanguage = if (isSupported) {
        withContext(ioDispatcher) { currentApplicationLocales() }
    } else {
        AppLanguage.SYSTEM
    }

    override suspend fun set(language: AppLanguage) {
        if (isSupported) withContext(ioDispatcher) { setApplicationLocales(language) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun currentApplicationLocales(): AppLanguage {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return AppLanguage.fromLanguageTags(List(locales.size()) { locales[it].toLanguageTag() })
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setApplicationLocales(language: AppLanguage) {
        // An empty list gives the choice back to the system.
        val locales = language.languageTag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }
}
