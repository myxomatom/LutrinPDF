// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.library)
}

android {
    namespace = "io.github.myxomatom.lutrin.core.testing"
}

dependencies {
    // Fakes of the PDF engine interfaces, shared by the tests of the feature modules.
    api(projects.core.pdf)
    api(libs.kotlinx.coroutines.core)
}
