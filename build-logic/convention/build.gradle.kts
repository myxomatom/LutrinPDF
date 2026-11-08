// SPDX-License-Identifier: GPL-3.0-or-later
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

group = "lutrin.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    compileOnly(libs.dokka.gradlePlugin)
    compileOnly(libs.licensee.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    // Part of the Gradle distribution (no external artifact): runs the convention plugins in tests.
    testImplementation(gradleTestKit())
}

ktlint {
    version.set(libs.versions.ktlint)
}

// The hexdump.sh comparison test reads MuPDF's sources (submodule of the parent build).
val mupdfSources = rootDir.resolve("../third-party/mupdf/libmupdf").normalize()
tasks.test {
    systemProperty("lutrin.mupdfDir", mupdfSources.absolutePath)
    inputs
        .files(
            fileTree(mupdfSources) {
                include("scripts/hexdump.sh", "resources/fonts/**", "resources/hyphen/**")
            },
        ).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("mupdfResources")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.lutrin.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.lutrin.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidTest") {
            id = libs.plugins.lutrin.android.test.get().pluginId
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.lutrin.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.lutrin.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.lutrin.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidRobolectric") {
            id = libs.plugins.lutrin.android.robolectric.get().pluginId
            implementationClass = "AndroidRobolectricConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.lutrin.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
        register("mupdf") {
            id = libs.plugins.lutrin.mupdf.get().pluginId
            implementationClass = "MupdfConventionPlugin"
        }
    }
}
