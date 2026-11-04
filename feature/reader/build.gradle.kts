// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.feature)
    alias(libs.plugins.lutrin.android.robolectric)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.myxomatom.lutrin.feature.reader"
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    // The reader draws pages: it needs the PdfEngine interface, never MuPDF itself (ADR 0008).
    implementation(projects.core.pdf)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
