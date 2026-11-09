// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.library)
    alias(libs.plugins.lutrin.hilt)
    alias(libs.plugins.lutrin.android.robolectric)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.myxomatom.lutrin.core.data"
    // The exported schemas are read by MigrationTestHelper from the test assets (ADR 0017).
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

// Every schema version is committed, so that migrations can be written and tested against it.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.domain)
    // Thumbnails of recent documents are drawn through PdfEngine, never MuPDF itself (ADR 0015).
    implementation(projects.core.pdf)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
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
