// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

/**
 * Colours the pages of a document are drawn with (F13, V6 « Couleurs du document », ADR 0028).
 * The engine maps the white of a page to [paper] and its black to [ink]; values come from the
 * section `color.reader` of `docs/design/design-tokens.json`, as opaque ARGB colours.
 *
 * @property paper colour of a blank page, and of the page before its rendering arrives.
 * @property ink colour black text is drawn with.
 * @property isDark whether the page is dark: the text drawn by the reader over it (the message
 *   of a damaged page, ADR 0031) then uses the dark colour scheme.
 */
enum class DocumentColorMode(val paper: Int, val ink: Int, val isDark: Boolean) {
    /** The original colours of the PDF, untouched. The default, in the dark theme too. */
    NORMAL(paper = 0xFFFFFFFF.toInt(), ink = 0xFF000000.toInt(), isDark = false),

    /**
     * Light text on a dark page: the lightness of the rendering is inverted, hues kept, so that
     * images stay recognisable, then mapped onto the night paper and ink (tokens `pageNight`,
     * `textNight`).
     */
    NIGHT(paper = 0xFF16181C.toInt(), ink = 0xFFE1E2E8.toInt(), isDark = true),

    /**
     * Brown text on cream paper: a colour filter over the rendering (tokens `pageSepia`,
     * `textSepia`).
     */
    SEPIA(paper = 0xFFF4ECD8.toInt(), ink = 0xFF3D2E14.toInt(), isDark = false),
}

/**
 * How the user chose the colours of the documents (F13, ADR 0028): one choice for every
 * document, made in the reader's display options (V6), and an optional link to the dark theme
 * of the app, set in the settings (E3).
 *
 * @property chosen the colours picked in the reader, `null` until the user picks some: the
 *   document then keeps its original colours, unless [followDarkTheme] applies.
 * @property followDarkTheme whether a document without [chosen] colours turns to night while the
 *   app is dark. Off by default: night mode never starts on its own.
 */
data class DocumentColors(val chosen: DocumentColorMode? = null, val followDarkTheme: Boolean = false) {
    /**
     * The colours to draw the pages with.
     *
     * @param darkTheme whether the interface currently uses its dark colour scheme.
     * @return [chosen] if any; otherwise night in the dark theme when [followDarkTheme], and the
     *   original colours in every other case.
     */
    fun modeFor(darkTheme: Boolean): DocumentColorMode = chosen
        ?: if (followDarkTheme && darkTheme) DocumentColorMode.NIGHT else DocumentColorMode.NORMAL
}
