// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

/**
 * Where the repository of the source code is (« Code source » in E4). AGPL v3 section 13 asks the
 * app to offer its corresponding source to its users (ADR 0030). Never fetched by the app: the
 * address is only handed to another app after the user confirmed.
 */
internal const val SOURCE_CODE_URL = "https://github.com/myxomatom/LutrinPDF"

/**
 * Gives the version of the installed app, so that the ViewModel never touches a `Context`
 * (`AGENTS.md` §3). Implemented by the app module, from its `PackageInfo`.
 */
fun interface AppVersionProvider {
    /** Version name of the app (for example `0.1.0`), or `null` when the system cannot tell. */
    fun versionName(): String?
}

/**
 * Gives the third-party license data generated at build time by AboutLibraries (ADR 0030).
 * Implemented by the app module, which owns the generated `res/raw/aboutlibraries.json`.
 */
fun interface LicensesSource {
    /**
     * Reads the generated JSON. Blocking: the caller runs it on the I/O dispatcher.
     *
     * @return the whole content of `aboutlibraries.json`.
     * @throws java.io.IOException if the resource cannot be read.
     */
    fun readJson(): String
}
