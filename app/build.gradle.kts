// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.application)
    alias(libs.plugins.lutrin.android.compose)
    alias(libs.plugins.lutrin.hilt)
    alias(libs.plugins.lutrin.android.robolectric)
    alias(libs.plugins.roborazzi)
}

/** Version code of the release; each per-ABI APK derives its own from it (see below). */
val appVersionCode = 1

android {
    namespace = "io.github.myxomatom.lutrin"

    defaultConfig {
        applicationId = "io.github.myxomatom.lutrin"
        versionCode = appVersionCode
        versionName = "0.1.0"
    }

    // One APK per ABI, each under the 15 MB budget of SPEC.md §3 (the MuPDF native library
    // is most of the size). App bundles for Google Play are split by the store itself.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // No Google-encrypted dependency metadata in the APK or bundle: F-Droid flags it, and it
    // would make the build harder to reproduce.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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

// Distinct version codes per ABI, as F-Droid and Google Play require for split APKs:
// versionCode * 10 + ABI rank (1 = armeabi-v7a, 2 = arm64-v8a, 3 = x86_64).
val abiVersionCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find {
                it.filterType ==
                    com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }
            val rank = abi?.let { abiVersionCodes[it.identifier] }
            if (rank != null) output.versionCode.set(appVersionCode * 10 + rank)
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Leak detection in debug builds only (AGENTS.md §8); never in release.
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
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
