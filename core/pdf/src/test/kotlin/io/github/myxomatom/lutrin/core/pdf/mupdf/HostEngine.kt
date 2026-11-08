// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import org.junit.Assume.assumeTrue

/**
 * Whether MuPDF's JNI library is built for this machine. Gradle sets `lutrin.hostEngine` to
 * `false` outside Linux (ADR 0013); when it is `true` or absent, the engine tests run and fail
 * if the library is missing.
 */
private val isHostEngineAvailable: Boolean = System.getProperty("lutrin.hostEngine") != "false"

/** Skips the calling test, with an explicit message, when the host engine is not built (ADR 0013). */
internal fun assumeHostEngine() {
    assumeTrue(
        "MuPDF engine JVM tests need the host JNI library, built on Linux only; the CI runs them (ADR 0013)",
        isHostEngineAvailable,
    )
}
