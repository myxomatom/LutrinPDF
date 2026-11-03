// SPDX-License-Identifier: GPL-3.0-or-later
import lutrin.convention.library
import lutrin.convention.libs
import lutrin.convention.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.withType

/**
 * Convention for `feature:*` modules: Android library with Compose and Hilt, depending on
 * `core:ui` and `core:domain` only. A feature never depends on another feature (`AGENTS.md` §3).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("lutrin-android-library"))
            pluginManager.apply(libs.pluginId("lutrin-android-compose"))
            pluginManager.apply(libs.pluginId("lutrin-hilt"))
            // Hilt generates test-variant classes even in a module without any test yet, which
            // Gradle would otherwise report as "test sources present but no test discovered".
            tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
            dependencies {
                "implementation"(project(":core:ui"))
                "implementation"(project(":core:domain"))
                "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
                "testImplementation"(project(":core:testing"))
                "testImplementation"(libs.library("junit"))
                "testImplementation"(libs.library("truth"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
                "testImplementation"(libs.library("turbine"))
            }
        }
    }
}
