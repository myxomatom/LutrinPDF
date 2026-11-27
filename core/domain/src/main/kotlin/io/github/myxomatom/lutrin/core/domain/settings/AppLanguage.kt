// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

import javax.inject.Inject

/**
 * Language of the interface chosen in the settings (F19, E3, ADR 0029): French, English, or the
 * language of the system. The choice is kept by the system, as the per-app language of Android 13
 * and later, not by the app.
 *
 * @property languageTag BCP 47 tag of the language; `null` for the language of the system.
 */
enum class AppLanguage(val languageTag: String?) {
    /** The language of the system, as long as the app has it; English otherwise. The default. */
    SYSTEM(null),

    /** French. */
    FRENCH("fr"),

    /** English. */
    ENGLISH("en"),
    ;

    /** Companion helpers. */
    companion object {
        /**
         * The language a list of language tags stands for.
         *
         * @param tags tags in order of preference, such as `["fr-CA"]`; empty for the system language.
         * @return the language of the first tag when the app has it (a region is ignored: `fr-CA`
         *   is [FRENCH]), [SYSTEM] when the list is empty or its first language is not one of the app.
         */
        fun fromLanguageTags(tags: List<String>): AppLanguage {
            val language = tags.firstOrNull()?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return entries.firstOrNull { it.languageTag != null && it.languageTag == language } ?: SYSTEM
        }
    }
}

/**
 * Where the language of the app is read and changed: the per-app language of the system (Android
 * 13 and later). On an older Android the app follows the language of the system and offers no
 * choice (ADR 0029). Implemented in `core:data`.
 */
interface AppLanguageController {
    /** Whether the system lets the app choose its own language (Android 13 and later). */
    val isSupported: Boolean

    /** The language chosen now; [AppLanguage.SYSTEM] when none, or when [isSupported] is `false`. */
    suspend fun current(): AppLanguage

    /**
     * Changes the language of the app. The system restarts the open screens in it.
     *
     * @param language the language to use from now on; ignored when [isSupported] is `false`.
     */
    suspend fun set(language: AppLanguage)
}

/**
 * Reads the language chosen for the app (E3, ADR 0029).
 *
 * @param controller where the language is kept.
 */
class GetAppLanguageUseCase @Inject constructor(private val controller: AppLanguageController) {
    /** @return the language chosen, or `null` when the system does not let the app choose one. */
    suspend operator fun invoke(): AppLanguage? = if (controller.isSupported) controller.current() else null
}

/**
 * Changes the language of the app (E3, ADR 0029).
 *
 * @param controller where the language is kept.
 */
class SetAppLanguageUseCase @Inject constructor(private val controller: AppLanguageController) {
    /** @param language French, English or the language of the system, from now on. */
    suspend operator fun invoke(language: AppLanguage) {
        controller.set(language)
    }
}
