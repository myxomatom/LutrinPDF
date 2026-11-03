// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.application)
    alias(libs.plugins.lutrin.android.compose)
    alias(libs.plugins.lutrin.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.myxomatom.lutrin"

    defaultConfig {
        applicationId = "io.github.myxomatom.lutrin"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // Lets a debug build live next to the published app on the same device.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.pdf)
    implementation(projects.feature.library)
    implementation(projects.feature.reader)
    implementation(projects.feature.settings)
    implementation(projects.feature.tools)
    implementation(projects.feature.assistant)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Leak detection in debug builds only (AGENTS.md §8); never in release.
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
}
