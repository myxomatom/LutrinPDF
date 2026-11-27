// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguageController

/**
 * In-memory [AppLanguageController] for tests.
 *
 * @property isSupported whether the fake system lets the app choose its language.
 * @param initial the language chosen at the start.
 */
class FakeAppLanguageController(override val isSupported: Boolean = true, initial: AppLanguage = AppLanguage.SYSTEM) :
    AppLanguageController {
    private var language = initial

    /** Every language [set] was called with, in order. */
    val changes: MutableList<AppLanguage> = mutableListOf()

    /** The language [set] chose last. */
    override suspend fun current(): AppLanguage = language

    /** Remembers the language and records the call in [changes]. */
    override suspend fun set(language: AppLanguage) {
        changes += language
        this.language = language
    }
}
