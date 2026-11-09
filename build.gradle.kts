// SPDX-License-Identifier: GPL-3.0-or-later

// Plugins are resolved here once (apply false) so that the convention plugins of
// build-logic share the same versions and class loader.
plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.licensee) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

/** Every module, for the aggregated coverage report and documentation. */
val lutrinModules =
    listOf(
        ":app",
        ":core:domain",
        ":core:data",
        ":core:pdf",
        ":core:ui",
        ":core:testing",
        ":feature:library",
        ":feature:reader",
        ":feature:settings",
        ":feature:tools",
        ":feature:assistant",
    )

dependencies {
    lutrinModules.forEach { dokka(project(it)) }
    // core:testing only holds test support code: it is not part of the shipped app's coverage.
    lutrinModules.filterNot { it == ":core:testing" }.forEach { kover(project(it)) }
}

kover {
    reports {
        // Global threshold (AGENTS.md §5.2); core:domain and core:data enforce 80 % themselves.
        verify {
            rule {
                minBound(70)
            }
        }
    }
}

dokka {
    moduleName.set("Lutrin")
    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }
}

tasks.named("check") {
    dependsOn("koverVerify")
    dependsOn(gradle.includedBuild("build-logic").task(":convention:check"))
}
