// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.library)
}

android {
    namespace = "io.github.myxomatom.lutrin.core.testing"
}

dependencies {
    api(projects.core.pdf)
    implementation(libs.kotlinx.coroutines.core)
    // The accessibility audit of Compose screens (AccessibilityAudit), used by the tests of the features.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.truth)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
