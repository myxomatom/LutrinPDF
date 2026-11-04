// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.library)
    alias(libs.plugins.lutrin.hilt)
}

android {
    namespace = "io.github.myxomatom.lutrin.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

// Stricter than the global 70 % (AGENTS.md §5.2).
kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
