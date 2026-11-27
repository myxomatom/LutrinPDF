// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

/**
 * Theme of the interface (F13, E3 « Thème de l'application »). It never changes the colours of
 * the document itself, which are a separate choice ([DocumentColors], ADR 0028).
 */
enum class AppTheme {
    /** Always the light colour scheme. */
    LIGHT,

    /** Always the dark colour scheme. */
    DARK,

    /** The scheme of the system setting, followed live. The default. */
    SYSTEM,
    ;

    /**
     * Whether the interface is drawn with the dark colour scheme.
     *
     * @param systemDark whether the system currently asks for a dark theme.
     */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }
}
