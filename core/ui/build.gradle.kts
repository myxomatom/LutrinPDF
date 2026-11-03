// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.library)
    alias(libs.plugins.lutrin.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.myxomatom.lutrin.core.ui"
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
