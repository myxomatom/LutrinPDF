// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.feature)
    alias(libs.plugins.lutrin.android.robolectric)
}

android {
    namespace = "io.github.myxomatom.lutrin.feature.library"
}

dependencies {
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
